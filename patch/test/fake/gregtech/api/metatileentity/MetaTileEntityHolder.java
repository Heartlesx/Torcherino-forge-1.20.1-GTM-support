package gregtech.api.metatileentity;

import net.minecraft.util.ITickable;

/**
 * 离线测试用桩，签名与真实 GTCE(gregtech-1.12.2-master-#2901) 一致。
 */
public class MetaTileEntityHolder implements ITickable {

    public int updateCalls;
    private MetaTileEntity metaTileEntity;

    public MetaTileEntity getMetaTileEntity() {
        return metaTileEntity;
    }

    public void setMetaTileEntity(MetaTileEntity metaTileEntity) {
        this.metaTileEntity = metaTileEntity;
    }

    @Override
    public void func_73660_a() {
        updateCalls++;
    }
}
