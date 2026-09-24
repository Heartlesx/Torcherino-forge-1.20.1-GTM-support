package gregtech.api.capability.impl;

import gregtech.common.metatileentities.multi.electric.MetaTileEntityFluidDrill;

/**
 * 离线测试用桩，签名与真实 GTCE(gregtech-1.12.2-master-#2901) 的 FluidDrillLogic 一致：
 * 注意它不是 AbstractRecipeLogic（这就是流体钻机走不回标准配方路径的原因）。
 * 每 tick 先模拟检查能量，再实扣能量并推进进度，每 20 tick 归零（即产出一份）。
 */
public class FluidDrillLogic {

    public static final int MAX_PROGRESS = 20;

    private final MetaTileEntityFluidDrill metaTileEntity;
    public int progressTime;
    public int drillingCalls;
    public boolean isActive = true;
    public boolean isWorkingEnabled = true;
    public boolean hasNotEnoughEnergy;

    public FluidDrillLogic(MetaTileEntityFluidDrill metaTileEntity) {
        this.metaTileEntity = metaTileEntity;
    }

    public void performDrilling() {
        drillingCalls++;
        if (!isWorkingEnabled) {
            return;
        }
        if (!metaTileEntity.drainEnergy(true)) {
            if (progressTime >= 2) {
                progressTime = Math.max(1, progressTime - 2);
                hasNotEnoughEnergy = true;
            }
            return;
        }
        metaTileEntity.drainEnergy(false);
        if (++progressTime % MAX_PROGRESS == 0) {
            progressTime = 0;
        }
    }
}
