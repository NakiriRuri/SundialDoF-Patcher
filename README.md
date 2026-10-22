# SundialDoF-Patcher

**中文** | [English](README.en.md)

把 **Sundial 物理景深**注入到 **Complementary Reimagined + Euphoria Patches** 的补丁工具。

> 景深算法移植自 [Sundial Lite 1.2.0](https://modrinth.com/shader/sundial-lite)（GPL-3.0）：
> 物理薄透镜弥散圆 + CoC spread + scatter-as-gather，合并进 `composite3` 单 pass，
> 作为可选算法与原来的 Legacy 景深并存。

当前支持：

| 源包 | Sundial Lite | 补丁版本 |
|---|---|---|
| Complementary Reimagined **r5.9.3** + Euphoria Patches **1.10.5** | 1.2.0 | **v1.0** |

其它版本暂不支持。

## 它是什么，不是什么

- 是**补丁机制**：jar 放在你本地光影包旁边，复制一份并注入代码，输出新包 `C5.9.3_EP1.10.5_DOF/`。
- 不是**整合包**：jar 里没有 Complementary / Euphoria Patches 的任何代码或资源。
- 注入前会校验：官方 Sundial Lite 1.2.0 的**大小 / SHA-1 / SHA-512**，以及源包 5 个目标文件的 SHA-1。
  对不上就拒绝，避免打进错误版本或重复注入。

## 下载

Release 页面里的 `C5.9.3_DOF_Patcher_v1.0.jar`（Java 8+ 运行）。

## 使用

1. 把 jar 放进 `shaderpacks/`，与下面两样**同目录**：
   - `ComplementaryReimagined_r5.9.3 + EuphoriaPatches_1.10.5`（文件夹，或同名 zip）
   - `Sundial_Lite_v1.2.0.zip` —— 从 Modrinth 下载的**官方文件**
2. 运行：

   ```bash
   java -jar C5.9.3_DOF_Patcher_v1.0.jar
   ```

3. 游戏内：光影设置 → 摄像机 → 景深设置 → `视界模糊类型` 选“景深”，
   `景深算法` 选“Sundial 物理”（与“Sundial 物理设置”子页同排）。

可选参数：`-s` 源包路径、`-o` 输出目录、`--sundial` 指定官方 zip、`--overwrite` 覆盖输出、`--force` 跳过源包校验。

## 校验值

Sundial Lite 1.2.0（Modrinth 官方）：

| 项 | 值 |
|---|---|
| size | `1513863` |
| SHA-1 | `8b079dde0a481b554861131e26407da89a2250c6` |
| SHA-512 | `c334f92acc37ef30dfa2cec5c0e7179984ad52651fa8c6e3b4251990305498afb5a3997ba73dfc0b8c7700db4a18edf3b9b35a2ed60050dc2802e5000f83b973` |

源包目标文件（SHA-1，用于确认是未修改的 r5.9.3 + EP1.10.5）：

| 文件 | SHA-1 |
|---|---|
| `shaders/program/composite3.glsl` | `f7c1640182a8669bcb6eeb2d359dfb62f7f7ce3f` |
| `shaders/lib/shaderSettings/composite3.glsl` | `95f1e4373043888e4b0ddfbc8b141e7a81e36075` |
| `shaders/shaders.properties` | `e7405ea55dcd087ee9d55c7a18405bfb6d2102c9` |
| `shaders/lang/en_US.lang` | `a848499bef2a3fb2ca9fbe774ed129fcd28dc3a3` |
| `shaders/lang/zh_CN.lang` | `343e1fd17403601ae63f5b4c9efb64c92e2ef984` |

## 重要：生成的包仅限自己使用

按 **Euphoria Patches License Agreement v1.1 §2.3**，补丁生成的
`C5.9.3_EP1.10.5_DOF` 包属于 Modified Pack，**仅限你本人在本地使用**。

- **不可再分发**：不要把它上传网盘、打包进整合包、或以任何形式分享。
- 想分享给朋友，请分享这个补丁，让对方用自己下载的原包自行生成。
- 本仓库和 Release 里也不包含任何生成好的光影包。

## 许可与致谢

- 本仓库代码与注入片段按 **GPL-3.0** 发布（景深代码是 Sundial Lite 1.2.0 的衍生作品），
  全文见 [LICENSE](LICENSE)。
- **Complementary Reimagined** —— EminGT / Complementary Development，
  [complementary.dev](https://www.complementary.dev)，遵循 Complementary License Agreement 1.7。
- **Euphoria Patches** —— SpacEagle17，[euphoriapatches.com](https://euphoriapatches.com)，
  遵循 Euphoria Patches License Agreement v1.1。
- **Sundial Lite** —— GeForceLegend，[modrinth.com/shader/sundial-lite](https://modrinth.com/shader/sundial-lite)，GPL-3.0。
- **AI 协作** —— DeepSeek Harness（deepseek-flash 模型），协助完成算法移植与补丁工具。
- 本工具与上述项目**无隶属关系**，不包含、不重新分发它们的内容。
  输出包名 `C5.9.3_EP1.10.5_DOF` 与原包名不同、且不含 “Euphoria”。
