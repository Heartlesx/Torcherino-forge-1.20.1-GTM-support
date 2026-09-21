package gregtech.api.capability.impl;

/** 模拟发电机等每 tick 产出型机器：consumesEnergy() == false，应走完整 tick。 */
public class FakeFuelRecipeLogic extends AbstractRecipeLogic {

    @Override
    public boolean consumesEnergy() {
        return false;
    }
}
