package gregtech.api.capability.impl;

/**
 * 未知结构的能量容器：既没有 update() 也没有内部容器列表。
 * hook 应禁用能量提升（避免灌满缓冲卡住进度），而不是报错。
 */
public class FakePlainEnergyContainer {

    public long stored;
    public long capacity = 8192L;
}
