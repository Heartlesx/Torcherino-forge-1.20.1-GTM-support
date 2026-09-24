import com.sci.torcherino.RangeValues;

/**
 * RangeValues 的离线测试：旧存档映射、上限裁剪、尺寸与文本。
 * 这些用例不依赖 Minecraft 类，直接从构建产物 jar 加载 RangeValues。
 */
public class RangeValuesTest {

    private static int checks;

    public static void main(String[] args) {
        testLegacyMappingKeepsOriginalShape();
        testLegacyStoppedUsesStandardShape();
        testClampRespectsLimits();
        testSizesAndShapeText();
        testDescribeText();
        testBoundsMatchLegacyForOldTorches();
        testBoundsAreIndependentPerAxis();
        System.out.println("ALL RANGE TESTS PASSED (" + checks + " checks)");
    }

    private static void testLegacyMappingKeepsOriginalShape() {
        check("legacy mode 4 -> 9x3x9", "9x3x9".equals(RangeValues.fromLegacy(4, 4).shape()));
        check("legacy mode 2 -> 5x3x5", "5x3x5".equals(RangeValues.fromLegacy(2, 3).shape()));
        check("legacy mode 1 speed 0 -> stopped", !RangeValues.fromLegacy(1, 0).active());
        check("legacy mode 1 speed 4 -> active", RangeValues.fromLegacy(1, 4).active());
    }

    private static void testLegacyStoppedUsesStandardShape() {
        RangeValues stopped = RangeValues.fromLegacy(0, 0);
        check("legacy stopped -> standard 9x3x9", "9x3x9".equals(stopped.shape()));
        check("legacy stopped -> not active", !stopped.active());
    }

    private static void testClampRespectsLimits() {
        RangeValues high = new RangeValues(9, 9, 5, 9).clamped(RangeValues.LIMIT_XZ, RangeValues.LIMIT_Y);
        check("clamp high -> xz 4", high.xRange() == 4 && high.zRange() == 4);
        check("clamp high -> y 1", high.yRange() == 1);
        check("clamp high -> speed 4", high.speed() == 4);

        RangeValues low = new RangeValues(-2, 1, -5, -3).clamped(RangeValues.LIMIT_XZ, RangeValues.LIMIT_Y);
        check("clamp low -> 1x1x3", "1x1x3".equals(low.shape()));
        check("clamp low -> speed 0", low.speed() == 0);
    }

    private static void testSizesAndShapeText() {
        RangeValues values = new RangeValues(1, 4, 0, 1);
        check("sizes: x 3", values.xSize() == 3);
        check("sizes: y 1", values.ySize() == 1);
        check("sizes: z 9", values.zSize() == 9);
        check("shape text 3x1x9", "3x1x9".equals(values.shape()));
    }

    private static void testDescribeText() {
        RangeValues values = new RangeValues(4, 2, 1, 4);
        check("describe text", "Area: 9x3x5 | Speed: 3600%".equals(values.describe(3600)));
        check("describe text (stopped)", "Area: 9x3x5 | Speed: 0%".equals(values.describe(0)));
    }

    private static void testBoundsMatchLegacyForOldTorches() {
        // 旧存档火把（mode 3）在坐标 (10,64,20) 的边界必须与原版 x±mode / y±1 完全一致
        RangeValues.Bounds bounds = RangeValues.fromLegacy(3, 4).boundsAt(10, 64, 20);
        check("legacy bounds xMin", bounds.xMin == 7);
        check("legacy bounds xMax", bounds.xMax == 13);
        check("legacy bounds yMin", bounds.yMin == 63);
        check("legacy bounds yMax", bounds.yMax == 65);
        check("legacy bounds zMin", bounds.zMin == 17);
        check("legacy bounds zMax", bounds.zMax == 23);
    }

    private static void testBoundsAreIndependentPerAxis() {
        RangeValues.Bounds bounds = new RangeValues(1, 4, 0, 1).boundsAt(10, 64, -3);
        check("bounds xMin", bounds.xMin == 9);
        check("bounds xMax", bounds.xMax == 11);
        check("bounds yMin == yMax (1 layer)", bounds.yMin == 64 && bounds.yMax == 64);
        check("bounds zMin", bounds.zMin == -7);
        check("bounds zMax", bounds.zMax == 1);
    }

    private static void check(String label, boolean condition) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + label);
        }
        System.out.println("ok - " + label);
    }
}
