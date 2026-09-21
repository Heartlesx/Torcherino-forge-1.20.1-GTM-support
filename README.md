# Torcherino 1.12.2 - GTCE 支持版

加速火把的 GregTech Community Edition 支持分支：可以加速 GTCE 机器，但**不会因为额外 tick 导致机器跳电**。

- 原版加速火把：额外 tick 会让机器以 ×(1+速度) 的倍率消耗 EU，电网瞬间被抽干（跳电）
- 本版本：加工机器 EU 消耗保持 1x，发电机燃料消耗保持 1x，真正做到"只加速、不多耗"

## 特性

| 对象 | 行为 |
|---|---|
| GTCE 加工机器（耗电） | 配方进度 ×(1+速度倍率)，EU 消耗保持 1x |
| GTCE 发电机 | 燃料消耗保持 1x，发电 ×(1+速度倍率)，额外电力立即送入电网 |
| 锅炉 / 研究站等自定义逻辑机器 | 保持原版加速火把行为（完整 tick 加速） |
| 非 GTCE 方块 | 完全不受影响 |
| 未安装 GTCE | 完全退回原版行为（GTCE 为可选依赖） |

## 安装

1. Minecraft 1.12.2 + Forge（14.23.5.2860 测试通过）
2. 把 `dist/Torcherino-1.12.2-7.6-gtce-no-extra-eu-GT特供.jar` 放进 `mods/`
3. 替换原版加速火把（两者 modid 相同，不能共存）

## 构建

本项目不包含原版 mod 文件（版权原因），构建前请自备原版 `torcherino-7.6.jar`
并放到与 `patch/` 同级的目录（即仓库根目录）：

```
torcherino-7.6.jar
patch/
  build.ps1
  stub/  src/  tools/  test/  resources/
```

然后执行（需要 JDK 17，路径在 `patch/build.ps1` 顶部可改）：

```powershell
powershell -ExecutionPolicy Bypass -File patch/build.ps1
```

脚本会编译兼容层、给原版 jar 打字节码补丁、注入汉化资源，并运行离线测试；
产物输出到 `out/Torcherino-1.12.2-7.6-gtce-GT特供.jar`。

## 原理

原版 `torcherino-7.6.jar` 无公开源码，本项目采用「反编译分析 + 单指令字节码替换 + 兼容层」方案：

1. 把加速循环里唯一一处 `ITickable.update()` 调用替换为 `GTCECompat.tickTile(...)`（全 jar 仅 1 处）
2. `GTCECompat`（Java 8，全反射访问 GTCE，零编译期依赖）：
   - **耗能机器**：直接推进 `AbstractRecipeLogic.progressTime`，跳过整机 tick → EU 消耗不增加
   - **发电机**：额外注入一份 EU 后立即驱动能量容器把电推给电网（避免缓冲灌满导致燃烧进度停滞）
   - **回退**：非 GTCE 方块、锅炉、研究站、未知容器结构、任何反射失败 → 维持原版完整 tick
3. 附带汉化：`zh_cn.lang` + `mcmod.info` 中文

诊断：日志中以 `[Torcherino-GTCE]` 开头的行会说明每类机器的接管状态与回退原因。

## 已验证环境

- GT Lite 整合包（gtlitecore 用 Mixin 覆盖了发电机逻辑，已兼容）
- GTCE `gregtech-1.12.2-master-#2901`

## 致谢

- 原版 Torcherino 作者：Moze_Intel、sci4me、NinjaPhenix1
- 本项目仅对原版 jar 做字节码补丁，不重新分发原版文件
