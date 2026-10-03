package com.xulai.keyswitch.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeySwitchKeys {

    public static final String CATEGORY = "key.categories.keyswitch";

    public static final KeyMapping OVERVIEW = new KeyMapping("key.keyswitch.overview", GLFW.GLFW_KEY_F8, CATEGORY);

    private KeySwitchKeys() {
    }
}
