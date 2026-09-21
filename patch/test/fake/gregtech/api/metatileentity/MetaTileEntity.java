package gregtech.api.metatileentity;

import gregtech.api.capability.impl.AbstractRecipeLogic;

/**
 * 离线测试用桩，签名与真实 GTCE(gregtech-1.12.2-master-#2901) 一致。
 */
public class MetaTileEntity {

    private AbstractRecipeLogic recipeLogic;

    public final AbstractRecipeLogic getRecipeLogic() {
        return recipeLogic;
    }

    public void setRecipeLogic(AbstractRecipeLogic recipeLogic) {
        this.recipeLogic = recipeLogic;
    }
}
