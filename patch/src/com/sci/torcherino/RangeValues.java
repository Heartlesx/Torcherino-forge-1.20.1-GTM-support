package com.sci.torcherino;

/**
 * 火把的范围与速度设置（纯数据，不依赖 Minecraft 类，便于离线测试）。
 *
 * 范围用“对称奇数”模型：某轴边长 = 2 * range + 1，以火把自身为中心。
 * speed 沿用原版档位 0..4（0 = 停止），实际倍率由火把变体的 speed(int) 决定（×1/×9/×81）。
 */
public final class RangeValues {

    /** 原版硬上限：XZ 半宽 4（9x3x9）、Y 半宽 1（3 层）、速度档 4。 */
    public static final int LIMIT_XZ = 4;
    public static final int LIMIT_Y = 1;
    public static final int LIMIT_SPEED = 4;

    private final int xRange;
    private final int zRange;
    private final int yRange;
    private final int speed;

    public RangeValues(int xRange, int zRange, int yRange, int speed) {
        this.xRange = xRange;
        this.zRange = zRange;
        this.yRange = yRange;
        this.speed = speed;
    }

    /**
     * 旧存档火把（只有 mode/speed）的等价设置。
     * mode==0 表示停止，此时范围不参与运算，界面初值取原版最大标准形状 9x3x9。
     */
    public static RangeValues fromLegacy(int mode, int speed) {
        int range = mode <= 0 ? LIMIT_XZ : Math.min(mode, LIMIT_XZ);
        return new RangeValues(range, range, LIMIT_Y, clamp(speed, 0, LIMIT_SPEED));
    }

    /** 按上限裁剪（服务端校验与界面取值都用它）。 */
    public RangeValues clamped(int limitXZ, int limitY) {
        return new RangeValues(clamp(xRange, 0, limitXZ), clamp(zRange, 0, limitXZ),
                clamp(yRange, 0, limitY), clamp(speed, 0, LIMIT_SPEED));
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public int xRange() {
        return xRange;
    }

    public int zRange() {
        return zRange;
    }

    public int yRange() {
        return yRange;
    }

    public int speed() {
        return speed;
    }

    public int xSize() {
        return xRange * 2 + 1;
    }

    public int ySize() {
        return yRange * 2 + 1;
    }

    public int zSize() {
        return zRange * 2 + 1;
    }

    /** 形如 "9x3x5"（X×Y×Z）。 */
    public String shape() {
        return xSize() + "x" + ySize() + "x" + zSize();
    }

    public boolean active() {
        return speed > 0;
    }

    /** 形如 "Area: 9x3x5 | Speed: 3600%"；speedPercent 由火把变体折算后传入。 */
    public String describe(int speedPercent) {
        return "Area: " + shape() + " | Speed: " + speedPercent + "%";
    }

    /** 以火把坐标为中心的扫描边界（含端点）。 */
    public Bounds boundsAt(int x, int y, int z) {
        return new Bounds(x - xRange, y - yRange, z - zRange, x + xRange, y + yRange, z + zRange);
    }

    /** 作用域边界，对应 TileTorcherino 里的 xMin/yMin/zMin/xMax/yMax/zMax。 */
    public static final class Bounds {
        public final int xMin;
        public final int yMin;
        public final int zMin;
        public final int xMax;
        public final int yMax;
        public final int zMax;

        Bounds(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax) {
            this.xMin = xMin;
            this.yMin = yMin;
            this.zMin = zMin;
            this.xMax = xMax;
            this.yMax = yMax;
            this.zMax = zMax;
        }
    }
}
