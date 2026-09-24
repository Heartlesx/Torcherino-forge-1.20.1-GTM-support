package com.sci.torcherino.client;

import org.lwjgl.opengl.GL11;

import com.sci.torcherino.RangeValues;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 世界内范围方框（二期）：绿色线框 + 最底层淡绿半透明平面，穿透方块可见。
 * 只画"刚操作过的那台"火把，5 秒后自动消失（数据由 RangeClient 从服务端的 SHOW/REFRESH 包记录）。
 */
public final class RangeRenderer {

    private static final float RED = 0.33F;
    private static final float GREEN = 1.0F;
    private static final float BLUE = 0.33F;
    private static final float LINE_ALPHA = 1.0F;
    private static final float FACE_ALPHA = 0.16F;
    private static final float LINE_WIDTH = 2.0F;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        RangeValues values = RangeClient.displayValues();
        long packed = RangeClient.displayPos();
        if (values == null || packed == Long.MIN_VALUE) {
            return;
        }
        RangeValues.Bounds bounds = values.boundsAt(unpackX(packed), unpackY(packed), unpackZ(packed));
        Minecraft minecraft = Minecraft.func_71410_x();
        Entity view = minecraft == null ? null : minecraft.func_175606_aa();
        if (view == null) {
            return;
        }
        float partial = event.getPartialTicks();
        double camX = view.field_70142_S + (view.field_70165_t - view.field_70142_S) * partial;
        double camY = view.field_70137_T + (view.field_70163_u - view.field_70137_T) * partial;
        double camZ = view.field_70136_U + (view.field_70161_v - view.field_70136_U) * partial;

        // 方框贴着方块外沿：min 取格子的西/下/北面，max 取格子的东/上/南面
        double x0 = bounds.xMin;
        double y0 = bounds.yMin;
        double z0 = bounds.zMin;
        double x1 = bounds.xMax + 1.0D;
        double y1 = bounds.yMax + 1.0D;
        double z1 = bounds.zMax + 1.0D;

        GlStateManager.func_179094_E();
        GlStateManager.func_179137_b(-camX, -camY, -camZ);
        GlStateManager.func_179097_i(); // 关深度测试：穿透方块可见
        GlStateManager.func_179090_x();
        GlStateManager.func_179147_l();
        GlStateManager.func_179120_a(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        GL11.glLineWidth(LINE_WIDTH);
        GlStateManager.func_179131_c(RED, GREEN, BLUE, LINE_ALPHA);
        GL11.glBegin(GL11.GL_LINES);
        edge(x0, y0, z0, x1, y0, z0);
        edge(x1, y0, z0, x1, y0, z1);
        edge(x1, y0, z1, x0, y0, z1);
        edge(x0, y0, z1, x0, y0, z0);
        edge(x0, y1, z0, x1, y1, z0);
        edge(x1, y1, z0, x1, y1, z1);
        edge(x1, y1, z1, x0, y1, z1);
        edge(x0, y1, z1, x0, y1, z0);
        edge(x0, y0, z0, x0, y1, z0);
        edge(x1, y0, z0, x1, y1, z0);
        edge(x1, y0, z1, x1, y1, z1);
        edge(x0, y0, z1, x0, y1, z1);
        GL11.glEnd();

        // 最底层那一层的淡绿平面
        GlStateManager.func_179131_c(RED, GREEN, BLUE, FACE_ALPHA);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x0, y0, z0);
        GL11.glVertex3d(x1, y0, z0);
        GL11.glVertex3d(x1, y0, z1);
        GL11.glVertex3d(x0, y0, z1);
        GL11.glEnd();

        GlStateManager.func_179098_w();
        GlStateManager.func_179126_j();
        GlStateManager.func_179084_k();
        GlStateManager.func_179121_F();
    }

    private static void edge(double ax, double ay, double az, double bx, double by, double bz) {
        GL11.glVertex3d(ax, ay, az);
        GL11.glVertex3d(bx, by, bz);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    private static int unpackY(long packed) {
        return (int) ((packed >> 26) & 0xFFFL);
    }

    private static int unpackZ(long packed) {
        return (int) (packed << 38 >> 38);
    }
}
