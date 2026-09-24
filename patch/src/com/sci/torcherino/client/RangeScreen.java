package com.sci.torcherino.client;

import java.io.IOException;

import com.sci.torcherino.RangeValues;
import com.sci.torcherino.Torcherino;
import com.sci.torcherino.network.RangeUpdateMessage;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * 火把设置界面（原版菜单风格）：原版背景（drawDefaultBackground）+ 原版灰按钮（GuiButton）。
 *
 * 每行一组 -/+，中间是该轴的边长（偶数限制不了，所以恒为奇数）；
 * 底部显示完整形状与折算后的速度百分比；Done 按钮或 Esc 关闭。
 */
public class RangeScreen extends GuiScreen {

    /** 内容整体高度（标题 + 4 行 + Done + 两行页脚），用来把整块居中。 */
    private static final int CONTENT_HEIGHT = 178;
    private static final int TITLE_Y = 0;
    private static final int ROWS_TOP = 22;
    private static final int ROW_HEIGHT = 24;
    private static final int DONE_TOP = 126;
    private static final int FOOTER_TOP = 154;
    private static final int BUTTON_SIZE = 20;
    private static final int LABEL_X = -110;
    private static final int MINUS_X = -40;
    private static final int PLUS_X = 20;

    private static final int ID_DONE = 100;

    private static final int ROW_X = 0;
    private static final int ROW_Z = 1;
    private static final int ROW_Y = 2;
    private static final int ROW_SPEED = 3;

    private static final String[] LABELS = {"X", "Z", "Y", "Speed"};

    private final long pos;
    private final int limitXZ;
    private final int limitY;
    private final int speedMultiplier;

    private RangeValues values;
    /** 内容块顶部坐标（垂直居中算出来的）。 */
    private int originY;

    public RangeScreen(long pos, RangeValues values, int limitXZ, int limitY, int speedMultiplier) {
        this.pos = pos;
        this.values = values;
        this.limitXZ = limitXZ;
        this.limitY = limitY;
        this.speedMultiplier = speedMultiplier;
    }

    public long pos() {
        return pos;
    }

    public void updateValues(RangeValues values) {
        this.values = values;
        refreshButtons();
    }

    @Override
    public boolean func_73868_f() {
        return false; // 不暂停游戏，便于直接观察加速效果
    }

    @Override
    public void func_73866_w_() {
        this.field_146292_n.clear();
        layout();
        int centerX = this.field_146294_l / 2;
        for (int row = 0; row < LABELS.length; row++) {
            int y = originY + ROWS_TOP + row * ROW_HEIGHT;
            this.field_146292_n.add(new GuiButton(minusId(row), centerX + MINUS_X, y, BUTTON_SIZE, BUTTON_SIZE, "-"));
            this.field_146292_n.add(new GuiButton(plusId(row), centerX + PLUS_X, y, BUTTON_SIZE, BUTTON_SIZE, "+"));
        }
        this.field_146292_n.add(new GuiButton(ID_DONE, centerX - 60, originY + DONE_TOP, 120, 20, "Done"));
        refreshButtons();
    }

    @Override
    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        func_146276_q_(); // 原版菜单背景
        layout();
        int centerX = this.field_146294_l / 2;
        func_73732_a(this.field_146289_q, "Torcherino", centerX, originY + TITLE_Y, 0xFFFFFF);
        for (int row = 0; row < LABELS.length; row++) {
            int y = originY + ROWS_TOP + row * ROW_HEIGHT;
            func_73731_b(this.field_146289_q, LABELS[row], centerX + LABEL_X, y + 6, 0xA0A0A0);
            func_73732_a(this.field_146289_q, Integer.toString(displayValue(row)), centerX, y + 6, 0xFFFFFF);
        }
        func_73732_a(this.field_146289_q, "Area: " + values.shape(), centerX, originY + FOOTER_TOP, 0x55FF55);
        func_73732_a(this.field_146289_q, "Speed: " + (values.speed() * speedMultiplier * 100) + "%",
                centerX, originY + FOOTER_TOP + 12, 0xA0A0A0);
        super.func_73863_a(mouseX, mouseY, partialTicks); // 按钮由父类统一绘制
    }

    private void layout() {
        originY = (this.field_146295_m - CONTENT_HEIGHT) / 2;
    }

    @Override
    protected void func_146284_a(GuiButton button) throws IOException {
        super.func_146284_a(button);
        if (button.field_146127_k == ID_DONE) {
            this.field_146297_k.func_147108_a(null); // 原版关闭方式
            return;
        }
        int row = rowOf(button.field_146127_k);
        if (row >= 0) {
            change(row, button.field_146127_k == minusId(row) ? -1 : 1);
        }
    }

    /** 本地先改（界面立即响应），并发给服务端；服务端按上限校验后会回显权威值。 */
    private void change(int row, int delta) {
        int xRange = values.xRange();
        int zRange = values.zRange();
        int yRange = values.yRange();
        int speed = values.speed();
        if (row == ROW_X) {
            xRange += delta;
        } else if (row == ROW_Z) {
            zRange += delta;
        } else if (row == ROW_Y) {
            yRange += delta;
        } else {
            speed += delta;
        }
        RangeValues updated = new RangeValues(xRange, zRange, yRange, speed).clamped(limitXZ, limitY);
        if (updated.xRange() == values.xRange() && updated.zRange() == values.zRange()
                && updated.yRange() == values.yRange() && updated.speed() == values.speed()) {
            return;
        }
        values = updated;
        refreshButtons();
        Torcherino.network.sendToServer(new RangeUpdateMessage(pos, values));
    }

    private void refreshButtons() {
        if (this.field_146292_n == null) {
            return;
        }
        for (GuiButton button : this.field_146292_n) {
            int row = rowOf(button.field_146127_k);
            if (row < 0) {
                continue;
            }
            int range = rangeOf(row);
            int limit = limitOf(row);
            button.field_146124_l = button.field_146127_k == minusId(row) ? range > 0 : range < limit;
        }
    }

    private int displayValue(int row) {
        if (row == ROW_X) {
            return values.xSize();
        }
        if (row == ROW_Z) {
            return values.zSize();
        }
        if (row == ROW_Y) {
            return values.ySize();
        }
        return values.speed();
    }

    private int rangeOf(int row) {
        if (row == ROW_X) {
            return values.xRange();
        }
        if (row == ROW_Z) {
            return values.zRange();
        }
        if (row == ROW_Y) {
            return values.yRange();
        }
        return values.speed();
    }

    private int limitOf(int row) {
        if (row == ROW_Y) {
            return limitY;
        }
        if (row == ROW_SPEED) {
            return RangeValues.LIMIT_SPEED;
        }
        return limitXZ;
    }

    private static int minusId(int row) {
        return row * 2;
    }

    private static int plusId(int row) {
        return row * 2 + 1;
    }

    private static int rowOf(int id) {
        return id >= 0 && id < LABELS.length * 2 ? id / 2 : -1;
    }
}
