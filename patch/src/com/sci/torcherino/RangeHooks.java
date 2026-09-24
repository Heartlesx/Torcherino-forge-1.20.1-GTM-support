package com.sci.torcherino;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.sci.torcherino.blocks.tiles.TileTorcherino;
import com.sci.torcherino.network.PacketHandler;
import com.sci.torcherino.network.RangeScreenMessage;
import com.sci.torcherino.network.RangeUpdateMessage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 字节码注入点的实现（规则表见 tools/Patcher.java）：
 * 1) capturePlayer   替代 EventHandler 里的 event.getEntityPlayer()
 * 2) updateBounds    替代 TileTorcherino.updateCachedModeIfNeeded()
 * 3) changeMode      替代 TileTorcherino.changeMode(boolean)
 * 4) describe        替代 TileTorcherino.getDescription()
 * 5) initPackets     替代 PacketHandler.preInit()
 *
 * 旧存档火把（RangeStore 里没有记录）一律回退到原版行为，因此升级不会让已有火把失效。
 */
public final class RangeHooks {

    /** 本次右键交互的玩家；由 capturePlayer 在事件处理开头写入，同一线程内紧接着使用。 */
    private static final ThreadLocal<EntityPlayer> CURRENT_PLAYER = new ThreadLocal<EntityPlayer>();

    private static int limitXZ = RangeValues.LIMIT_XZ;
    private static int limitY = RangeValues.LIMIT_Y;

    private static final Field FIELD_MODE = findField("mode");
    private static final Field FIELD_SPEED = findField("speed");
    private static final Field FIELD_X_MIN = findField("xMin");
    private static final Field FIELD_Y_MIN = findField("yMin");
    private static final Field FIELD_Z_MIN = findField("zMin");
    private static final Field FIELD_X_MAX = findField("xMax");
    private static final Field FIELD_Y_MAX = findField("yMax");
    private static final Field FIELD_Z_MAX = findField("zMax");
    private static final Method METHOD_ORIGINAL_BOUNDS = findMethod("updateCachedModeIfNeeded");
    private static final Method METHOD_SPEED = findMethod("speed", int.class);

    private RangeHooks() {
    }

    /** 注入点 1：EventHandler 需要玩家对象，但原方法只把火把留在栈上，所以在这里顺手捕获。 */
    public static EntityPlayer capturePlayer(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        CURRENT_PLAYER.set(player);
        return player;
    }

    /** 注入点 2：每 tick 的扫描边界。有记录用记录（三轴独立），无记录走原版（按 mode 的正方形）。 */
    public static void updateBounds(TileTorcherino torch) {
        World world = torch.func_145831_w();
        if (world == null || world.field_72995_K) {
            return;
        }
        BlockPos pos = torch.func_174877_v();
        RangeValues values = RangeStore.get(world, pos);
        if (values == null) {
            invokeOriginalBounds(torch);
            return;
        }
        RangeValues.Bounds bounds = values.boundsAt(pos.func_177958_n(), pos.func_177956_o(), pos.func_177952_p());
        setInt(FIELD_X_MIN, torch, bounds.xMin);
        setInt(FIELD_Y_MIN, torch, bounds.yMin);
        setInt(FIELD_Z_MIN, torch, bounds.zMin);
        setInt(FIELD_X_MAX, torch, bounds.xMax);
        setInt(FIELD_Y_MAX, torch, bounds.yMax);
        setInt(FIELD_Z_MAX, torch, bounds.zMax);
    }

    /** 注入点 3：右键交互。普通右键开设置界面，潜行右键把范围显示出来（二期换成绿色方框）。 */
    public static void changeMode(TileTorcherino torch, boolean modifier) {
        EntityPlayer player = CURRENT_PLAYER.get();
        CURRENT_PLAYER.remove();
        World world = torch.func_145831_w();
        if (world == null || world.field_72995_K || !(player instanceof EntityPlayerMP)) {
            return;
        }
        BlockPos pos = torch.func_174877_v();
        sendScreen((EntityPlayerMP) player, torch, pos, currentValues(world, torch),
                modifier ? RangeScreenMessage.ACTION_SHOW : RangeScreenMessage.ACTION_OPEN);
    }

    /** 注入点 4：动作条文本。有记录显示真实形状，没记录沿用原版文本。 */
    public static TextComponentString describe(TileTorcherino torch) {
        World world = torch.func_145831_w();
        if (world != null) {
            RangeValues values = RangeStore.get(world, torch.func_174877_v());
            if (values != null) {
                return new TextComponentString(values.describe(speedPercent(torch, values.speed())));
            }
        }
        return torch.getDescription();
    }

    /** 注入点 5：注册网络包（沿用原版通道，占用 id 1 / 2）+ 读取上限配置。 */
    public static void initPackets() {
        loadLimits();
        PacketHandler.preInit();
        Torcherino.network.registerMessage(RangeUpdateMessage.Handler.class, RangeUpdateMessage.class, 1, Side.SERVER);
        Torcherino.network.registerMessage(RangeScreenMessage.Handler.class, RangeScreenMessage.class, 2, Side.CLIENT);
    }

    /** 服务端收到界面提交：校验上限 -> 落盘 -> 应用到火把 -> 回显（由 C2S 处理器调度到服务端线程后调用）。 */
    public static void applyUpdate(EntityPlayerMP player, RangeUpdateMessage message) {
        World world = player.field_70170_p;
        if (world == null || world.field_72995_K) {
            return;
        }
        BlockPos pos = BlockPos.func_177969_a(message.pos());
        TileEntity tile = world.func_175625_s(pos);
        if (!(tile instanceof TileTorcherino)) {
            return; // 火把已被拆掉或是别的方块：忽略（不信任客户端）
        }
        TileTorcherino torch = (TileTorcherino) tile;
        RangeValues values = message.values().clamped(limitXZ, limitY);
        RangeStore.set(world, pos, values);
        setByte(FIELD_SPEED, torch, (byte) values.speed());
        activate(torch);
        sendScreen(player, torch, pos, values, RangeScreenMessage.ACTION_REFRESH);
        player.func_146105_b((net.minecraft.util.text.ITextComponent)
                new TextComponentString(values.describe(speedPercent(torch, values.speed()))), true);
    }

    public static int limitXZ() {
        return limitXZ;
    }

    public static int limitY() {
        return limitY;
    }

    private static void sendScreen(EntityPlayerMP player, TileTorcherino torch, BlockPos pos, RangeValues values, byte action) {
        Torcherino.network.sendTo(new RangeScreenMessage(pos.func_177986_g(), action, values,
                speedMultiplier(torch), limitXZ, limitY), player);
    }

    /** 火把变体的倍率（×1/×9/×81），等于 speed(1) 的返回值。 */
    private static int speedMultiplier(TileTorcherino torch) {
        return Math.max(1, speedPercent(torch, 1) / 100);
    }

    private static RangeValues currentValues(World world, TileTorcherino torch) {
        RangeValues stored = RangeStore.get(world, torch.func_174877_v());
        if (stored != null) {
            return stored;
        }
        return RangeValues.fromLegacy(readByte(FIELD_MODE, torch), readByte(FIELD_SPEED, torch));
    }

    /** 火把变体决定倍率（×1/×9/×81），文本里的百分比要按变体折算。 */
    private static int speedPercent(TileTorcherino torch, int speed) {
        if (METHOD_SPEED == null) {
            return speed * 100;
        }
        try {
            return ((Integer) METHOD_SPEED.invoke(torch, Integer.valueOf(speed))).intValue() * 100;
        } catch (Throwable failure) {
            return speed * 100;
        }
    }

    /** 界面里把速度调成 0 表示停止，仍按原版语义；这里只负责把"曾经停止"的 mode 从 0 抬起来。 */
    private static void activate(TileTorcherino torch) {
        if (readByte(FIELD_MODE, torch) == 0) {
            setByte(FIELD_MODE, torch, (byte) 1);
        }
    }

    private static void loadLimits() {
        try {
            File file = new File(new File(Loader.instance().getConfigDir(), "sci4me"), "Torcherino.cfg");
            Configuration config = new Configuration(file);
            try {
                config.load();
                limitXZ = config.getInt("rangeLimitXZ", "ranges", RangeValues.LIMIT_XZ, 1, 64,
                        "Max half width on X/Z (9x3x9 = 4); the shape is always odd: 2*r+1");
                limitY = config.getInt("rangeLimitY", "ranges", RangeValues.LIMIT_Y, 0, 16,
                        "Max half height on Y (3 layers = 1); the shape is always odd: 2*r+1");
            } finally {
                if (config.hasChanged()) {
                    config.save();
                }
            }
        } catch (Throwable failure) {
            log("failed to read ranges config, using defaults: " + failure);
        }
    }

    private static void invokeOriginalBounds(TileTorcherino torch) {
        if (METHOD_ORIGINAL_BOUNDS == null) {
            return;
        }
        try {
            METHOD_ORIGINAL_BOUNDS.invoke(torch);
        } catch (Throwable failure) {
            log("failed to compute vanilla bounds: " + failure);
        }
    }

    private static Field findField(String name) {
        try {
            Field field = TileTorcherino.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable missing) {
            log("Torcherino field not found: " + name);
            return null;
        }
    }

    private static Method findMethod(String name, Class<?>... parameters) {
        try {
            Method method = TileTorcherino.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Throwable missing) {
            log("Torcherino method not found: " + name);
            return null;
        }
    }

    private static byte readByte(Field field, TileTorcherino torch) {
        if (field == null) {
            return 0;
        }
        try {
            return field.getByte(torch);
        } catch (Throwable failure) {
            return 0;
        }
    }

    private static void setByte(Field field, TileTorcherino torch, byte value) {
        if (field == null) {
            return;
        }
        try {
            field.setByte(torch, value);
        } catch (Throwable failure) {
            log("failed to write Torcherino field: " + failure);
        }
    }

    private static void setInt(Field field, TileTorcherino torch, int value) {
        if (field == null) {
            return;
        }
        try {
            field.setInt(torch, value);
        } catch (Throwable failure) {
            log("failed to write Torcherino bounds: " + failure);
        }
    }

    private static void log(String message) {
        // 与 GTCECompat 一致：走标准输出，避免为了日志再引一个依赖
        System.out.println("[Torcherino-Range] " + message);
    }
}
