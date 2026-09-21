import com.sci.torcherino.GTCECompat;
import gregtech.api.capability.impl.AbstractRecipeLogic;
import gregtech.api.capability.impl.FakeCustomProgressRecipeLogic;
import gregtech.api.capability.impl.FakeEnergyContainer;
import gregtech.api.capability.impl.FakeEnergyContainerList;
import gregtech.api.capability.impl.FakeFuelRecipeLogic;
import gregtech.api.capability.impl.FakeGtLiteFuelRecipeLogic;
import gregtech.api.capability.impl.FakePlainEnergyContainer;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import net.minecraft.util.ITickable;

/**
 * GTCECompat 的离线行为测试：用与真实 GTCE 同签名的桩类验证
 * “耗能机器只推进进度、发电机不加速燃烧只多发 EU、其他 tile 保持原行为”。
 */
public class HookTest {

    private static int checks;

    public static void main(String[] args) {
        testRunningRecipeAdvancesWithoutMachineUpdate();
        testCompletionAtMaxProgress();
        testPlainTileFallsBackToUpdate();
        testMachineNotWorkingSkipsMachineUpdate();
        testCannotProgressSkipsAdvance();
        testNoRecipeLogicFallsBackToMachineUpdate();
        testUnloadedMetaTileEntitySkipsMachineUpdate();
        testGeneratorBoostsEnergyWithoutBurningFuel();
        testGeneratorNotWorkingSkipsBoost();
        testGtLiteStyleGeneratorStillBoosts();
        testFullBufferStillFlushesWithoutInjecting();
        testMultiblockStyleContainerStillBoosts();
        testUnknownContainerDisablesBoost();
        testBoilerStyleFallsBackToMachineUpdate();
        System.out.println("ALL TESTS PASSED (" + checks + " checks)");
    }

    private static void testRunningRecipeAdvancesWithoutMachineUpdate() {
        Fixture f = new Fixture(1, 10);
        GTCECompat.tickTile(f.holder);
        check("running: machine update skipped", f.holder.updateCalls == 0);
        check("running: progress advanced by 1", f.logic.getProgress() == 2);
        check("running: not completed yet", f.logic.completed == 0);
        check("running: no energy drained or produced", f.logic.energyOutputs == 0);
    }

    private static void testCompletionAtMaxProgress() {
        Fixture f = new Fixture(10, 10);
        GTCECompat.tickTile(f.holder);
        check("complete: machine update skipped", f.holder.updateCalls == 0);
        check("complete: recipe finished", f.logic.completed == 1);
        check("complete: progress reset", f.logic.getProgress() == 0);
    }

    private static void testPlainTileFallsBackToUpdate() {
        PlainTile tile = new PlainTile();
        GTCECompat.tickTile(tile);
        check("plain tile: update called once", tile.updateCalls == 1);
    }

    private static void testMachineNotWorkingSkipsMachineUpdate() {
        Fixture f = new Fixture(3, 10);
        f.logic.setActive(false);
        GTCECompat.tickTile(f.holder);
        check("not working: machine update skipped", f.holder.updateCalls == 0);
        check("not working: progress untouched", f.logic.getProgress() == 3);
    }

    private static void testCannotProgressSkipsAdvance() {
        Fixture f = new Fixture(3, 10);
        f.logic.setCanRecipeProgress(false);
        GTCECompat.tickTile(f.holder);
        check("cannot progress: machine update skipped", f.holder.updateCalls == 0);
        check("cannot progress: progress untouched", f.logic.getProgress() == 3);
    }

    private static void testNoRecipeLogicFallsBackToMachineUpdate() {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(new MetaTileEntity());
        GTCECompat.tickTile(holder);
        check("no recipe logic: falls back to machine update", holder.updateCalls == 1);
    }

    private static void testUnloadedMetaTileEntitySkipsMachineUpdate() {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        GTCECompat.tickTile(holder);
        check("unloaded machine: machine update skipped", holder.updateCalls == 0);
    }

    private static void testGeneratorBoostsEnergyWithoutBurningFuel() {
        FakeFuelRecipeLogic logic = new FakeFuelRecipeLogic();
        Fixture f = new Fixture(logic, 5, 100);
        logic.setRecipeEUt(32L);
        GTCECompat.tickTile(f.holder);
        check("generator: machine update skipped", f.holder.updateCalls == 0);
        check("generator: burn progress untouched", f.logic.getProgress() == 5);
        check("generator: one extra energy output", logic.energyOutputs == 1);
        check("generator: output amount equals recipe EUt", logic.lastEnergyOutput == 32L);
        check("generator: container flushed to grid", ((FakeEnergyContainer) logic.energyContainer).updateCalls == 1);
    }

    private static void testGeneratorNotWorkingSkipsBoost() {
        FakeFuelRecipeLogic logic = new FakeFuelRecipeLogic();
        Fixture f = new Fixture(logic, 0, 0);
        logic.setRecipeEUt(32L);
        logic.setActive(false);
        GTCECompat.tickTile(f.holder);
        check("idle generator: machine update skipped", f.holder.updateCalls == 0);
        check("idle generator: no energy output", logic.energyOutputs == 0);
    }

    private static void testGtLiteStyleGeneratorStillBoosts() {
        FakeGtLiteFuelRecipeLogic logic = new FakeGtLiteFuelRecipeLogic();
        Fixture f = new Fixture(logic, 5, 100);
        logic.setRecipeEUt(32L);
        GTCECompat.tickTile(f.holder);
        check("gtlite generator: machine update skipped", f.holder.updateCalls == 0);
        check("gtlite generator: burn progress untouched", f.logic.getProgress() == 5);
        check("gtlite generator: one extra energy output", logic.energyOutputs == 1);
    }

    private static void testFullBufferStillFlushesWithoutInjecting() {
        FakeFuelRecipeLogic logic = new FakeFuelRecipeLogic();
        Fixture f = new Fixture(logic, 5, 100);
        logic.setRecipeEUt(32L);
        logic.rejectSimulate = true; // 容器已满：不能再注入
        GTCECompat.tickTile(f.holder);
        check("full buffer: machine update skipped", f.holder.updateCalls == 0);
        check("full buffer: no injection", logic.energyOutputs == 0);
        check("full buffer: container still flushed", ((FakeEnergyContainer) logic.energyContainer).updateCalls == 1);
    }

    private static void testMultiblockStyleContainerStillBoosts() {
        FakeFuelRecipeLogic logic = new FakeFuelRecipeLogic();
        Fixture f = new Fixture(logic, 5, 100);
        logic.setRecipeEUt(32L);
        FakeEnergyContainerList container = new FakeEnergyContainerList(2);
        logic.energyContainer = container;
        GTCECompat.tickTile(f.holder);
        check("multiblock container: machine update skipped", f.holder.updateCalls == 0);
        check("multiblock container: one extra energy output", logic.energyOutputs == 1);
        check("multiblock container: inner containers flushed",
                container.size() == 2 && container.updateCallsOf(0) == 1 && container.updateCallsOf(1) == 1);
    }

    private static void testUnknownContainerDisablesBoost() {
        FakeFuelRecipeLogic logic = new FakeFuelRecipeLogic();
        Fixture f = new Fixture(logic, 5, 100);
        logic.setRecipeEUt(32L);
        logic.energyContainer = new FakePlainEnergyContainer();
        GTCECompat.tickTile(f.holder);
        check("unknown container: machine update skipped", f.holder.updateCalls == 0);
        check("unknown container: no injection", logic.energyOutputs == 0);
    }

    private static void testBoilerStyleFallsBackToMachineUpdate() {
        Fixture f = new Fixture(new FakeCustomProgressRecipeLogic(), 1, 10);
        FakeCustomProgressRecipeLogic logic = (FakeCustomProgressRecipeLogic) f.logic;
        GTCECompat.tickTile(f.holder);
        check("boiler style: falls back to machine update", f.holder.updateCalls == 1);
        check("boiler style: progress untouched", f.logic.getProgress() == 1);
        check("boiler style: hook did not touch drawEnergy", logic.drawEnergyCalls == 0);
    }

    private static void check(String label, boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + label);
        }
        System.out.println("ok - " + label);
    }

    private static final class Fixture {
        final MetaTileEntityHolder holder = new MetaTileEntityHolder();
        final MetaTileEntity machine = new MetaTileEntity();
        final AbstractRecipeLogic logic;

        Fixture(int progress, int maxProgress) {
            this(new AbstractRecipeLogic(), progress, maxProgress);
        }

        Fixture(AbstractRecipeLogic logic, int progress, int maxProgress) {
            this.logic = logic;
            logic.setup(progress, maxProgress);
            machine.setRecipeLogic(logic);
            holder.setMetaTileEntity(machine);
        }
    }

    private static final class PlainTile implements ITickable {
        int updateCalls;

        @Override
        public void func_73660_a() {
            updateCalls++;
        }
    }
}
