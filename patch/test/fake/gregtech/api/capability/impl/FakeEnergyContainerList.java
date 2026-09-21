package gregtech.api.capability.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟 EnergyContainerList（多方块）：自身没有 update()，内部持有多个能量容器。
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
}
