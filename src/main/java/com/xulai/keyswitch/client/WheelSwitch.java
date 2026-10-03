package com.xulai.keyswitch.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class WheelSwitch {

    private static final int ROW_HEIGHT = 13;
    private static final int CARD = 0xE610141A;
    private static final int CARD_BORDER = 0xFF5A636E;
    private static final int HIGHLIGHT = 0x4D74A8D8;
    private static final int ACCENT = 0xFF7FB4E0;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFBAC2CC;
    private static final int TEXT_ON = 0xFF8FD08F;

    @Nullable
    private static InputConstants.Key input;
    private static List<KeyMapping> options = List.of();
    private static int cursor;
    private static long startedAt;

    public static boolean isActive() {
        return input != null;
    }

    public static boolean holds(InputConstants.Key pressed) {
        return input != null && input.equals(pressed);
    }

    public static void start(Minecraft mc, InputConstants.Key pressed) {
        List<KeyMapping> bindings = new ArrayList<>(KeySwitchEngine.mappingsOf(pressed));
        if (bindings.size() < 2) {
            return;
        }
        input = pressed;
        List<KeyMapping> all = new ArrayList<>(bindings);
        all.add(null);
        options = all;
        List<KeyMapping> committed = KeySwitchEngine.activeOf(pressed);
        KeyMapping active = committed.size() == 1 ? committed.get(0) : null;
        cursor = active == null ? 0 : Math.max(0, all.indexOf(active));
        startedAt = System.currentTimeMillis();
    }

    public static boolean expired() {
        return input != null && System.currentTimeMillis() - startedAt > 20000L;
    }

    public static void stop() {
        input = null;
        options = List.of();
    }

    public static boolean scrolled(Minecraft mc, double delta) {
        if (input == null) {
            return false;
        }
        int step = delta > 0 ? -1 : 1;
        cursor = Mth.positiveModulo(cursor + step, options.size());
        return true;
    }

    @Nullable
    public static InputConstants.Key commit(Minecraft mc) {
        if (input == null) {
            return null;
        }
        InputConstants.Key held = input;
        KeyMapping chosen = options.get(cursor);
        int blocked = 0;
        if (chosen != null) {
            for (KeyMapping other : options) {
                if (other != null && other != chosen) {
                    blocked++;
                }
            }
        }
        stop();
        if (chosen == null) {
            KeySwitchEngine.release(mc, held);
            KeySwitchClient.showToast(Component.translatable("text.keyswitch.restored", held.getDisplayName()));
        } else {
            KeySwitchEngine.chooseSolo(mc, held, chosen);
            KeySwitchClient.showToast(Component.translatable("text.keyswitch.switched",
                    held.getDisplayName(), chosen.getDisplayName(), blocked));
        }
        return held;
    }

    public static void render(GuiGraphics guiGraphics, Font font) {
        if (input == null) {
            return;
        }
        Component title = Component.translatable("hud.keyswitch.title", input.getDisplayName(), cursor + 1, options.size());
        int width = font.width(title) + 20;
        for (KeyMapping mapping : options) {
            width = Math.max(width, 30 + font.width(labelOf(mapping)) + font.width(ownerOf(mapping)) + 16);
        }
        width = Math.min(width, guiGraphics.guiWidth() - 16);
        int height = 16 + options.size() * ROW_HEIGHT + 12;
        int x = (guiGraphics.guiWidth() - width) / 2;
        int y = guiGraphics.guiHeight() - 56 - height;

        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, CARD_BORDER);
        guiGraphics.fill(x, y, x + width, y + height, CARD);
        guiGraphics.drawString(font, title, x + 8, y + 5, TEXT, false);

        int rowY = y + 16;
        for (int index = 0; index < options.size(); index++) {
            boolean selected = index == cursor;
            if (selected) {
                guiGraphics.fill(x + 4, rowY - 2, x + width - 4, rowY + ROW_HEIGHT - 2, HIGHLIGHT);
                guiGraphics.fill(x + 4, rowY - 2, x + 6, rowY + ROW_HEIGHT - 2, ACCENT);
            }
            KeyMapping mapping = options.get(index);
            String label = labelOf(mapping);
            String owner = ownerOf(mapping);
            guiGraphics.drawString(font, label, x + 10, rowY, selected ? TEXT : TEXT_DIM, false);
            guiGraphics.drawString(font, owner, x + width - 24 - font.width(owner), rowY,
                    isCommitted(mapping) ? TEXT_ON : TEXT_DIM, false);
            rowY += ROW_HEIGHT;
        }
        guiGraphics.drawString(font, Component.translatable("hud.keyswitch.footer"),
                x + 8, y + height - 11, TEXT_DIM, false);
    }

    private static boolean isCommitted(@Nullable KeyMapping mapping) {
        if (input == null) {
            return false;
        }
        List<KeyMapping> active = KeySwitchEngine.activeOf(input);
        if (mapping == null) {
            return active.size() == options.size() - 1;
        }
        return active.size() == 1 && active.get(0) == mapping;
    }

    private static String labelOf(@Nullable KeyMapping mapping) {
        return mapping == null
                ? Component.translatable("screen.keyswitch.action_all").getString()
                : mapping.getDisplayName().getString();
    }

    private static String ownerOf(@Nullable KeyMapping mapping) {
        if (mapping == null) {
            return ChatFormatting.GRAY + "" + Component.translatable("text.keyswitch.status.neutral").getString()
                    + ChatFormatting.RESET + "";
        }
        return KeySwitchEngine.ownerOf(mapping).getString() + " §8" + KeySwitchEngine.realOf(mapping).input().getName()
                + "§r";
    }

    private WheelSwitch() {
    }
}
