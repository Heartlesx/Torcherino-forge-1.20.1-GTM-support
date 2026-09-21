package gregtech.api.capability.impl;

/** 模拟锅炉：产出型且自定义 updateRecipeProgress()，应走完整 tick，且 hook 不得调用 drawEnergy。 */
public class FakeCustomProgressRecipeLogic extends AbstractRecipeLogic {

    public int customProgressCalls;
    public int drawEnergyCalls;

    @Override
    public boolean consumesEnergy() {
        return false;
    }

    @Override
    protected void updateRecipeProgress() {
        customProgressCalls++;
    }

    @Override
    protected boolean drawEnergy(long recipeEUt, boolean simulate) {
        drawEnergyCalls++;
        return false;
    }
}
