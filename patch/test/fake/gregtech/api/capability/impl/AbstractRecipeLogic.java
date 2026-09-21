package gregtech.api.capability.impl;

/**
 * 离线测试用桩，字段与方法签名/修饰符与真实 GTCE(gregtech-1.12.2-master-#2901) 一致。
 */
public class AbstractRecipeLogic {

    protected int progressTime;
    protected int maxProgressTime;
    protected boolean canRecipeProgress = true;
    protected boolean isActive = true;
    protected boolean workingEnabled = true;
    protected boolean hasNotEnoughEnergy = false;
    protected long recipeEUt;

    /** 测试观测：配方完成次数。 */
    public int completed;
    /** 测试观测：能量输出次数与金额。 */
    public int energyOutputs;
    public long lastEnergyOutput;
    /** 测试开关：模拟“容器已满、无法再注入”。 */
    public boolean rejectSimulate;
    /** 测试用能量容器（真实机器由 RecipeLogicEnergy 提供）。 */
    public Object energyContainer = new FakeEnergyContainer();

    public boolean isWorking() {
        return isActive && !hasNotEnoughEnergy && workingEnabled;
    }

    public boolean consumesEnergy() {
        return true;
    }

    protected void updateRecipeProgress() {
        if (++progressTime > maxProgressTime) {
            completeRecipe();
        }
    }

    protected boolean drawEnergy(long recipeEUt, boolean simulate) {
        if (simulate) {
            return !rejectSimulate;
        }
        energyOutputs++;
        lastEnergyOutput = recipeEUt;
        return true;
    }

    protected Object getEnergyContainer() {
        return energyContainer;
    }

    public int getProgress() {
        return progressTime;
    }

    public int getMaxProgress() {
        return maxProgressTime;
    }

    public void setup(int progress, int maxProgress) {
        this.progressTime = progress;
        this.maxProgressTime = maxProgress;
    }

    public void setRecipeEUt(long recipeEUt) {
        this.recipeEUt = recipeEUt;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setCanRecipeProgress(boolean canRecipeProgress) {
        this.canRecipeProgress = canRecipeProgress;
    }

    protected void completeRecipe() {
        this.progressTime = 0;
        this.maxProgressTime = 0;
        this.completed++;
    }
}
