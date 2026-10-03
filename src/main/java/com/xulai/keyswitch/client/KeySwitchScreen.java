package com.xulai.keyswitch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xulai.keyswitch.Config;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class KeySwitchScreen extends Screen {

    private static final int ROW_HEIGHT = 30;
    private static final int PADDING = 10;
    private static final int HEADER = 62;
    private static final int FOOTER = 46;

    private static final int BACKDROP = 0xFF0B0D10;
    private static final int PANEL = 0xFF15181D;
    private static final int PANEL_BORDER = 0xFF69788C;
    private static final int ROW_HIGHLIGHT = 0x59306E9E;
    private static final int ROW_ALTERNATE = 0x14FFFFFF;
    private static final int ACCENT = 0xFF7FB4E0;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFC3CAD3;
    private static final int TEXT_KEY = 0xFFFFE08A;

    @Nullable
    private final InputConstants.Key focus;
    private static Integer blurBackup;
    private static int openScreens;
    private boolean counted;
    private List<KeyMapping> mappings = List.of();
    private List<InputConstants.Key> conflicts = List.of();
    private int cursor;
    private int scroll;
    private int panelX;
    private int panelWidth;
    private int listTop;
    private int visibleRows;

    public KeySwitchScreen(@Nullable InputConstants.Key focus) {
        super(Component.translatable(focus == null ? "screen.keyswitch.overview" : "screen.keyswitch.picker"));
        this.focus = focus;
    }

    @Override
    protected void init() {
        super.init();
        if (!this.counted) {
            this.counted = true;
            openScreens++;
            if (blurBackup == null) {
                int blur = this.minecraft.options.menuBackgroundBlurriness().get();
                blurBackup = blur;
                if (blur > 0) {
                    this.minecraft.options.menuBackgroundBlurriness().set(0);
                }
            }
        }
        this.reload();
        this.cursor = 0;
        this.scroll = 0;
        this.panelWidth = Math.min(470, this.width - 32);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.listTop = HEADER;
        this.visibleRows = Math.max(1, (this.height - FOOTER - this.listTop) / ROW_HEIGHT);
    }

    private void reload() {
        this.mappings = this.focus == null ? List.of() : new ArrayList<>(KeySwitchEngine.mappingsOf(this.focus));
        this.conflicts = this.focus == null ? KeySwitchEngine.conflictKeys() : List.of();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, BACKDROP);
        int panelRows = Math.max(1, Math.min(this.rowCount(), this.visibleRows));
        int panelBottom = this.listTop + panelRows * ROW_HEIGHT + 8;
        int left = this.panelX - 8;
        int right = this.panelX + this.panelWidth + 8;
        guiGraphics.fill(left, 8, right, panelBottom, PANEL);
        guiGraphics.fill(left, 8, right, 9, PANEL_BORDER);
        guiGraphics.fill(left, panelBottom - 1, right, panelBottom, PANEL_BORDER);
        guiGraphics.fill(left, 8, left + 1, panelBottom, PANEL_BORDER);
        guiGraphics.fill(right - 1, 8, right, panelBottom, PANEL_BORDER);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, TEXT);
        guiGraphics.drawCenteredString(this.font, this.subtitleText(), this.width / 2, 30, TEXT_DIM);
        guiGraphics.drawCenteredString(this.font, this.keyLine(), this.width / 2, 43, TEXT_KEY);
        guiGraphics.fill(left, 54, right, 55, 0x33FFFFFF);

        int hovered = this.rowAt(mouseX, mouseY);
        if (hovered >= 0) {
            this.cursor = hovered;
            this.reveal(this.cursor);
        }

        if (this.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, this.emptyText(), this.width / 2, this.listTop + 8, TEXT_KEY);
        } else {
            int last = Math.min(this.rowCount(), this.scroll + this.visibleRows);
            for (int index = this.scroll; index < last; index++) {
                this.renderRow(guiGraphics, index, index == this.cursor);
            }
        }

        this.drawHints(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawHints(GuiGraphics guiGraphics) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component hint : List.of(this.hintText(), this.hint2Text())) {
            lines.addAll(this.font.split(hint, this.width - 24));
        }
        int step = this.font.lineHeight + 2;
        int shown = Math.min(lines.size(), Math.max(1, (this.height - this.listTop - 8) / step));
        int y = this.height - 8 - (shown - 1) * step;
        for (int index = Math.max(0, lines.size() - shown); index < lines.size(); index++) {
            guiGraphics.drawCenteredString(this.font, lines.get(index), this.width / 2, y, TEXT_DIM);
            y += step;
        }
    }

    private void renderRow(GuiGraphics guiGraphics, int index, boolean highlighted) {
        int top = this.listTop + (index - this.scroll) * ROW_HEIGHT;
        int bottom = top + ROW_HEIGHT - 3;
        if ((index & 1) == 1) {
            guiGraphics.fill(this.panelX, top, this.panelX + this.panelWidth, bottom, ROW_ALTERNATE);
        }
        if (highlighted) {
            guiGraphics.fill(this.panelX, top, this.panelX + this.panelWidth, bottom, ROW_HIGHLIGHT);
            guiGraphics.fill(this.panelX, top, this.panelX + 3, bottom, ACCENT);
        }
        Component status = this.rowStatus(index);
        int statusLeft = this.panelX + this.panelWidth - PADDING - this.font.width(status);
        int labelRoom = Math.max(48, statusLeft - 10 - this.panelX - PADDING);
        guiGraphics.drawString(this.font, this.fit(this.rowLabel(index), labelRoom), this.panelX + PADDING, top + 4, TEXT, false);
        guiGraphics.drawString(this.font, status, statusLeft, top + 4, TEXT, false);
        guiGraphics.drawString(this.font, this.fit(this.rowDetail(index), this.panelWidth - PADDING * 2),
                this.panelX + PADDING, top + 16, TEXT_DIM, false);
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0 || this.font.width(text) <= maxWidth) {
            return text;
        }
        int end = text.length();
        while (end > 1 && this.font.width(text.substring(0, end) + "…") > maxWidth) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = this.rowAt((int) mouseX, (int) mouseY);
        if (row >= 0 && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            this.select(row, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.setScroll(this.scroll - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) {
            this.moveCursor(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            this.select(this.cursor, Screen.hasShiftDown());
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < this.rowCount()) {
                this.select(index, false);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveCursor(int step) {
        this.cursor = Mth.clamp(this.cursor + step, 0, Math.max(0, this.rowCount() - 1));
        this.reveal(this.cursor);
    }

    private void reveal(int index) {
        if (index < this.scroll) {
            this.setScroll(index);
        } else if (index >= this.scroll + this.visibleRows) {
            this.setScroll(index - this.visibleRows + 1);
        }
    }

    private void setScroll(int value) {
        this.scroll = Mth.clamp(value, 0, Math.max(0, this.rowCount() - this.visibleRows));
    }

    private int rowAt(int mouseX, int mouseY) {
        if (this.isEmpty() || mouseX < this.panelX || mouseX > this.panelX + this.panelWidth) {
            return -1;
        }
        if (mouseY < this.listTop || mouseY >= this.listTop + this.visibleRows * ROW_HEIGHT) {
            return -1;
        }
        int index = this.scroll + (mouseY - this.listTop) / ROW_HEIGHT;
        return index < this.rowCount() ? index : -1;
    }

    private int rowCount() {
        if (this.focus == null) {
            return this.conflicts.size();
        }
        return this.mappings.isEmpty() ? 0 : this.mappings.size() + 1;
    }

    private boolean isEmpty() {
        return this.rowCount() == 0;
    }

    private String rowLabel(int index) {
        if (this.focus == null) {
            return (index + 1) + ".  " + this.conflicts.get(index).getDisplayName().getString();
        }
        if (index >= this.mappings.size()) {
            return "→  " + Component.translatable("screen.keyswitch.action_all").getString();
        }
        return (index + 1) + ".  " + this.mappings.get(index).getDisplayName().getString();
    }

    private String rowDetail(int index) {
        if (this.focus == null) {
            InputConstants.Key key = this.conflicts.get(index);
            StringBuilder detail = new StringBuilder();
            for (KeyMapping mapping : KeySwitchEngine.mappingsOf(key)) {
                if (!detail.isEmpty()) {
                    detail.append("   ");
                }
                detail.append(KeySwitchEngine.ownerOf(mapping).getString()).append(' ')
                        .append(mapping.getDisplayName().getString());
                if (KeySwitchEngine.isBlocked(mapping)) {
                    detail.append("  [").append(Component.translatable("text.keyswitch.status.blocked").getString()).append(']');
                }
            }
            return detail.toString();
        }
        if (index >= this.mappings.size()) {
            return Component.translatable("screen.keyswitch.action_all.tooltip").getString();
        }
        KeyMapping mapping = this.mappings.get(index);
        return KeySwitchEngine.realOf(mapping).input().getDisplayName().getString()
                + "   " + KeySwitchEngine.ownerOf(mapping).getString()
                + "   " + Component.translatable(mapping.getCategory()).getString()
                + "   §8" + mapping.getName() + "§r";
    }

    private Component rowStatus(int index) {
        if (this.focus == null) {
            InputConstants.Key key = this.conflicts.get(index);
            List<KeyMapping> active = KeySwitchEngine.activeOf(key);
            int total = KeySwitchEngine.mappingsOf(key).size();
            if (active.size() == total) {
                return Component.translatable("text.keyswitch.status.all").withStyle(ChatFormatting.GRAY);
            }
            if (active.size() == 1) {
                return Component.translatable("text.keyswitch.status.active_of", active.get(0).getDisplayName())
                        .withStyle(ChatFormatting.GREEN);
            }
            return Component.translatable("text.keyswitch.status.active_many", active.size()).withStyle(ChatFormatting.GREEN);
        }
        if (index >= this.mappings.size()) {
            return Component.translatable("text.keyswitch.status.neutral").withStyle(ChatFormatting.GRAY);
        }
        KeyMapping mapping = this.mappings.get(index);
        if (KeySwitchEngine.isProtected(mapping)) {
            return Component.translatable("text.keyswitch.status.protected").withStyle(ChatFormatting.AQUA);
        }
        if (KeySwitchEngine.isBlocked(mapping)) {
            return Component.translatable("text.keyswitch.status.blocked").withStyle(ChatFormatting.RED);
        }
        return isManaged()
                ? Component.translatable("text.keyswitch.status.active").withStyle(ChatFormatting.GREEN)
                : Component.translatable("text.keyswitch.status.all").withStyle(ChatFormatting.YELLOW);
    }

    private boolean isManaged() {
        List<KeyMapping> active = KeySwitchEngine.activeOf(this.focus);
        return active.size() < this.mappings.size();
    }

    private void select(int index, boolean toggleOnly) {
        Minecraft mc = this.minecraft;
        if (this.focus == null) {
            if (index >= 0 && index < this.conflicts.size()) {
                mc.pushGuiLayer(new KeySwitchScreen(this.conflicts.get(index)));
            }
            return;
        }
        if (index >= this.mappings.size()) {
            KeySwitchEngine.release(mc, this.focus);
            this.onClose();
            KeySwitchClient.showToast(Component.translatable("text.keyswitch.restored", this.focus.getDisplayName()));
            return;
        }
        KeyMapping mapping = this.mappings.get(index);
        if (toggleOnly) {
            KeySwitchEngine.toggleBlocked(mc, this.focus, mapping);
            this.reload();
            KeySwitchClient.showToast(Component.translatable("text.keyswitch.toggled",
                    mapping.getDisplayName(), this.focus.getDisplayName()));
            return;
        }
        int blocked = 0;
        for (KeyMapping other : this.mappings) {
            if (other != mapping && !KeySwitchEngine.isProtected(other)) {
                blocked++;
            }
        }
        KeySwitchEngine.chooseSolo(mc, this.focus, mapping);
        this.onClose();
        KeySwitchClient.showToast(Component.translatable("text.keyswitch.switched",
                this.focus.getDisplayName(), mapping.getDisplayName(), blocked));
    }

    private Component subtitleText() {
        if (this.focus == null) {
            return Component.translatable("screen.keyswitch.overview.subtitle", this.conflicts.size());
        }
        List<KeyMapping> active = KeySwitchEngine.activeOf(this.focus);
        Component who = active.size() == 1
                ? active.get(0).getDisplayName()
                : Component.translatable("text.keyswitch.status.active_many", active.size());
        return Component.translatable("screen.keyswitch.picker.subtitle", who);
    }

    private Component keyLine() {
        if (this.focus == null) {
            return Component.translatable("screen.keyswitch.overview.keyline",
                    KeySwitchKeys.OVERVIEW.getTranslatedKeyMessage());
        }
        if (!KeySwitchEngine.isSwitchable(this.focus)) {
            return Component.translatable("screen.keyswitch.picker.keyline_locked",
                    this.focus.getDisplayName(),
                    this.mappings.size(),
                    KeySwitchKeys.OVERVIEW.getTranslatedKeyMessage());
        }
        return Component.translatable("screen.keyswitch.picker.keyline",
                this.focus.getDisplayName(),
                this.mappings.size(),
                Config.LONG_PRESS_MILLIS.get());
    }

    private Component hintText() {
        return Component.translatable("screen.keyswitch.hint1",
                KeySwitchKeys.OVERVIEW.getTranslatedKeyMessage());
    }

    private Component hint2Text() {
        return Component.translatable("screen.keyswitch.hint2");
    }

    private Component emptyText() {
        return this.focus == null
                ? Component.translatable("screen.keyswitch.overview.empty")
                : Component.translatable("screen.keyswitch.picker.empty", this.focus.getDisplayName().getString());
    }

    @Override
    public boolean isPauseScreen() {
        return Config.PAUSE_WHILE_CHOOSING.get();
    }

    @Override
    public void removed() {
        if (this.counted) {
            this.counted = false;
            openScreens--;
            if (openScreens == 0 && blurBackup != null) {
                this.minecraft.options.menuBackgroundBlurriness().set(blurBackup);
                blurBackup = null;
            }
        }
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        if (!KeySwitchEngine.isFrozen()) {
            int known = this.focus == null ? KeySwitchEngine.conflictKeys().size() : KeySwitchEngine.mappingsOf(this.focus).size();
            int shown = this.focus == null ? this.conflicts.size() : this.mappings.size();
            if (known != shown) {
                this.reload();
                this.cursor = Mth.clamp(this.cursor, 0, Math.max(0, this.rowCount() - 1));
            }
        }
    }
}
