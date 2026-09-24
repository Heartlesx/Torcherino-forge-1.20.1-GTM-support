package gregtech.api.capability.impl;

/**
 * 模拟 EnergyContainerHandler：每 tick 由 update() 把内部缓冲推给电网，
 * 并支持读取/改写能量（用于发电机能量输出与能量退还的测试）。
 */
public class FakeEnergyContainer {

    public int updateCalls;
    public long stored = 4096L;
    public long capacity = 4096L;

    public void update() {
        updateCalls++;
    }

    public long getEnergyStored() {
        return stored;
    }

    public long changeEnergy(long amount) {
        long next = stored + amount;
        if (next > capacity) {
            next = capacity;
        }
        if (next < 0L) {
            next = 0L;
        }
        long delta = next - stored;
        stored = next;
        return delta;
    }
}
