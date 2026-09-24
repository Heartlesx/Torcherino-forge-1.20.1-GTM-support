# Torcherino 1.12.2 - GTCE 支持版

加速火把的 GregTech Community Edition 支持分支：可以加速 GTCE 机器，但**不会因为额外 tick 导致机器跳电**；
并额外提供**可自定义的加速范围 + 设置界面 + 世界内范围方框**。

- 原版加速火把：额外 tick 会让机器以 ×(1+速度) 的倍率消耗 EU，电网瞬间被抽干（跳电）
- 本版本：加工机器 EU 消耗保持 1x，发电机燃料消耗保持 1x，真正做到"只加速、不多耗"

## 特性

### 1. GTCE 兼容（不额外耗电）

| 对象 | 行为 |
|---|---|
| GTCE 加工机器（耗电） | 配方进度 ×(1+速度倍率)，EU 消耗保持 1x |
| GTCE 发电机 | 燃料消耗保持 1x，发电 ×(1+速度倍率)，额外电力立即送入电网 |
| 大型流体钻机（`FluidDrillLogic`） | 保留其自身行为，额外 tick 多扣的 EU 退还 → 净消耗 1x |
| 锅炉 / 研究站等自定义逻辑机器 | 保持原版加速火把行为（完整 tick 加速） |
| 非 GTCE 方块 | 完全不受影响 |
| 未安装 GTCE | 完全退回原版行为（GTCE 为可选依赖） |

### 2. 自定义范围 / 设置界面 / 范围方框

- **普通右键**火把 → 打开设置界面（原版菜单风格）：X / Z / Y 三轴独立（边长恒为奇数）+ 速度档（0 = 停止）
- **潜行右键** → 世界内显示该火把范围：绿色线框 + 最底层淡绿半透明面，**穿透方块可见**，5 秒后消失
- 原版做不到的形状现在可以设：例如 `9x1x5`、`5x3x7`（原版 X/Z 恒为正方形、Y 恒 3 层）
- 只有**被调整过**的火把才写独立存档数据（`WorldSavedData`，每维度一份）；
  未调整过的旧存档火把完全走原版逻辑 → 升级不影响已有存档，装回旧 jar 也不会读到异常值
- 上限默认 = 原版上限，可在 `config/sci4me/Torcherino.cfg` 的 `[ranges]` 放开：
  `rangeLimitXZ`（默认 4 → 9x3x9）、`rangeLimitY`（默认 1 → 3 层）；服务端按同一上限校验客户端提交
- 注意：速度档入口从 `Shift+右键` 移到界面里；"停止"仍可用速度档 0

## 安装

1. Minecraft 1.12.2 + Forge（14.23.5.2860 测试通过）
2. 把 `dist/Torcherino-1.12.2-7.6-gtce-no-extra-eu-GT特供.jar` 放进 `mods/`
3. 替换原版加速火把（两者 modid 相同，不能共存）

## 构建

本项目不包含原版 mod 文件与编译依赖（版权/体积原因），构建前需要自己准备：

1. 原版 `torcherino-7.6.jar` 放到仓库根目录（与 `patch/` 同级）
2. `libs/` 目录放入以下编译依赖：
   - `forge-1.12.2-14.23.5.2860-srg.jar`（**SRG 命名**的 MC+Forge jar，可在 ForgeGradle 的
     `minecraft_user_repo` 或 Gradle 缓存里找到）
   - `netty-buffer-4.1.82.Final.jar`、`netty-common-4.1.82.Final.jar`
   - `guava-21.0.jar`
   - `lwjgl-2.9.4-nightly-20150209.jar`（GL11 立即模式绘制用）

目录结构：

```
torcherino-7.6.jar
libs/                （上面这些 jar）
patch/
  build.ps1
  stub/  src/  tools/  test/  resources/
```

然后执行（需要 JDK 17，路径在 `patch/build.ps1` 顶部可改）：

```powershell
powershell -ExecutionPolicy Bypass -File patch/build.ps1
```

脚本流程：编译 hook → 编译 Patcher → 给原版 jar 打字节码补丁（规则表 + 命中数断言）→ 注入资源 →
校验必需类是否都在 jar 内 → 从**最终 jar** 运行两套离线测试（GTCE 兼容 50 项 + 范围 28 项）；
产物输出到 `out/Torcherino-1.12.2-7.6-gtce-GT特供.jar`。

## 原理

原版 `torcherino-7.6.jar` 无公开源码，本项目采用「反编译分析 + 同形调用替换 + 注入层」方案。
`patch/tools/Patcher.java` 里维护一张**规则表**（`owner+name+desc` → hook 静态方法），每条规则断言命中数，
命中数不符直接构建失败；所有替换都是**同形**的（实例调用的接收者变成静态方法的第一个参数，参数与返回值不变），
因此不需要重算 max stack / stackmap frames。

| 规则 | 被替换的调用 | 钩子 | 作用 |
|---|---|---|---|
| gtce-tile-update | `ITickable.func_73660_a()` | `GTCECompat.tickTile` | GTCE 机器加速但不额外耗电（耗能机器只推进进度 / 发电机 boost+flush / 流体钻机退还额外耗电） |
| torch-bounds | `TileTorcherino.updateCachedModeIfNeeded()` | `RangeHooks.updateBounds` | 每 tick 扫描边界：有记录用记录（三轴独立），无记录走原版 |
| torch-changemode | `TileTorcherino.changeMode(boolean)` | `RangeHooks.changeMode` | 普通右键开界面 / 潜行右键显示范围 |
| torch-describe | `TileTorcherino.getDescription()` | `RangeHooks.describe` | 动作条显示真实形状与折算后的速度 |
| packets-init | `PacketHandler.preInit()` | `RangeHooks.initPackets` | 复用原通道注册 id 1/2 两个包 + 读范围上限配置 |
| player-capture | `PlayerInteractEvent$RightClickBlock.getEntityPlayer()` | `RangeHooks.capturePlayer` | 原方法栈上没有玩家对象，在这里捕获 |
| client-hooks-init | `KeyHandler.preInit()` | `RangeClientHooks.init` | 注册世界内范围方框的渲染监听（客户端专属类，服务端不加载） |

- 数据：`RangeValues`（范围/速度模型 + 边界计算）、`RangeStore`（`WorldSavedData` 独立存档，键 = 坐标）
- 网络：`RangeUpdateMessage`（C2S 提交，服务端按上限校验并落盘）、`RangeScreenMessage`（S2C：开界面 / 显示 / 回显）
- 客户端：`RangeScreen`（原版风格界面）、`RangeRenderer`（`RenderWorldLastEvent` + GL11 画框与半透明面）、
  `RangeClient`（由包处理器反射调用，并把界面操作调度回客户端主线程）

诊断日志前缀：`[Torcherino-GTCE]`（兼容层接管/回退原因）、`[Torcherino-Range]`（范围功能异常）。

## 已验证环境

- GT Lite 整合包（gtlitecore 用 Mixin 覆盖了发电机逻辑，已兼容）
- GTCE `gregtech-1.12.2-master-#2901`
- 离线测试：78 项全过（GTCE 兼容 50 + 范围 28）

## 已知限制

- 设置界面目前是原版控件风格（未做美化）
- 范围方框的线宽依赖显卡驱动（部分驱动会强制成 1px）；颜色与显示时长目前写死
- 速度档只能在界面里调（不再占用 Shift+右键）

## 致谢

- 原版 Torcherino 作者：Moze_Intel、sci4me、NinjaPhenix1
- 本项目仅对原版 jar 做字节码补丁，不重新分发原版文件
