package com.xulai.keyswitch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xulai.keyswitch.Config;
import com.xulai.keyswitch.KeySwitch;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

@Mod(value = KeySwitch.MODID, dist = Dist.CLIENT)
public final class KeySwitchClient {

    private static final Map<InputConstants.Key, Long> HELD = new HashMap<>();
    private static final long TOAST_MILLIS = 3500L;

    private static long nextScan;
    private static Component toast;
    private static long toastUntil;
    private static boolean hintShown;

    public KeySwitchClient(ModContainer container, IEventBus modEventBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(KeySwitchClient::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, KeySwitchClient::onKeyInput);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, KeySwitchClient::onMouseButton);
        NeoForge.EVENT_BUS.addListener(KeySwitchClient::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(KeySwitchClient::onScreenMouseScroll);
        NeoForge.EVENT_BUS.addListener(KeySwitchClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(KeySwitchClient::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(KeySwitchClient::onScreenClosing);
        NeoForge.EVENT_BUS.addListener(KeySwitchClient::onRenderGui);
        NeoForge.EVENT_BUS.addListener(KeySwitchClient::onScreenRender);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeySwitchKeys.OVERVIEW);
    }

    static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key input = InputConstants.getKey(event.getKeyEvent());
        if (event.getAction() == InputConstants.RELEASE) {
            release(mc, input);
            return;
        }
        if (KeySwitchKeys.OVERVIEW.matches(input)) {
            KeySwitchKeys.OVERVIEW.consumeClick();
            HELD.remove(input);
            Screen screen = mc.gui.screen();
            if (screen instanceof KeySwitchScreen switcher) {
                switcher.onClose();
            } else if (mc.player != null && mc.gui.overlay() == null && !(screen instanceof KeyBindsScreen)) {
                mc.gui.setScreen(new KeySwitchScreen(null, screen));
            }
            return;
        }
        if (event.getAction() == InputConstants.REPEAT) {
            return;
        }
        if (WheelSwitch.isActive()) {
            WheelSwitch.commit(mc);
        }
        if (!canArm(mc) || !KeySwitchEngine.isSwitchable(input)) {
            HELD.remove(input);
            return;
        }
        HELD.putIfAbsent(input, Util.getMillis());
    }

    static void onMouseButton(InputEvent.MouseButton.Post event) {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key input = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        if (event.getAction() == InputConstants.RELEASE) {
            release(mc, input);
            return;
        }
        if (event.getAction() != InputConstants.PRESS) {
            return;
        }
        if (WheelSwitch.isActive()) {
            WheelSwitch.commit(mc);
        }
        if (!Config.MOUSE_BUTTON_LONG_PRESS.get() || !canArm(mc) || !KeySwitchEngine.isSwitchable(input)) {
            HELD.remove(input);
            return;
        }
        HELD.putIfAbsent(input, Util.getMillis());
    }

    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        double delta = event.getScrollDeltaY();
        if (WheelSwitch.isActive()) {
            if (WheelSwitch.scrolled(mc, delta)) {
                event.setCanceled(true);
            }
            return;
        }
        InputConstants.Key armed = armedKey(mc);
        if (armed != null && mc.gui.screen() == null && startSwitching(mc, armed, delta)) {
            event.setCanceled(true);
        }
    }

    static void onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        double delta = event.getScrollDeltaY();
        if (WheelSwitch.isActive()) {
            if (WheelSwitch.scrolled(mc, delta)) {
                event.setCanceled(true);
            }
            return;
        }
        if (!Config.WORK_INSIDE_GUI.get()) {
            return;
        }
        InputConstants.Key armed = armedKey(mc);
        if (armed != null && startSwitching(mc, armed, delta)) {
            event.setCanceled(true);
        }
    }

    private static boolean startSwitching(Minecraft mc, InputConstants.Key input, double delta) {
        if (!KeySwitchEngine.isSwitchable(input)) {
            return false;
        }
        WheelSwitch.start(mc, input);
        if (!WheelSwitch.isActive()) {
            return false;
        }
        WheelSwitch.scrolled(mc, delta);
        return true;
    }

    @Nullable
    private static InputConstants.Key armedKey(Minecraft mc) {
        if (!canArm(mc) || HELD.isEmpty()) {
            return null;
        }
        long required = Config.LONG_PRESS_MILLIS.get();
        long now = Util.getMillis();
        InputConstants.Key best = null;
        for (Iterator<Map.Entry<InputConstants.Key, Long>> it = HELD.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<InputConstants.Key, Long> entry = it.next();
            if (!stillDown(mc, entry.getKey())) {
                it.remove();
                continue;
            }
            if (best == null && now - entry.getValue() >= required && KeySwitchEngine.isSwitchable(entry.getKey())) {
                best = entry.getKey();
            }
        }
        return best;
    }

    private static boolean stillDown(Minecraft mc, InputConstants.Key input) {
        if (input.getType() == InputConstants.Type.MOUSE) {
            int button = input.getValue();
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                return mc.mouseHandler.isLeftPressed();
            }
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                return mc.mouseHandler.isRightPressed();
            }
            if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
                return mc.mouseHandler.isMiddlePressed();
            }
            return true;
        }
        if (input.getType() == InputConstants.Type.KEYBOARD) {
            return InputConstants.isKeyDown(input.getValue());
        }
        return true;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (now >= nextScan) {
            nextScan = now + 1000L;
            if (mc.gui.screen() instanceof KeyBindsScreen) {
                KeySwitchEngine.startEditing(mc);
            } else {
                KeySwitchEngine.init(mc);
                KeySwitchEngine.sync(mc);
            }
        }
        if (!mc.isWindowActive()) {
            HELD.clear();
            WheelSwitch.stop();
        }
        if (mc.level == null) {
            HELD.clear();
            hintShown = false;
            return;
        }
        if (WheelSwitch.isActive() && WheelSwitch.expired()) {
            WheelSwitch.commit(mc);
        }
        if (!hintShown && mc.player != null && mc.player.tickCount > 40) {
            hintShown = true;
            sendConflictHint(mc);
        }
    }

    static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof KeyBindsScreen) {
            KeySwitchEngine.startEditing(Minecraft.getInstance());
        }
    }

    static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof KeyBindsScreen) {
            KeySwitchEngine.stopEditing(Minecraft.getInstance());
        } else if (event.getScreen() instanceof KeySwitchScreen) {
            HELD.clear();
        }
    }

    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        WheelSwitch.render(event.getGuiGraphics(), mc.font);
        if (toast == null) {
            return;
        }
        if (Util.getMillis() >= toastUntil) {
            toast = null;
            return;
        }
        if (!Config.SHOW_SWITCH_TOAST.get()) {
            return;
        }
        event.getGuiGraphics()
                .centeredText(mc.font, toast, mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() - 60, 0xFFD75F);
    }

    static void onScreenRender(ScreenEvent.Render.Foreground event) {
        WheelSwitch.render(event.getGuiGraphics(), Minecraft.getInstance().font);
    }

    public static void showToast(Component message) {
        Minecraft mc = Minecraft.getInstance();
        toast = message;
        toastUntil = Util.getMillis() + TOAST_MILLIS;
        if (Config.SHOW_SWITCH_TOAST.get() && mc.player != null) {
            mc.gui.hud.getChat().addClientSystemMessage(message);
        }
    }

    private static void release(Minecraft mc, InputConstants.Key input) {
        HELD.remove(input);
        if (WheelSwitch.holds(input)) {
            WheelSwitch.commit(mc);
        }
    }

    private static boolean canArm(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (mc.player == null || mc.gui.overlay() != null) {
            return false;
        }
        if (screen instanceof KeySwitchScreen || screen instanceof KeyBindsScreen) {
            return false;
        }
        if (isTyping(screen)) {
            return false;
        }
        return screen == null || Config.WORK_INSIDE_GUI.get();
    }

    private static boolean isTyping(@Nullable Screen screen) {
        return screen != null && screen.getFocused() instanceof EditBox box && box.canConsumeInput();
    }

    private static void sendConflictHint(Minecraft mc) {
        if (!Config.SHOW_CONFLICT_HINT.get() || mc.player == null) {
            return;
        }
        int conflicts = KeySwitchEngine.conflictKeys().size();
        if (conflicts == 0) {
            return;
        }
        mc.gui.hud.getChat().addClientSystemMessage(Component.translatable(
                "text.keyswitch.hint",
                conflicts,
                KeySwitchKeys.OVERVIEW.getTranslatedKeyMessage()));
    }
}
