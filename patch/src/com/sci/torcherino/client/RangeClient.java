package com.sci.torcherino.client;

import com.sci.torcherino.RangeValues;
import com.sci.torcherino.network.RangeScreenMessage;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IThreadListener;

/** 客户端侧处理（只会在客户端被加载）：打开设置界面、记录"当前要显示的火把"。 */
public final class RangeClient {

    /** 方框显示时长（毫秒）；二期渲染按它淡出。 */
    private static final long DISPLAY_MILLIS = 5000L;

    private static RangeScreen open;
    private static long displayPos;
    private static RangeValues displayValues;
    private static long displayUntil;

    private RangeClient() {
    }

    public static void handle(final RangeScreenMessage message) {
        Minecraft minecraft = Minecraft.func_71410_x();
        if (minecraft == null) {
            return;
        }
        // 包是在网络线程处理的：界面/鼠标抓取必须在客户端主线程操作，否则会出现"光标消失"
        ((IThreadListener) minecraft).func_152344_a(new Runnable() {
            @Override
            public void run() {
                apply(message);
            }
        });
    }

    private static void apply(RangeScreenMessage message) {
        // 任何一次交互（开界面 / 潜行显示 / 服务端确认的修改）都让方框亮起来 5 秒
        displayPos = message.pos();
        displayValues = message.values();
        displayUntil = System.currentTimeMillis() + DISPLAY_MILLIS;
        if (message.action() == RangeScreenMessage.ACTION_SHOW) {
            return; // 显示请求：只有方框（动作条文本由原版代码路径给出）
        }
        Minecraft minecraft = Minecraft.func_71410_x();
        if (open != null && open.pos() == message.pos() && minecraft.field_71462_r == open) {
            open.updateValues(message.values()); // 服务端校验后的权威值回显
            return;
        }
        if (message.action() == RangeScreenMessage.ACTION_REFRESH) {
            return; // 界面已经关掉了：只更新，不再弹开
        }
        open = new RangeScreen(message.pos(), message.values(), message.limitXZ(), message.limitY(),
                message.speedMultiplier());
        minecraft.func_147108_a(open);
    }

    /** 当前要显示的火把坐标；没有显示目标时返回 Long.MIN_VALUE。 */
    public static long displayPos() {
        return displayValues == null ? Long.MIN_VALUE : displayPos;
    }

    public static RangeValues displayValues() {
        return System.currentTimeMillis() < displayUntil ? displayValues : null;
    }
}
