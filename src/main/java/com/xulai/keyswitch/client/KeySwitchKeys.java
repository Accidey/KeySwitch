package com.xulai.keyswitch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xulai.keyswitch.KeySwitch;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class KeySwitchKeys {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(KeySwitch.MODID, "main"));

    public static final KeyMapping OVERVIEW = new KeyMapping("key.keyswitch.overview", InputConstants.KEY_F8, CATEGORY);

    private KeySwitchKeys() {
    }
}
