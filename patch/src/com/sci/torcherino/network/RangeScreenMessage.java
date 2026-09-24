package com.sci.torcherino.network;

import com.sci.torcherino.RangeValues;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** 服务端 -> 客户端：打开设置界面（ACTION_OPEN）或把范围显示出来（ACTION_SHOW）。 */
public class RangeScreenMessage implements IMessage {

    /** 打开设置界面（玩家右键时下发）。 */
    public static final byte ACTION_OPEN = 0;
    /** 把范围显示出来（潜行右键时下发；二期用于画绿色方框）。 */
    public static final byte ACTION_SHOW = 1;
    /** 只刷新已打开的界面（服务端校验后的回显），不会把刚关掉的界面重新弹开。 */
    public static final byte ACTION_REFRESH = 2;

    private long pos;
    private byte action;
    private byte xRange;
    private byte zRange;
    private byte yRange;
    private byte speed;
    private byte speedMultiplier;
    private byte limitXZ;
    private byte limitY;

    public RangeScreenMessage() {
    }

    public RangeScreenMessage(long pos, byte action, RangeValues values, int speedMultiplier, int limitXZ, int limitY) {
        this.pos = pos;
        this.action = action;
        this.xRange = (byte) values.xRange();
        this.zRange = (byte) values.zRange();
        this.yRange = (byte) values.yRange();
        this.speed = (byte) values.speed();
        this.speedMultiplier = (byte) speedMultiplier;
        this.limitXZ = (byte) limitXZ;
        this.limitY = (byte) limitY;
    }

    public long pos() {
        return pos;
    }

    public byte action() {
        return action;
    }

    /** 火把变体的速度倍率（×1/×9/×81），客户端用它把档位折算成百分比。 */
    public int speedMultiplier() {
        return speedMultiplier;
    }

    public int limitXZ() {
        return limitXZ;
    }

    public int limitY() {
        return limitY;
    }

    public RangeValues values() {
        return new RangeValues(xRange, zRange, yRange, speed);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = buf.readLong();
        action = buf.readByte();
        xRange = buf.readByte();
        zRange = buf.readByte();
        yRange = buf.readByte();
        speed = buf.readByte();
        speedMultiplier = buf.readByte();
        limitXZ = buf.readByte();
        limitY = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        buf.writeByte(action);
        buf.writeByte(xRange);
        buf.writeByte(zRange);
        buf.writeByte(yRange);
        buf.writeByte(speed);
        buf.writeByte(speedMultiplier);
        buf.writeByte(limitXZ);
        buf.writeByte(limitY);
    }

    public static class Handler implements IMessageHandler<RangeScreenMessage, IMessage> {
        private static final String CLIENT_CLASS = "com.sci.torcherino.client.RangeClient";

        @Override
        public IMessage onMessage(RangeScreenMessage message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                handleOnClient(message);
            }
            return null;
        }

        /**
         * 用反射调用客户端专属类：这样本类的常量池里不出现 net.minecraft.client.*，
         * 专用服务器（没有客户端类）加载/校验这个处理器时也不会炸。
         */
        private static void handleOnClient(RangeScreenMessage message) {
            try {
                Class.forName(CLIENT_CLASS).getMethod("handle", RangeScreenMessage.class).invoke(null, message);
            } catch (Throwable failure) {
                System.out.println("[Torcherino-Range] client handler failed: " + failure);
            }
        }
    }
}
