package sundialdof;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Sundial Physical DoF patcher for
 * "Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5".
 *
 * Same workflow as the GFME patch tool: the jar is placed next to the pack, it locates
 * itself, verifies the inputs, and produces a patched copy. The jar carries only the
 * injected code and documentation - never the shader pack itself.
 */
public final class Patcher {

    private static final String VERSION = "1.0";
    private static final String PACK_NAME = "ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5";
    private static final String OUT_NAME = "C5.9.3_EP1.10.5_DOF";
    private static final String SUNDIAL_NAME = "Sundial_Lite_v1.2.0.zip";
    private static final String SUNDIAL_SHA1 = "8b079dde0a481b554861131e26407da89a2250c6";
    private static final String SUNDIAL_SHA512 =
            "c334f92acc37ef30dfa2cec5c0e7179984ad52651fa8c6e3b4251990305498afb5a3997ba73dfc0b8c7700db4a18edf3b9b35a2ed60050dc2802e5000f83b973";
    private static final long SUNDIAL_SIZE = 1513863L;
    private static final String SUNDIAL_PAGE = "https://modrinth.com/shader/sundial-lite/version/1.2.0";

    /** SHA-1 of the untouched files the patch injects into. */
    private static final Map<String, String> FINGERPRINT = new LinkedHashMap<String, String>();

    static {
        FINGERPRINT.put("shaders/program/composite3.glsl", "f7c1640182a8669bcb6eeb2d359dfb62f7f7ce3f");
        FINGERPRINT.put("shaders/lib/shaderSettings/composite3.glsl", "95f1e4373043888e4b0ddfbc8b141e7a81e36075");
        FINGERPRINT.put("shaders/shaders.properties", "e7405ea55dcd087ee9d55c7a18405bfb6d2102c9");
        FINGERPRINT.put("shaders/lang/en_US.lang", "a848499bef2a3fb2ca9fbe774ed129fcd28dc3a3");
        FINGERPRINT.put("shaders/lang/zh_CN.lang", "343e1fd17403601ae63f5b4c9efb64c92e2ef984");
    }

    private static File selfDir;
    private static boolean force;
    private static boolean overwrite;

    private Patcher() {
    }

    public static void main(String[] args) {
        try {
            selfDir = locateSelf();
            File source = null;
            File output = null;
            File sundial = null;
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if ("-s".equals(a) || "--source".equals(a)) {
                    source = new File(next(args, ++i, a));
                } else if ("-o".equals(a) || "--output".equals(a)) {
                    output = new File(next(args, ++i, a));
                } else if ("--sundial".equals(a)) {
                    sundial = new File(next(args, ++i, a));
                } else if ("--overwrite".equals(a)) {
                    overwrite = true;
                } else if ("--force".equals(a)) {
                    force = true;
                } else if ("-h".equals(a) || "--help".equals(a)) {
                    usage();
                    return;
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + a);
                }
            }
            if (source == null) {
                File folder = new File(selfDir, PACK_NAME);
                File zip = new File(selfDir, PACK_NAME + ".zip");
                source = folder.exists() || !zip.exists() ? folder : zip;
            }
            if (output == null) {
                output = new File(selfDir, OUT_NAME);
            }

            System.out.println("Sundial Physical DoF patcher " + VERSION);
            System.out.println("  jar    : " + selfDir);
            System.out.println("  source : " + source.getAbsolutePath());
            System.out.println("  output : " + output.getAbsolutePath());
            System.out.println();

            System.out.println("[1/6] Verifying Sundial Lite 1.2.0 (official Modrinth file) ...");
            if (sundial == null) {
                sundial = findSundial(selfDir);
            }
            verifySundial(sundial);
            System.out.println("      OK  " + sundial.getName() + "  sha1=" + SUNDIAL_SHA1);

            System.out.println("[2/6] Checking source pack ...");
            if (!source.exists()) {
                fail("Source pack not found: " + source.getAbsolutePath()
                        + "\n      Put this jar next to \"" + PACK_NAME + "\" (folder or zip).");
            }
            boolean sourceIsZip = source.isFile() && source.getName().toLowerCase().endsWith(".zip");
            if (!sourceIsZip && !source.isDirectory()) {
                fail("Source must be a folder or a .zip: " + source.getAbsolutePath());
            }
            checkFingerprint(source, sourceIsZip);

            System.out.println("[3/6] Preparing output ...");
            if (output.exists()) {
                if (!overwrite) {
                    fail("Output already exists: " + output.getAbsolutePath()
                            + "\n      Use --overwrite to replace it.");
                }
                deleteTree(output.toPath());
            }
            Files.createDirectories(output.toPath());

            System.out.println("[4/6] Copying pack ...");
            if (sourceIsZip) {
                String root = detectRootPrefix(source.toPath());
                if (!root.isEmpty()) {
                    System.out.println("      note: stripping top level folder \"" + root + "\"");
                }
                unzip(source.toPath(), output.toPath(), root);
            } else {
                copyTree(source, output);
            }
            normalizeRoot(output);
            File shaders = new File(output, "shaders");
            if (!new File(shaders, "program/composite3.glsl").isFile()) {
                fail("Copied pack has no shaders/program/composite3.glsl - wrong pack?");
            }
            File backup = new File(output, "DOF_BACKUP_pre_sundial_20261022.zip");
            zipDir(shaders, backup);
            System.out.println("      backup: " + backup.getName());

            System.out.println("[5/6] Injecting DoF patch ...");
            inject(output);

            System.out.println("[6/6] Writing documentation, license and preset ...");
            writeDocs(output);
            writeSundialLicense(sundial, output);
            copyPreset(source, output);

            System.out.println();
            System.out.println("Done.");
            System.out.println("  patched pack : " + output.getAbsolutePath());
            System.out.println("  backup       : " + backup.getAbsolutePath());
            System.out.println("  The source pack was not modified. Delete the output folder to revert.");
        } catch (IllegalArgumentException e) {
            System.out.println("ERROR: " + e.getMessage());
            System.out.println();
            usage();
            System.exit(2);
        } catch (PatchException e) {
            System.out.println("ERROR: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.out.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void usage() {
        System.out.println("Usage: java -jar <this jar> [-s <pack folder|zip>] [-o <output folder>]");
        System.out.println("                             [--sundial <zip>] [--overwrite] [--force]");
        System.out.println();
        System.out.println("  -s, --source   pack to patch (default: \"" + PACK_NAME + "\" next to the jar)");
        System.out.println("  -o, --output   output folder (default: \"" + OUT_NAME + "\" next to the jar)");
        System.out.println("  --sundial      official Sundial Lite 1.2.0 zip (default: searched next to the jar)");
        System.out.println("  --overwrite    replace an existing output folder");
        System.out.println("  --force        skip the source pack fingerprint check");
    }

    private static String next(String[] args, int i, String opt) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Missing value for " + opt);
        }
        return args[i];
    }

    private static final class PatchException extends RuntimeException {
        PatchException(String message) {
            super(message);
        }
    }

    private static void fail(String message) {
        throw new PatchException(message);
    }

    private static File locateSelf() throws Exception {
        URI uri = Patcher.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        File f = new File(uri);
        return f.isFile() ? f.getParentFile() : f;
    }

    // ------------------------------------------------------------------ checks

    private static File findSundial(File dir) throws IOException {
        File direct = new File(dir, SUNDIAL_NAME);
        if (direct.isFile()) {
            return direct;
        }
        File[] entries = dir.listFiles();
        if (entries != null) {
            for (File f : entries) {
                if (f.isFile() && f.length() == SUNDIAL_SIZE && sha1(f).equals(SUNDIAL_SHA1)) {
                    return f;
                }
            }
            for (File f : entries) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(".zip") && sha1(f).equals(SUNDIAL_SHA1)) {
                    return f;
                }
            }
        }
        throw new PatchException(
                "Official Sundial Lite 1.2.0 not found next to the jar.\n"
                        + "      Put \"" + SUNDIAL_NAME + "\" (the Modrinth download) next to the jar,\n"
                        + "      or point to it with --sundial <zip>.\n"
                        + "      sha1   = " + SUNDIAL_SHA1 + "\n"
                        + "      sha512 = " + SUNDIAL_SHA512 + "\n"
                        + "      size   = " + SUNDIAL_SIZE + " bytes\n"
                        + "      page   = " + SUNDIAL_PAGE);
    }

    private static void verifySundial(File zip) throws Exception {
        if (zip == null || !zip.isFile()) {
            fail("Sundial Lite zip not found: " + zip);
        }
        long size = zip.length();
        String s1 = sha1(zip);
        String s512 = sha512(zip);
        if (size != SUNDIAL_SIZE || !SUNDIAL_SHA1.equals(s1) || !SUNDIAL_SHA512.equals(s512)) {
            fail("Sundial Lite hash check failed - not the official Modrinth 1.2.0 file.\n"
                    + "      file   : " + zip.getAbsolutePath() + " (" + size + " bytes)\n"
                    + "      sha1   : " + s1 + "\n"
                    + "      sha512 : " + s512 + "\n"
                    + "      expect : " + SUNDIAL_SHA1 + "\n"
                    + "               " + SUNDIAL_SHA512 + "\n"
                    + "      page   : " + SUNDIAL_PAGE);
        }
    }

    private static void checkFingerprint(File source, boolean sourceIsZip) throws Exception {
        Map<String, String> actual = sourceIsZip ? hashZipEntries(source) : hashFiles(source.toPath());
        List<String> bad = new ArrayList<String>();
        for (Map.Entry<String, String> e : FINGERPRINT.entrySet()) {
            String got = actual.get(e.getKey());
            if (!e.getValue().equals(got)) {
                bad.add(e.getKey() + (got == null ? "  (missing)" : "  sha1=" + got));
            }
        }
        if (bad.isEmpty()) {
            System.out.println("      OK  " + FINGERPRINT.size() + " target files match the original build");
            return;
        }
        System.out.println("      MISMATCH in " + bad.size() + " file(s):");
        for (String s : bad) {
            System.out.println("        " + s);
        }
        if (!force) {
            fail("Source pack fingerprint mismatch.\n"
                    + "      This patcher only supports the untouched\n"
                    + "      \"Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5\" build.\n"
                    + "      Re-run with --force only if you know what you are doing.");
        }
        System.out.println("      --force: continuing anyway");
    }

    private static Map<String, String> hashFiles(Path root) throws Exception {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (String rel : FINGERPRINT.keySet()) {
            Path p = root.resolve(rel.replace('/', File.separatorChar));
            if (Files.isRegularFile(p)) {
                map.put(rel, sha1(p.toFile()));
            }
        }
        return map;
    }

    private static Map<String, String> hashZipEntries(File zip) throws Exception {
        Map<String, String> map = new LinkedHashMap<String, String>();
        ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip.toPath()));
        try {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                String rel = stripRoot(e.getName().replace('\\', '/'));
                if (FINGERPRINT.containsKey(rel) && !map.containsKey(rel)) {
                    map.put(rel, sha1(zis));
                }
            }
        } finally {
            zis.close();
        }
        return map;
    }

    /** zips created from a folder may carry one top level directory. */
    private static String stripRoot(String name) {
        if (FINGERPRINT.containsKey(name)) {
            return name;
        }
        int slash = name.indexOf('/');
        if (slash > 0) {
            String rest = name.substring(slash + 1);
            if (FINGERPRINT.containsKey(rest)) {
                return rest;
            }
        }
        return name;
    }

    // ------------------------------------------------------------------ copy

    private static void copyTree(File src, File dst) throws IOException {
        File[] children = src.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            File target = new File(dst, c.getName());
            if (c.isDirectory()) {
                if (!target.mkdirs() && !target.isDirectory()) {
                    throw new IOException("cannot create " + target);
                }
                copyTree(c, target);
            } else {
                Files.copy(c.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Packs zipped as a folder carry one top level directory; lift its contents up. */
    private static void normalizeRoot(File out) throws IOException {
        if (new File(out, "shaders").isDirectory()) {
            return;
        }
        File[] entries = out.listFiles();
        if (entries != null && entries.length == 1 && entries[0].isDirectory()
                && new File(entries[0], "shaders").isDirectory()) {
            File top = entries[0];
            File[] children = top.listFiles();
            if (children != null) {
                for (File c : children) {
                    Files.move(c.toPath(), new File(out, c.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            deleteTree(top.toPath());
            System.out.println("      note: removed extra top level folder \"" + top.getName() + "\"");
        }
    }

    /** Returns the common top level folder inside the zip ("Foo/"), or "" when there is none. */
    private static String detectRootPrefix(Path zip) throws IOException {
        String prefix = null;
        ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip));
        try {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String n = e.getName().replace('\\', '/');
                if (n.isEmpty()) {
                    continue;
                }
                int slash = n.indexOf('/');
                if (slash <= 0) {
                    return "";
                }
                String top = n.substring(0, slash + 1);
                if (prefix == null) {
                    prefix = top;
                } else if (!prefix.equals(top)) {
                    return "";
                }
            }
        } finally {
            zis.close();
        }
        return prefix == null ? "" : prefix;
    }

    private static void unzip(Path zip, Path dest, String prefix) throws IOException {
        ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip));
        try {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (!name.startsWith(prefix)) {
                    continue;
                }
                String rel = name.substring(prefix.length());
                if (rel.isEmpty()) {
                    continue;
                }
                Path out = dest.resolve(rel).normalize();
                if (!out.startsWith(dest)) {
                    throw new IOException("Unsafe zip entry: " + e.getName());
                }
                // Directory entries are not always stored with a trailing slash.
                if (name.endsWith("/") || e.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                if (Files.isDirectory(out)) {
                    continue;
                }
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            zis.close();
        }
    }

    private static void zipDir(File dir, File out) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out.toPath()));
        try {
            // keep the "shaders/" prefix so the archive can be unpacked into the pack root
            zipWalk(dir.getParentFile(), dir, zos);
        } finally {
            zos.close();
        }
    }

    private static void zipWalk(File root, File cur, ZipOutputStream zos) throws IOException {
        File[] files = cur.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                zipWalk(root, f, zos);
            } else {
                String rel = root.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(rel));
                Files.copy(f.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    // ------------------------------------------------------------------ injection

    private static void inject(File pack) throws Exception {
        injectComposite3(new File(pack, "shaders/program/composite3.glsl"));
        injectSettings(new File(pack, "shaders/lib/shaderSettings/composite3.glsl"));
        injectProperties(new File(pack, "shaders/shaders.properties"));
        injectLang(new File(pack, "shaders/lang/en_US.lang"), "en");
        injectLang(new File(pack, "shaders/lang/zh_CN.lang"), "zh");

        require(new File(pack, "shaders/program/composite3.glsl"), "DoWorldBlurSundial");
        require(new File(pack, "shaders/lib/shaderSettings/composite3.glsl"), "DOF_TECHNIQUE 1");
        require(new File(pack, "shaders/shaders.properties"), "screen.DOF_SUNDIAL_SETTINGS");
        require(new File(pack, "shaders/lang/en_US.lang"), "Sundial Physical");
        require(new File(pack, "shaders/lang/zh_CN.lang"), "Sundial 物理");
        System.out.println("      OK  5 files injected");
    }

    private static void injectComposite3(File file) throws Exception {
        String sep = lineSeparatorOf(file);
        String c = lf(read(file));
        if (c.contains("DoWorldBlurSundial")) {
            fail("composite3.glsl already contains DoWorldBlurSundial (already patched?)");
        }
        // There is one //Includes// in the fragment and one in the vertex section;
        // the DoF kernel belongs before the first one.
        int includes = c.indexOf("//Includes//");
        if (includes < 0) {
            fail("composite3.glsl: cannot find the //Includes// marker");
        }
        if (c.indexOf("void DoWorldBlur") < 0 || c.indexOf("void DoWorldBlur") > includes) {
            fail("composite3.glsl: unexpected layout (DoWorldBlur not before //Includes//)");
        }
        c = c.substring(0, includes) + resource("kernel.txt") + "\n\n" + c.substring(includes);

        String[] lines = c.split("\n", -1);
        int at = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("DoWorldBlur(color, z1, lViewPos);")) {
                if (at >= 0) {
                    fail("composite3.glsl: DoWorldBlur call found more than once");
                }
                at = i;
            }
        }
        if (at < 0) {
            fail("composite3.glsl: DoWorldBlur(color, z1, lViewPos); not found");
        }
        lines[at] = resource("dispatch.txt");
        write(file, toSep(join(lines), sep));
    }

    private static void injectSettings(File file) throws Exception {
        String sep = lineSeparatorOf(file);
        String c = lf(read(file));
        if (c.contains("DOF_TECHNIQUE")) {
            fail("shaderSettings/composite3.glsl already contains DOF_TECHNIQUE (already patched?)");
        }
        String marker = "// Euphoria Patches Settings";
        if (count(c, marker) != 1) {
            fail("shaderSettings/composite3.glsl: cannot find the settings anchor");
        }
        c = c.replace(marker, resource("settings.txt") + "\n\n" + marker);
        write(file, toSep(c, sep));
    }

    private static void injectProperties(File file) throws Exception {
        String sep = lineSeparatorOf(file);
        List<String> lines = toList(lf(read(file)));

        int dof = indexOfPrefix(lines, "screen.DOF_SETTINGS=");
        if (dof < 0) {
            fail("shaders.properties: screen.DOF_SETTINGS not found");
        }
        lines.set(dof, resource("props_dof_line.txt"));

        int sliders = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith("AURORA_UP_R ")) {
                sliders = i;
                break;
            }
        }
        if (sliders < 0) {
            fail("shaders.properties: sliders line (AURORA_UP_R ...) not found");
        }
        if (lines.get(sliders).contains("DOF_SAMPLES")) {
            fail("shaders.properties: sliders already contain DOF_SAMPLES");
        }
        lines.set(sliders, lines.get(sliders) + resource("sliders.txt"));

        int shape = indexOfPrefix(lines, "screen.DOF_SHAPE_SETTINGS=");
        if (shape < 0) {
            fail("shaders.properties: screen.DOF_SHAPE_SETTINGS not found");
        }
        lines.add(shape + 1, resource("props_sub_screen.txt"));

        write(file, toSep(join(lines.toArray(new String[0])), sep));
    }

    private static void injectLang(File file, String tag) throws Exception {
        String sep = lineSeparatorOf(file);
        List<String> lines = toList(lf(read(file)));

        if (indexOfPrefix(lines, "screen.DOF_SUNDIAL_SETTINGS=") >= 0) {
            fail(file.getName() + " already contains screen.DOF_SUNDIAL_SETTINGS (already patched?)");
        }
        int shapeScreen = indexOfPrefix(lines, "screen.DOF_SHAPE_SETTINGS=");
        if (shapeScreen < 0) {
            fail(file.getName() + ": screen.DOF_SHAPE_SETTINGS not found");
        }
        lines.add(shapeScreen + 1, resource("lang_" + tag + "_screen.txt"));

        int dofShape = indexOfPrefix(lines, "option.DOF_SHAPE=");
        if (dofShape < 0) {
            fail(file.getName() + ": option.DOF_SHAPE not found");
        }
        lines.add(dofShape, resource("lang_" + tag + "_block.txt") + "\n");

        appendNote(lines, "option.DOF_POLYGON_SAMPLES.comment=", resource("lang_" + tag + "_note_samples.txt"));
        appendNote(lines, "option.DOF_POLYGON_RINGS.comment=", resource("lang_" + tag + "_note_rings.txt"));

        write(file, toSep(join(lines.toArray(new String[0])), sep));
    }

    private static void appendNote(List<String> lines, String prefix, String note) {
        int i = indexOfPrefix(lines, prefix);
        if (i < 0) {
            fail("lang: " + prefix + " not found");
        }
        if (!lines.get(i).contains("仅对原版算法") && !lines.get(i).contains("Only affects the Legacy algorithm")) {
            lines.set(i, lines.get(i) + note);
        }
    }

    // ------------------------------------------------------------------ extras

    private static void writeDocs(File pack) throws Exception {
        resourceToFile("SUNDIAL_DOF_DESIGN.md", new File(pack, "SUNDIAL_DOF_DESIGN.md"));
        resourceToFile("SUNDIAL_DOF_HANDOVER.md", new File(pack, "SUNDIAL_DOF_HANDOVER.md"));
        resourceToFile("PATCH_README.md", new File(pack, "PATCH_README.md"));
    }

    private static void writeSundialLicense(File sundial, File pack) throws Exception {
        File dir = new File(pack, "LICENSES");
        dir.mkdirs();
        ZipInputStream zis = new ZipInputStream(Files.newInputStream(sundial.toPath()));
        try {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory() && e.getName().equals("LICENSE")) {
                    Files.copy(zis, new File(dir, "Sundial-Lite-1.2.0-GPL-3.0.txt").toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        } finally {
            zis.close();
        }
        System.out.println("      note: LICENSE not found inside the Sundial zip");
    }

    private static void copyPreset(File source, File output) throws IOException {
        String base = source.getAbsolutePath();
        if (source.isFile() && base.toLowerCase().endsWith(".zip")) {
            base = base.substring(0, base.length() - 4);
        }
        File src = new File(base + ".txt");
        File dst = new File(output.getAbsolutePath() + ".txt");
        if (src.isFile() && !dst.exists()) {
            Files.copy(src.toPath(), dst.toPath());
            System.out.println("      preset: " + dst.getName());
        }
    }

    // ------------------------------------------------------------------ text helpers

    private static String resource(String name) throws IOException {
        InputStream in = Patcher.class.getResourceAsStream("/patch/" + name);
        if (in == null) {
            throw new IOException("missing resource: /patch/" + name);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } finally {
            in.close();
        }
    }

    private static void resourceToFile(String name, File target) throws IOException {
        InputStream in = Patcher.class.getResourceAsStream("/docs/" + name);
        if (in == null) {
            throw new IOException("missing resource: /docs/" + name);
        }
        try {
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            in.close();
        }
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void write(File f, String s) throws IOException {
        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    private static void require(File f, String needle) throws IOException {
        if (!read(f).contains(needle)) {
            fail("post-check failed: " + f.getName() + " does not contain " + needle);
        }
    }

    private static String lineSeparatorOf(File f) throws IOException {
        byte[] b = Files.readAllBytes(f.toPath());
        for (int i = 1; i < b.length; i++) {
            if (b[i] == '\n') {
                return b[i - 1] == '\r' ? "\r\n" : "\n";
            }
        }
        return "\n";
    }

    private static String lf(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    private static String toSep(String s, String sep) {
        return lf(s).replace("\n", sep);
    }

    private static String join(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static List<String> toList(String text) {
        return new ArrayList<String>(Arrays.asList(text.split("\n", -1)));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    private static int indexOfPrefix(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ hashes

    private static String sha1(File f) throws IOException {
        return digest(f, "SHA-1");
    }

    private static String sha512(File f) throws IOException {
        return digest(f, "SHA-512");
    }

    private static String digest(File f, String alg) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance(alg);
            byte[] buf = new byte[65536];
            InputStream in = Files.newInputStream(f.toPath());
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            } finally {
                in.close();
            }
            return hex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static String sha1(InputStream in) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return hex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16));
            sb.append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }


}
