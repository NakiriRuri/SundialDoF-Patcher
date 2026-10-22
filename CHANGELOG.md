# Changelog

## v1.0 — 2026-10-22

首个版本：把 Sundial 物理景深注入 Complementary Reimagined r5.9.3 + Euphoria Patches 1.10.5。

### 功能

- 新增 `DOF_TECHNIQUE`（0 = Legacy / 1 = Sundial 物理，默认 1），Legacy 景深完整保留。
- 算法：物理薄透镜 CoC + CoC spread + scatter-as-gather，合并进 `composite3` 单 pass。
- 新参数：`DOF_SAMPLES`、`DOF_COC_SPREAD_SAMPLES`、`DOF_APERTURE_DIAMETER_SCALE`、
  `DOF_FOCAL_LENGTH`、`DOF_MAX_BLUR_RADIUS`。
- 兼容 EP 原有功能：形状/旋转、色差、变形、蹲伏+望远镜、焦平面高亮、移轴
  （窗口深度偏移精确换算焦距）、`WB_DOF_I`（作为光圈倍率）、`WB_DOF_FOCUS` 三种模式。
- LOD：Distant Horizons / Voxy 深度回退；手部与 <11 cm 按 far 处理。
- UI：`景深算法` 下拉与 `Sundial 物理设置` 子页同排；中英文案与子页翻译。

### 补丁机制

- 自定位 jar，同目录读取原包（文件夹或 zip）与官方 Sundial Lite 1.2.0。
- 注入前校验：Sundial 大小 / SHA-1 / SHA-512（Modrinth 官方值）+ 源包 5 文件 SHA-1。
- 锚点注入 + 重复注入检测；输出整包副本 + 备份 zip + 文档 + GPL 全文 + 预设副本。
- 测试：288 次宏展开配平、文件夹/zip 输入、篡改 Sundial、缺 Sundial、已打补丁、
  `--overwrite`；输出与手工参考注入逐字节一致。
