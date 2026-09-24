package com.sci.torcherino.client;

import com.sci.torcherino.network.KeyHandler;

import net.minecraftforge.common.MinecraftForge;

/**
 * 客户端注入点：替代 ClientProxy.preInit 里的 KeyHandler.preInit()。
 * 先执行原逻辑，再把世界内方框的渲染监听注册到 Forge 事件总线。
 * 这个类是客户端专属的（ClientProxy 只会被客户端加载），服务端不会碰。
 */
public final class RangeClientHooks {

    private RangeClientHooks() {
    }

    public static void init() {
        KeyHandler.preInit();
        MinecraftForge.EVENT_BUS.register(new RangeRenderer());
    }
}
