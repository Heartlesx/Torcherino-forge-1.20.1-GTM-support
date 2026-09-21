package gregtech.api.capability.impl;

/**
 * 模拟 EnergyContainerHandler：每 tick 由 update() 把内部缓冲推给电网。
 * hook 在注入额外能量后调用它，观察是否被驱动。
 */
public class FakeEnergyContainer {

    public int updateCalls;

    public void update() {
        updateCalls++;
    }
}
