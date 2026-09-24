package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.impl.FakeEnergyContainer;
import gregtech.api.capability.impl.FluidDrillLogic;
import gregtech.api.metatileentity.MetaTileEntity;

/**
 * 离线测试用桩，关键成员与真实 GTCE 的 MetaTileEntityFluidDrill 一致：
 * minerLogic(FluidDrillLogic)、energyContainer、isStructureFormed()、drainEnergy(boolean)。
 * 真实类继承 MultiblockWithDisplayBase，这里为了测试桩简单起见直接继承 MetaTileEntity。
 */
public class MetaTileEntityFluidDrill extends MetaTileEntity {

    private final FluidDrillLogic minerLogic = new FluidDrillLogic(this);
    protected Object energyContainer = new FakeEnergyContainer();
    public boolean structureFormed = true;

    public boolean isStructureFormed() {
        return structureFormed;
    }

    public FluidDrillLogic getMinerLogic() {
        return minerLogic;
    }

    public void setEnergyContainer(Object energyContainer) {
        this.energyContainer = energyContainer;
    }

    /** 与真实实现一致：每 tick 扣 GTValues.VA[tier]（MV 为 128）。 */
    public boolean drainEnergy(boolean simulate) {
        FakeEnergyContainer container = (FakeEnergyContainer) energyContainer;
        long energyToDrain = 128L;
        long resultEnergy = container.getEnergyStored() - energyToDrain;
        if (resultEnergy >= 0L && resultEnergy <= container.capacity) {
            if (!simulate) {
                container.changeEnergy(-energyToDrain);
            }
            return true;
        }
        return false;
    }
}
