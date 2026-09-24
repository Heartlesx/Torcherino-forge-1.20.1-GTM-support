package gregtech.api.capability.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟 EnergyContainerList（多方块）：自身没有 update()，内部持有多个能量容器；
 * 提供与真实类一致的 getEnergyStored()/changeEnergy(long)。
 */
public class FakeEnergyContainerList {

    private final List<FakeEnergyContainer> energyContainerList;

    public FakeEnergyContainerList(int count) {
        energyContainerList = new ArrayList<FakeEnergyContainer>();
        for (int i = 0; i < count; i++) {
            energyContainerList.add(new FakeEnergyContainer());
        }
    }

    public int size() {
        return energyContainerList.size();
    }

    public int updateCallsOf(int index) {
        return energyContainerList.get(index).updateCalls;
    }

    public long getEnergyStored() {
        long total = 0L;
        for (FakeEnergyContainer container : energyContainerList) {
            total += container.stored;
        }
        return total;
    }

    public long changeEnergy(long amount) {
        if (amount >= 0L) {
            long remaining = amount;
            for (FakeEnergyContainer container : energyContainerList) {
                if (remaining <= 0L) {
                    break;
                }
                remaining -= container.changeEnergy(remaining);
            }
            return amount - remaining;
        }
        long remaining = -amount;
        long removed = 0L;
        for (FakeEnergyContainer container : energyContainerList) {
            if (remaining <= 0L) {
                break;
            }
            long before = container.stored;
            container.changeEnergy(-remaining);
            long took = before - container.stored;
            removed += took;
            remaining -= took;
        }
        return -removed;
    }
}
