package com.sci.torcherino.network;

import com.sci.torcherino.RangeHooks;
import com.sci.torcherino.RangeValues;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** 客户端 -> 服务端：提交某台火把的范围/速度（服务端会按上限校验后再应用）。 */
public class RangeUpdateMessage implements IMessage {

    private long pos;
    private byte xRange;
    private byte zRange;
    private byte yRange;
    private byte speed;

    public RangeUpdateMessage() {
    }

    public RangeUpdateMessage(long pos, RangeValues values) {
        this.pos = pos;
        this.xRange = (byte) values.xRange();
        this.zRange = (byte) values.zRange();
        this.yRange = (byte) values.yRange();
        this.speed = (byte) values.speed();
    }

    public long pos() {
        return pos;
    }

    public RangeValues values() {
        return new RangeValues(xRange, zRange, yRange, speed);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = buf.readLong();
        xRange = buf.readByte();
        zRange = buf.readByte();
        yRange = buf.readByte();
        speed = buf.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        buf.writeByte(xRange);
        buf.writeByte(zRange);
        buf.writeByte(yRange);
        buf.writeByte(speed);
    }

    public static class Handler implements IMessageHandler<RangeUpdateMessage, IMessage> {
        @Override
        public IMessage onMessage(final RangeUpdateMessage message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().field_147369_b;
            // 网络线程不能直接动世界：切回服务端主线程执行
            player.func_71121_q().func_152344_a(new Runnable() {
                @Override
                public void run() {
                    RangeHooks.applyUpdate(player, message);
                }
            });
            return null;
        }
    }
}
