package com.xulai.keyswitch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xulai.keyswitch.Config;
import com.xulai.keyswitch.KeySwitch;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = KeySwitch.MODID, value = Dist.CLIENT)
public final class KeySwitchClient {

    private static final Map<InputConstants.Key, Long> HELD = new HashMap<>();
    private static final long TOAST_MILLIS = 3500L;

    private static long nextScan;
    private static Component toast;
    private static long toastUntil;
    private static boolean hintShown;

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
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
                mc.setScreen(new KeySwitchScreen(null, mc.screen));
            }
            return;
        }
        if (event.getAction() == GLFW.GLFW_REPEAT) {
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
            WheelSwitch.commit(mc);
        }
        if (!Config.MOUSE_BUTTON_LONG_PRESS.get() || !canArm(mc) || !KeySwitchEngine.isSwitchable(input)) {
            HELD.remove(input);
            return;
        }
        HELD.putIfAbsent(input, Util.getMillis());
    }

    @SubscribeEvent
    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        double delta = event.getScrollDelta();
        if (WheelSwitch.isActive()) {
            if (WheelSwitch.scrolled(mc, delta)) {
                event.setCanceled(true);
            }
            return;
        }
        InputConstants.Key armed = armedKey(mc);
        if (armed != null && mc.screen == null && startSwitching(mc, armed, delta)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        double delta = event.getScrollDelta();
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
        long window = mc.getWindow().getWindow();
        if (input.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, input.getValue()) == GLFW.GLFW_PRESS;
        }
        if (input.getType() == InputConstants.Type.KEYSYM) {
            return InputConstants.isKeyDown(window, input.getValue());
        }
        return true;
    }

    @SubscribeEvent
    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
            HELD.clear();
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
            WheelSwitch.commit(mc);
        }
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

    private KeySwitchClient() {
    }
}
