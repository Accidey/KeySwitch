package com.xulai.keyswitch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xulai.keyswitch.Config;
import com.xulai.keyswitch.KeySwitch;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

@Mod(value = KeySwitch.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = KeySwitch.MODID, value = Dist.CLIENT)
public final class KeySwitchClient {

    private static final Map<InputConstants.Key, Long> HELD = new HashMap<>();
    private static final Map<InputConstants.Key, Long> PULSES = new HashMap<>();
    private static final Set<InputConstants.Key> SWALLOWED = new HashSet<>();
    private static final long TOAST_MILLIS = 3500L;
    private static final long PULSE_MILLIS = 60L;

    private static long nextScan;
    private static Component toast;
    private static long toastUntil;
    private static boolean hintShown;

    public KeySwitchClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeySwitchKeys.OVERVIEW);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key input = InputConstants.getKey(event.getKey(), event.getScanCode());
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            release(mc, input);
            return;
        }
        if (KeySwitchKeys.OVERVIEW.matches(event.getKey(), event.getScanCode())) {
            KeySwitchKeys.OVERVIEW.consumeClick();
            HELD.remove(input);
            if (mc.screen instanceof KeySwitchScreen switcher) {
                switcher.onClose();
            } else if (mc.player != null && mc.getOverlay() == null && !(mc.screen instanceof KeyBindsScreen)) {
                mc.pushGuiLayer(new KeySwitchScreen(null));
            }
            return;
        }
        if (event.getAction() == GLFW.GLFW_REPEAT) {
            if (mc.screen == null && SWALLOWED.contains(input)) {
                swallow(input);
            }
            return;
        }
        if (WheelSwitch.isActive()) {
            commitWheel(mc);
        }
        if (!canArm(mc) || !KeySwitchEngine.isSwitchable(input)) {
            HELD.remove(input);
            return;
        }
        if (swallowArmed(mc)) {
            swallow(input);
        }
        HELD.putIfAbsent(input, Util.getMillis());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onMouseButton(InputEvent.MouseButton.Post event) {
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key input = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            release(mc, input);
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (WheelSwitch.isActive()) {
            commitWheel(mc);
        }
        if (!Config.MOUSE_BUTTON_LONG_PRESS.get() || !canArm(mc) || !KeySwitchEngine.isSwitchable(input)) {
            HELD.remove(input);
            return;
        }
        if (swallowArmed(mc)) {
            swallow(input);
        }
        HELD.putIfAbsent(input, Util.getMillis());
    }

    @SubscribeEvent
    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (WheelSwitch.isActive()) {
            if (WheelSwitch.scrolled(mc, event.getScrollDeltaY())) {
                event.setCanceled(true);
            }
            return;
        }
        InputConstants.Key armed = armedKey(mc);
        if (armed != null && mc.screen == null && startSwitching(mc, armed, event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (WheelSwitch.isActive()) {
            if (WheelSwitch.scrolled(mc, event.getScrollDeltaY())) {
                event.setCanceled(true);
            }
            return;
        }
        if (!Config.WORK_INSIDE_GUI.get()) {
            return;
        }
        InputConstants.Key armed = armedKey(mc);
        if (armed != null && startSwitching(mc, armed, event.getScrollDeltaY())) {
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
        if (swallowArmed(mc)) {
            swallow(input);
        }
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
        for (Map.Entry<InputConstants.Key, Long> entry : HELD.entrySet()) {
            if (now - entry.getValue() >= required && KeySwitchEngine.isSwitchable(entry.getKey())) {
                best = entry.getKey();
                break;
            }
        }
        return best;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (now >= nextScan) {
            nextScan = now + 1000L;
            if (mc.screen instanceof KeyBindsScreen) {
                KeySwitchEngine.startEditing(mc);
            } else {
                KeySwitchEngine.init(mc);
                KeySwitchEngine.sync(mc);
            }
        }
        for (Iterator<Map.Entry<InputConstants.Key, Long>> it = PULSES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<InputConstants.Key, Long> pulse = it.next();
            if (now >= pulse.getValue()) {
                it.remove();
                KeyMapping.set(pulse.getKey(), false);
            }
        }
        if (!mc.isWindowActive()) {
            clearHolds();
            WheelSwitch.stop();
        }
        if (mc.level == null) {
            clearHolds();
            hintShown = false;
            return;
        }
        if (WheelSwitch.isActive()) {
            if (WheelSwitch.expired()) {
                commitWheel(mc);
            }
            keepSwallowing(mc);
        }
        if (!hintShown && mc.player != null && mc.player.tickCount > 40) {
            hintShown = true;
            sendConflictHint(mc);
        }
    }

    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof KeyBindsScreen) {
            KeySwitchEngine.startEditing(Minecraft.getInstance());
        }
    }

    @SubscribeEvent
    static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof KeyBindsScreen) {
            KeySwitchEngine.stopEditing(Minecraft.getInstance());
        } else if (event.getScreen() instanceof KeySwitchScreen) {
            clearHolds();
        }
    }

    @SubscribeEvent
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
                .drawCenteredString(mc.font, toast, mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() - 60, 0xFFD75F);
    }

    @SubscribeEvent
    static void onScreenRender(ScreenEvent.Render.Post event) {
        WheelSwitch.render(event.getGuiGraphics(), Minecraft.getInstance().font);
    }

    public static void showToast(Component message) {
        Minecraft mc = Minecraft.getInstance();
        toast = message;
        toastUntil = Util.getMillis() + TOAST_MILLIS;
        if (Config.SHOW_SWITCH_TOAST.get() && mc.player != null) {
            mc.gui.getChat().addMessage(message);
        }
    }

    private static void release(Minecraft mc, InputConstants.Key input) {
        HELD.remove(input);
        if (WheelSwitch.holds(input)) {
            commitWheel(mc);
            return;
        }
        if (SWALLOWED.remove(input)) {
            KeyMapping.click(input);
            if (mc.screen == null) {
                KeyMapping.set(input, true);
                PULSES.put(input, Util.getMillis() + PULSE_MILLIS);
            }
        }
    }

    private static void commitWheel(Minecraft mc) {
        InputConstants.Key committed = WheelSwitch.commit(mc);
        if (committed != null) {
            SWALLOWED.remove(committed);
            HELD.remove(committed);
        }
    }

    private static boolean swallowArmed(Minecraft mc) {
        return Config.SWALLOW_HELD_TRIGGER.get() && mc.screen == null && !isTyping(mc);
    }

    private static void swallow(InputConstants.Key input) {
        for (KeyMapping mapping : KeySwitchEngine.mappingsOf(input)) {
            int guard = 0;
            while (guard++ < 8 && mapping.consumeClick()) {
                mapping.setDown(false);
            }
            mapping.setDown(false);
        }
        SWALLOWED.add(input);
    }

    private static void keepSwallowing(Minecraft mc) {
        if (mc.screen != null || SWALLOWED.isEmpty()) {
            return;
        }
        for (InputConstants.Key held : Set.copyOf(SWALLOWED)) {
            swallow(held);
        }
    }

    private static void clearHolds() {
        HELD.clear();
        SWALLOWED.clear();
        PULSES.clear();
    }

    private static void sendConflictHint(Minecraft mc) {
        if (!Config.SHOW_CONFLICT_HINT.get() || mc.player == null) {
            return;
        }
        int conflicts = KeySwitchEngine.conflictKeys().size();
        if (conflicts == 0) {
            return;
        }
        mc.gui.getChat().addMessage(Component.translatable(
                "text.keyswitch.hint",
                conflicts,
                KeySwitchKeys.OVERVIEW.getTranslatedKeyMessage()));
    }

    private static boolean canArm(Minecraft mc) {
        if (mc.player == null || mc.getOverlay() != null) {
            return false;
        }
        if (mc.screen instanceof KeySwitchScreen || mc.screen instanceof KeyBindsScreen) {
            return false;
        }
        if (isTyping(mc)) {
            return false;
        }
        return mc.screen == null || Config.WORK_INSIDE_GUI.get();
    }

    private static boolean isTyping(Minecraft mc) {
        return mc.screen != null && mc.screen.getFocused() instanceof EditBox box && box.canConsumeInput();
    }
}
