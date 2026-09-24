package com.sci.torcherino;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * 每台火把的范围/速度设置，存成世界存档数据（每个维度一份）。
 *
 * 为什么不复用 TileTorcherino 自己的 NBT：
 * ① 原版类不能新增字段（我们只有“替换已有调用”级别的字节码注入能力）；
 * ② 把新值塞进原有的 Mode 字段会让“装回旧版 jar”时读到越界下标而崩溃；
 * ③ 独立存储天然满足“旧存档零迁移”：没有记录的旧火把完全走原版逻辑。
 */
public final class RangeStore extends WorldSavedData {

    private static final String NAME = "torcherino_ranges";
    private static final String KEY_LIST = "ranges";
    private static final String KEY_POS = "pos";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_SPEED = "speed";

    /** 坐标(BlockPos.toLong) -> 设置；值域已由使用方按上限裁剪。 */
    private final Map<Long, RangeValues> values = new HashMap<Long, RangeValues>();

    /** MapStorage 会用这个构造器反射创建实例。 */
    public RangeStore(String name) {
        super(name);
    }

    /** @return 该火把的设置；没有记录（旧存档火把）时返回 null，调用方回退原版行为。 */
    public static RangeValues get(World world, BlockPos pos) {
        RangeStore store = load(world, false);
        return store == null ? null : store.values.get(Long.valueOf(pos.func_177986_g()));
    }

    public static void set(World world, BlockPos pos, RangeValues values) {
        RangeStore store = load(world, true);
        if (store == null) {
            return;
        }
        store.values.put(Long.valueOf(pos.func_177986_g()), values);
        store.func_76185_a(); // markDirty：让存档在下次保存时落盘
    }

    private static RangeStore load(World world, boolean create) {
        MapStorage storage = world.func_175693_T();
        RangeStore store = (RangeStore) storage.func_75742_a(RangeStore.class, NAME);
        if (store == null && create) {
            store = new RangeStore(NAME);
            storage.func_75745_a(NAME, store);
        }
        return store;
    }

    @Override
    public void func_76184_a(NBTTagCompound compound) {
        values.clear();
        NBTTagList list = compound.func_150295_c(KEY_LIST, 10);
        for (int i = 0; i < list.func_74745_c(); i++) {
            NBTTagCompound tag = list.func_150305_b(i);
            values.put(Long.valueOf(tag.func_74763_f(KEY_POS)), new RangeValues(
                    tag.func_74771_c(KEY_X), tag.func_74771_c(KEY_Z),
                    tag.func_74771_c(KEY_Y), tag.func_74771_c(KEY_SPEED)));
        }
    }

    @Override
    public NBTTagCompound func_189551_b(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<Long, RangeValues> entry : values.entrySet()) {
            RangeValues value = entry.getValue();
            NBTTagCompound tag = new NBTTagCompound();
            tag.func_74772_a(KEY_POS, entry.getKey().longValue());
            tag.func_74774_a(KEY_X, (byte) value.xRange());
            tag.func_74774_a(KEY_Y, (byte) value.yRange());
            tag.func_74774_a(KEY_Z, (byte) value.zRange());
            tag.func_74774_a(KEY_SPEED, (byte) value.speed());
            list.func_74742_a(tag);
        }
        compound.func_74782_a(KEY_LIST, list);
        return compound;
    }
}
