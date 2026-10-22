# SundialDoF-Patcher

[中文](README.md) | **English**

A patcher that injects Sundial Physical Depth of Field into
**Complementary Reimagined + Euphoria Patches**.

> The DoF algorithm is ported from [Sundial Lite 1.2.0](https://modrinth.com/shader/sundial-lite) (GPL-3.0):
> physical thin-lens circle of confusion + CoC spread + scatter-as-gather, merged into a single
> `composite3` pass, available as an optional technique alongside the original Legacy DoF.

Supported:

| Source pack | Sundial Lite | Patcher |
|---|---|---|
| Complementary Reimagined **r5.9.3** + Euphoria Patches **1.10.5** | 1.2.0 | **v1.0** |

Other versions are not supported.

## What it is / what it is not

- It is a **patch mechanism**: the jar sits next to your local shader pack, copies it and injects
  code, producing a new pack `C5.9.3_EP1.10.5_DOF/`.
- It is **not a modpack**: the jar contains no Complementary / Euphoria Patches code or assets.
- Before patching it verifies the official Sundial Lite 1.2.0 **size / SHA-1 / SHA-512** and the
  SHA-1 of the five target files. Any mismatch aborts the patch, so it cannot be applied to a
  wrong or already-patched pack.

## Download

Grab `C5.9.3_DOF_Patcher_v1.0.jar` from the Releases page (runs on Java 8+).

## Usage

1. Put the jar in `shaderpacks/`, next to both of these:
   - `ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5` (folder, or a zip with that name)
   - `Sundial_Lite_v1.2.0.zip` — the **official** Modrinth download
2. Run:

   ```bash
   java -jar C5.9.3_DOF_Patcher_v1.0.jar
   ```

3. In game: Shader Options → Camera → Depth of Field Settings → set `World Blur` to
   “Depth of Field”, and `DOF Algorithm` to “Sundial 物理” (Sundial Physical), next to the
   “Sundial 物理设置” sub-page.

Optional arguments: `-s` source pack, `-o` output folder, `--sundial` explicit official zip,
`--overwrite` replace output, `--force` skip the source fingerprint check.

## Checksums

Sundial Lite 1.2.0 (official Modrinth file):

| Field | Value |
|---|---|
| size | `1513863` |
| SHA-1 | `8b079dde0a481b554861131e26407da89a2250c6` |
| SHA-512 | `c334f92acc37ef30dfa2cec5c0e7179984ad52651fa8c6e3b4251990305498afb5a3997ba73dfc0b8c7700db4a18edf3b9b35a2ed60050dc2802e5000f83b973` |

Source pack files (SHA-1, confirms an unmodified r5.9.3 + EP1.10.5):

| File | SHA-1 |
|---|---|
| `shaders/program/composite3.glsl` | `f7c1640182a8669bcb6eeb2d359dfb62f7f7ce3f` |
| `shaders/lib/shaderSettings/composite3.glsl` | `95f1e4373043888e4b0ddfbc8b141e7a81e36075` |
| `shaders/shaders.properties` | `e7405ea55dcd087ee9d55c7a18405bfb6d2102c9` |
| `shaders/lang/en_US.lang` | `a848499bef2a3fb2ca9fbe774ed129fcd28dc3a3` |
| `shaders/lang/zh_CN.lang` | `343e1fd17403601ae63f5b4c9efb64c92e2ef984` |

## Important: the generated pack is for personal use only

Under the **Euphoria Patches License Agreement v1.1 §2.3**, the generated
`C5.9.3_EP1.10.5_DOF` pack is a Modified Pack and is **only for your own local use**.

- **Do not redistribute it**: no cloud uploads, no bundling into modpacks, no sharing in any form.
- Want to share with a friend? Share this patcher and let them generate it from their own
  unmodified pack.
- This repository and the Releases contain no generated shader pack.

## License & credits

- This repository's code and injection fragments are released under **GPL-3.0** (the DoF code is
  derived from Sundial Lite 1.2.0). See [LICENSE](LICENSE).
- **Complementary Reimagined** — EminGT / Complementary Development,
  [complementary.dev](https://www.complementary.dev), under the Complementary License Agreement 1.7.
- **Euphoria Patches** — SpacEagle17, [euphoriapatches.com](https://euphoriapatches.com),
  under the Euphoria Patches License Agreement v1.1.
- **Sundial Lite** — GeForceLegend, [modrinth.com/shader/sundial-lite](https://modrinth.com/shader/sundial-lite), GPL-3.0.
- **AI collaboration** — DeepSeek Harness (`deepseek-flash` model), assisted with the algorithm port
  and the patcher.
- This tool is not affiliated with any of the projects above and does not contain or redistribute
  their content. The output name `C5.9.3_EP1.10.5_DOF` differs from the original pack name and does
  not contain “Euphoria”.
