package com.xulai.keyswitch.client;

import com.xulai.keyswitch.Config;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

public class KeySwitchConfigScreen extends Screen {

    private static final int[] LONG_PRESS_PRESETS = {120, 200, 300, 400, 500, 600, 800, 1000, 1500, 2000, 3000};

    private final Screen parent;

    public KeySwitchConfigScreen(Screen parent) {
        super(Component.translatable("keyswitch.configuration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 110;
        int top = 40;
        this.addRenderableWidget(Button.builder(longPressLabel(), button -> {
            Config.LONG_PRESS_MILLIS.set(nextLongPress());
            button.setMessage(longPressLabel());
        }).bounds(left, top, 220, 20).build());
        this.addRenderableWidget(toggle(left, top + 24,
                "keyswitch.configuration.workInsideGui", Config.WORK_INSIDE_GUI));
        this.addRenderableWidget(toggle(left, top + 48,
                "keyswitch.configuration.mouseButtonLongPress", Config.MOUSE_BUTTON_LONG_PRESS));
        this.addRenderableWidget(toggle(left, top + 72,
                "keyswitch.configuration.pauseWhileChoosing", Config.PAUSE_WHILE_CHOOSING));
        this.addRenderableWidget(toggle(left, top + 96,
                "keyswitch.configuration.showSwitchToast", Config.SHOW_SWITCH_TOAST));
        this.addRenderableWidget(toggle(left, top + 120,
                "keyswitch.configuration.showConflictHint", Config.SHOW_CONFLICT_HINT));
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left, top + 160, 220, 20).build());
    }

    private Button toggle(int x, int y, String key, ForgeConfigSpec.BooleanValue value) {
        return Button.builder(toggleLabel(key, value.get()), button -> {
            value.set(!value.get());
            button.setMessage(toggleLabel(key, value.get()));
        }).bounds(x, y, 220, 20).build();
    }

    private Component toggleLabel(String key, boolean enabled) {
        return Component.translatable(key).append(Component.literal(": " + (enabled ? "开" : "关")));
    }

    private Component longPressLabel() {
        return Component.translatable("keyswitch.configuration.longPressMillis")
                .append(Component.literal(": " + Config.LONG_PRESS_MILLIS.get() + " ms"));
    }

    private int nextLongPress() {
        int current = Config.LONG_PRESS_MILLIS.get();
        for (int preset : LONG_PRESS_PRESETS) {
            if (preset > current) {
                return preset;
            }
        }
        return LONG_PRESS_PRESETS[0];
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        List<? extends String> protectedBindings = Config.PROTECTED_BINDINGS.get();
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("screen.keyswitch.config.protected", protectedBindings.size()),
                this.width / 2, 40 + 138, 0xFFC3CAD3);
    }

    @Override
    public void onClose() {
        Config.SPEC.save();
        this.minecraft.setScreen(this.parent);
    }
}
