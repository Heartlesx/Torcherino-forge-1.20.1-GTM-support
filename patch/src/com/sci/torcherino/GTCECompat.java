package com.sci.torcherino;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.util.ITickable;

/**
 * GTCE(GregTech 1.12.2) 兼容层，由 Torcherino 的字节码补丁在额外 tick 循环中调用。
 *
 * 对标准配方机器：不执行整机 tick，只接管“每 tick 的行为”：
 * - 耗能机器（consumesEnergy() == true）：推进配方进度，不消耗 EU（EU 消耗保持 1x）。
 * - 发电机（consumesEnergy() == false）：不推进燃烧进度（燃料消耗保持原速），额外输出一份 EU，
 *   并立刻驱动能量容器向电网推送，避免内部缓冲被灌满导致燃烧进度卡死。
 * - 流体钻机（MetaTileEntityFluidDrill）：其每 tick 逻辑是独立的 FluidDrillLogic，不是
 *   AbstractRecipeLogic，拿不到配方逻辑句柄；因此保留它自己的行为（矿区判定、储罐满暂停、
 *   缺电衰减等），只把额外 tick 多扣的能量退还，使 EU 消耗保持 1x。
 * - 其他方块实体（非 GTCE、锅炉、自定义 drawEnergy 的机器、反射失败等）：维持原版完整 tick。
 *
 * 注意：部分整合包(如 GT Lite 的 gtlitecore)会用 Mixin 覆盖 FuelRecipeLogic.updateRecipeProgress，
 * 因此发电机判定不依赖 updateRecipeProgress 的声明类，只看 consumesEnergy() 与 drawEnergy()。
 *
 * 全部通过反射访问 GTCE 类，GTCE 为可选依赖。诊断日志以 [Torcherino-GTCE] 前缀输出，每种逻辑只打一次。
 */
public final class GTCECompat {

    private static final String ABSTRACT_RECIPE_LOGIC_NAME = "gregtech.api.capability.impl.AbstractRecipeLogic";

    private static final Class<?> META_TILE_ENTITY_HOLDER = loadClass("gregtech.api.metatileentity.MetaTileEntityHolder");
    private static final Class<?> META_TILE_ENTITY = loadClass("gregtech.api.metatileentity.MetaTileEntity");
    private static final Method GET_META_TILE_ENTITY = findMethod(META_TILE_ENTITY_HOLDER, "getMetaTileEntity");
    private static final Method GET_RECIPE_LOGIC = findMethod(META_TILE_ENTITY, "getRecipeLogic");

    /** 流体钻机：每 tick 逻辑是独立的 FluidDrillLogic（非 AbstractRecipeLogic），需要单独接管。 */
    private static final Class<?> FLUID_DRILL = loadClass("gregtech.common.metatileentities.multi.electric.MetaTileEntityFluidDrill");
    private static final Field FLUID_DRILL_LOGIC = findField(FLUID_DRILL, "minerLogic");
    private static final Field FLUID_DRILL_ENERGY = findField(FLUID_DRILL, "energyContainer");
    private static final Method FLUID_DRILL_FORMED = findMethod(FLUID_DRILL, "isStructureFormed");
    private static final Method PERFORM_DRILLING = findMethod(
            loadClass("gregtech.api.capability.impl.FluidDrillLogic"), "performDrilling");

    /** 配方逻辑类的反射句柄缓存；解析失败时存放 FAILED 哨兵，避免反复重试。 */
    private static final ConcurrentHashMap<Class<?>, Object> RECIPE_LOGIC_ACCESS = new ConcurrentHashMap<Class<?>, Object>();
    private static final Object FAILED = new Object();

    /** 能量容器的“立即输出”策略缓存：Method(update) 或 Field(List<IEnergyContainer>)；不支持时存放 UNSUPPORTED。 */
    private static final ConcurrentHashMap<Class<?>, Object> FLUSH_CACHE = new ConcurrentHashMap<Class<?>, Object>();

    /** 能量容器的读写句柄缓存。 */
    private static final ConcurrentHashMap<Class<?>, Object> CONTAINER_OPS = new ConcurrentHashMap<Class<?>, Object>();
    private static final Object UNSUPPORTED = new Object();

    /** 诊断日志去重集合。 */
    private static final Set<String> LOGGED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private GTCECompat() {
    }

    /**
     * 替代原先的 ((ITickable) tile).update() 调用。
     */
    public static void tickTile(ITickable tile) {
        logOnce("init", "GTCE integration: holder=" + (META_TILE_ENTITY_HOLDER != null)
                + " getMetaTileEntity=" + (GET_META_TILE_ENTITY != null)
                + " getRecipeLogic=" + (GET_RECIPE_LOGIC != null));
        if (advanceGtceuRecipe(tile)) {
            return;
        }
        tile.func_73660_a();
    }

    /**
     * @return true 表示目标是 GTCE 机器且已接管本次 tick（不再执行整机 update）。
     */
    private static boolean advanceGtceuRecipe(Object tile) {
        if (META_TILE_ENTITY_HOLDER == null || GET_META_TILE_ENTITY == null || GET_RECIPE_LOGIC == null
                || !META_TILE_ENTITY_HOLDER.isInstance(tile)) {
            return false;
        }
        try {
            Object metaTileEntity = GET_META_TILE_ENTITY.invoke(tile);
            if (metaTileEntity == null) {
                return true; // 机器尚未加载完成：跳过额外 tick，交给世界正常 tick 处理
            }
            if (FLUID_DRILL_LOGIC != null && FLUID_DRILL.isInstance(metaTileEntity)) {
                return accelerateFluidDrill(metaTileEntity);
            }
            Object recipeLogic = GET_RECIPE_LOGIC.invoke(metaTileEntity);
            if (recipeLogic == null) {
                logOnce("no-recipe-logic-" + metaTileEntity.getClass().getName(),
                        "no recipe logic on " + metaTileEntity.getClass().getName());
                return false; // 没有配方逻辑：维持原版行为
            }
            return advance(recipeLogic);
        } catch (Throwable failure) {
            logOnce("error-" + failure.getClass().getName(), "reflection failure: " + failure);
            return false; // 反射失败：维持原版行为
        }
    }

    /**
     * 接管标准配方逻辑的每 tick 行为。
     */
    private static boolean advance(Object recipeLogic) throws Exception {
        Object access = RECIPE_LOGIC_ACCESS.get(recipeLogic.getClass());
        if (access == null) {
            Object created = RecipeLogicAccess.create(recipeLogic.getClass());
            Object raced = RECIPE_LOGIC_ACCESS.putIfAbsent(recipeLogic.getClass(), created == null ? FAILED : created);
            access = raced != null ? raced : (created == null ? FAILED : created);
        }
        if (access == FAILED) {
            logOnce("fallback-access-" + recipeLogic.getClass().getName(),
                    "falling back for " + recipeLogic.getClass().getName() + ": layout not recognized");
            return false; // 结构不认识：维持原版行为
        }
        RecipeLogicAccess logic = (RecipeLogicAccess) access;
        if (!logic.consumesEnergy(recipeLogic)) {
            return boostEnergyOutput(recipeLogic, logic);
        }
        if (!logic.usesStandardProgress) {
            logOnce("fallback-progress-" + recipeLogic.getClass().getName(),
                    "falling back for " + recipeLogic.getClass().getName() + ": custom updateRecipeProgress");
            return false; // 自定义推进逻辑（如研究站）：维持原版完整 tick
        }
        if (!logic.isWorking(recipeLogic)) {
            return true; // 未运行（无配方/停机/缺电）：跳过额外 tick，等正常 tick 处理
        }
        if (!logic.canRecipeProgress(recipeLogic)) {
            return true; // 当前配方不允许推进（如清洁室/结构条件不满足）
        }
        int progress = logic.progressTime.getInt(recipeLogic);
        if (progress <= 0) {
            return true; // 没有配方在跑：跳过额外 tick，等正常 tick 搜索新配方
        }
        int maxProgress = logic.maxProgressTime.getInt(recipeLogic);
        if (maxProgress <= 0) {
            return true; // 没有有效配方
        }
        int next = progress + 1;
        logic.progressTime.setInt(recipeLogic, next);
        if (next > maxProgress) {
            logic.completeRecipe.invoke(recipeLogic);
        }
        logOnce("advance-" + recipeLogic.getClass().getName(),
                "progress acceleration active for " + recipeLogic.getClass().getName());
        return true;
    }

    /**
     * 流体钻机专用路径。
     *
     * 钻机的每 tick 行为（耗电、进度推进、矿区枯竭判定、储罐满暂停、缺电衰减）都在 FluidDrillLogic 里，
     * 与 AbstractRecipeLogic 无关，因此这里保留它自己的行为，只把额外 tick 多扣掉的那份能量退还，
     * 使 EU 消耗保持 1x（正常 tick 的那一份照付）。
     */
    private static boolean accelerateFluidDrill(Object metaTileEntity) {
        try {
            if (PERFORM_DRILLING == null) {
                return false;
            }
            if (FLUID_DRILL_FORMED != null && !((Boolean) FLUID_DRILL_FORMED.invoke(metaTileEntity)).booleanValue()) {
                return false; // 结构未成型：维持原版行为
            }
            Object minerLogic = FLUID_DRILL_LOGIC.get(metaTileEntity);
            if (minerLogic == null) {
                return false;
            }
            Object container = FLUID_DRILL_ENERGY == null ? null : FLUID_DRILL_ENERGY.get(metaTileEntity);
            Object opsRaw = container == null ? UNSUPPORTED : resolveContainerOps(container.getClass());
            if (opsRaw == UNSUPPORTED) {
                // 读不到/改不了能量容器就无法退还额外耗电：宁可不加速，也不让机器退回 ×N 耗电
                logOnce("drill-no-energy-ops-" + metaTileEntity.getClass().getName(),
                        "fluid drill acceleration disabled for " + metaTileEntity.getClass().getName()
                                + ": energy container not readable");
                return true;
            }
            ContainerOps ops = (ContainerOps) opsRaw;
            long before = ops.getEnergyStored(container);
            PERFORM_DRILLING.invoke(minerLogic);
            long after = ops.getEnergyStored(container);
            if (after < before) {
                ops.changeEnergy(container, before - after);
            }
            logOnce("drill-" + metaTileEntity.getClass().getName(),
                    "fluid drill acceleration active for " + metaTileEntity.getClass().getName()
                            + " (energy cost kept at 1x)");
            return true;
        } catch (Throwable failure) {
            logOnce("drill-error-" + failure.getClass().getName(), "fluid drill failure: " + failure);
            return false; // 反射失败：维持原版行为
        }
    }

    /**
     * 发电机：不推进燃烧进度（燃料消耗保持原速），额外输出一份 EU 并立即送往电网。
     *
     * 关键点：只注入而不推送会把内部缓冲灌满，燃烧进度会因此停滞，所以注入后必须驱动容器输出。
     */
    private static boolean boostEnergyOutput(Object recipeLogic, RecipeLogicAccess logic) throws Exception {
        if (!logic.standardDrawEnergy) {
            logOnce("fallback-draw-" + recipeLogic.getClass().getName(),
                    "falling back for " + recipeLogic.getClass().getName() + ": custom drawEnergy");
            return false; // 自定义能量处理（如锅炉禁止 drawEnergy）：维持原版完整 tick
        }
        if (!logic.isWorking(recipeLogic)) {
            return true; // 未在运行：跳过额外 tick
        }
        long recipeEUt = logic.recipeEUt.getLong(recipeLogic);
        if (recipeEUt <= 0L) {
            return true; // 没有有效输出
        }
        Object container = logic.getEnergyContainer(recipeLogic);
        if (!flushSupported(container)) {
            logOnce("no-flush-" + (container == null ? "null" : container.getClass().getName()),
                    "energy boost disabled (cannot flush) for container " + (container == null ? "null" : container.getClass().getName()));
            return true; // 无法立即输出：不注入，避免缓冲被灌满导致进度停滞
        }
        if (logic.drawEnergySimulate(recipeLogic, recipeEUt)) {
            logic.drawEnergy.invoke(recipeLogic, Long.valueOf(recipeEUt), Boolean.FALSE);
            flushEnergy(container, 0);
        } else {
            // 缓冲已满：先把存量推给电网，给燃烧进度让路
            flushEnergy(container, 0);
        }
        logOnce("boost-" + recipeLogic.getClass().getName(),
                "energy boost active for " + recipeLogic.getClass().getName());
        return true;
    }

    /** 检查容器能否被立即驱动输出（update() 方法或内部容器列表）。 */
    private static boolean flushSupported(Object container) {
        if (container == null) {
            return false;
        }
        return resolveFlushStrategy(container.getClass()) != UNSUPPORTED;
    }

    private static Object resolveFlushStrategy(Class<?> containerClass) {
        Object cached = FLUSH_CACHE.get(containerClass);
        if (cached != null) {
            return cached;
        }
        Object created = createFlushStrategy(containerClass);
        Object raced = FLUSH_CACHE.putIfAbsent(containerClass, created == null ? UNSUPPORTED : created);
        return raced != null ? raced : (created == null ? UNSUPPORTED : created);
    }

    private static Object createFlushStrategy(Class<?> containerClass) {
        // 单方块机器：EnergyContainerHandler.update()
        Method update = findMethod(containerClass, "update");
        if (update != null) {
            return update;
        }
        // 多方块机器：EnergyContainerList 内含多个能量容器（如动力仓），逐个驱动
        for (Class<?> current = containerClass; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (List.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                    } catch (Throwable failure) {
                        return null;
                    }
                    return field;
                }
            }
        }
        return null;
    }

    private static void flushEnergy(Object container, int depth) {
        if (container == null || depth > 1) {
            return;
        }
        Object strategy = resolveFlushStrategy(container.getClass());
        if (strategy == UNSUPPORTED) {
            return;
        }
        try {
            if (strategy instanceof Method) {
                ((Method) strategy).invoke(container);
            } else if (strategy instanceof Field) {
                Object value = ((Field) strategy).get(container);
                if (value instanceof List) {
                    for (Object element : (List<?>) value) {
                        flushEnergy(element, depth + 1);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 输出失败不影响 tick
        }
    }

    private static Object resolveContainerOps(Class<?> containerClass) {
        Object cached = CONTAINER_OPS.get(containerClass);
        if (cached != null) {
            return cached;
        }
        Object created = createContainerOps(containerClass);
        Object raced = CONTAINER_OPS.putIfAbsent(containerClass, created == null ? UNSUPPORTED : created);
        return raced != null ? raced : (created == null ? UNSUPPORTED : created);
    }

    private static Object createContainerOps(Class<?> containerClass) {
        Method getEnergyStored = findMethod(containerClass, "getEnergyStored");
        Method changeEnergy = findMethod(containerClass, "changeEnergy", long.class);
        if (getEnergyStored == null || changeEnergy == null) {
            return null;
        }
        return new ContainerOps(getEnergyStored, changeEnergy);
    }

    private static final class ContainerOps {
        final Method getEnergyStored;
        final Method changeEnergy;

        ContainerOps(Method getEnergyStored, Method changeEnergy) {
            this.getEnergyStored = getEnergyStored;
            this.changeEnergy = changeEnergy;
        }

        long getEnergyStored(Object container) throws Exception {
            return ((Long) getEnergyStored.invoke(container)).longValue();
        }

        long changeEnergy(Object container, long amount) throws Exception {
            return ((Long) changeEnergy.invoke(container, Long.valueOf(amount))).longValue();
        }
    }

    private static void logOnce(String key, String message) {
        try {
            if (LOGGED.add(key)) {
                System.out.println("[Torcherino-GTCE] " + message);
            }
        } catch (Throwable ignored) {
            // never let diagnostics break ticking
        }
    }

    private static final class RecipeLogicAccess {
        final Method isWorking;
        final Method consumesEnergy;
        final Method completeRecipe;
        final Method drawEnergy;
        final Method getEnergyContainer;
        final Field progressTime;
        final Field maxProgressTime;
        final Field canRecipeProgress;
        final Field recipeEUt;
        /** updateRecipeProgress() 未被具体类覆盖时，才是标准的每 tick 推进。 */
        final boolean usesStandardProgress;
        /** drawEnergy() 未被具体类覆盖时，才允许由本 hook 额外输出能量。 */
        final boolean standardDrawEnergy;

        private RecipeLogicAccess(Method isWorking, Method consumesEnergy, Method completeRecipe, Method drawEnergy,
                                  Method getEnergyContainer, Field progressTime, Field maxProgressTime,
                                  Field canRecipeProgress, Field recipeEUt,
                                  boolean usesStandardProgress, boolean standardDrawEnergy) {
            this.isWorking = isWorking;
            this.consumesEnergy = consumesEnergy;
            this.completeRecipe = completeRecipe;
            this.drawEnergy = drawEnergy;
            this.getEnergyContainer = getEnergyContainer;
            this.progressTime = progressTime;
            this.maxProgressTime = maxProgressTime;
            this.canRecipeProgress = canRecipeProgress;
            this.recipeEUt = recipeEUt;
            this.usesStandardProgress = usesStandardProgress;
            this.standardDrawEnergy = standardDrawEnergy;
        }

        static Object create(Class<?> logicClass) {
            try {
                Method isWorking = findMethod(logicClass, "isWorking");
                Method consumesEnergy = findMethod(logicClass, "consumesEnergy");
                Method completeRecipe = findMethod(logicClass, "completeRecipe");
                Method updateRecipeProgress = findMethod(logicClass, "updateRecipeProgress");
                Method drawEnergy = findMethod(logicClass, "drawEnergy", long.class, boolean.class);
                Method getEnergyContainer = findMethod(logicClass, "getEnergyContainer");
                Field progressTime = findField(logicClass, "progressTime");
                Field maxProgressTime = findField(logicClass, "maxProgressTime");
                Field recipeEUt = findField(logicClass, "recipeEUt");
                if (isWorking == null || consumesEnergy == null || completeRecipe == null || updateRecipeProgress == null
                        || drawEnergy == null || getEnergyContainer == null
                        || progressTime == null || maxProgressTime == null || recipeEUt == null) {
                    return null;
                }
                boolean usesStandardProgress = ABSTRACT_RECIPE_LOGIC_NAME.equals(updateRecipeProgress.getDeclaringClass().getName());
                boolean standardDrawEnergy = ABSTRACT_RECIPE_LOGIC_NAME.equals(drawEnergy.getDeclaringClass().getName());
                // canRecipeProgress 为可选字段，缺失时不做该检查
                Field canRecipeProgress = findField(logicClass, "canRecipeProgress");
                return new RecipeLogicAccess(isWorking, consumesEnergy, completeRecipe, drawEnergy, getEnergyContainer,
                        progressTime, maxProgressTime, canRecipeProgress, recipeEUt,
                        usesStandardProgress, standardDrawEnergy);
            } catch (Throwable failure) {
                return null;
            }
        }

        boolean isWorking(Object logic) throws Exception {
            return ((Boolean) isWorking.invoke(logic)).booleanValue();
        }

        boolean consumesEnergy(Object logic) throws Exception {
            return ((Boolean) consumesEnergy.invoke(logic)).booleanValue();
        }

        boolean canRecipeProgress(Object logic) throws Exception {
            return canRecipeProgress == null || canRecipeProgress.getBoolean(logic);
        }

        boolean drawEnergySimulate(Object logic, long recipeEUt) throws Exception {
            return ((Boolean) drawEnergy.invoke(logic, Long.valueOf(recipeEUt), Boolean.TRUE)).booleanValue();
        }

        Object getEnergyContainer(Object logic) {
            try {
                return getEnergyContainer.invoke(logic);
            } catch (Throwable failure) {
                return null;
            }
        }
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable missing) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        if (type == null) {
            return null;
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // continue up the hierarchy
            } catch (Throwable failure) {
                return null;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // continue up the hierarchy
            } catch (Throwable failure) {
                return null;
            }
        }
        return null;
    }
}
