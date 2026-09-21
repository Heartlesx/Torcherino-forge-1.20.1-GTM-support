package gregtech.api.capability.impl;

/**
 * 模拟 GT Lite(gtlitecore) 风格的发电机：Mixin 覆盖了 updateRecipeProgress（标准语义），
 * drawEnergy 仍为标准实现，消耗能量为 false —— 应走“只多发 EU、不加速燃烧”的 boost 路径。
 */
public class FakeGtLiteFuelRecipeLogic extends AbstractRecipeLogic {

    public int customProgressCalls;

    @Override
    public boolean consumesEnergy() {
        return false;
    }

    @Override
    protected void updateRecipeProgress() {
        customProgressCalls++;
        if (canRecipeProgress && drawEnergy(recipeEUt, true)) {
            drawEnergy(recipeEUt, false);
            if (++progressTime > maxProgressTime) {
                completeRecipe();
            }
        }
    }
}
