package com.xulai.keyswitch.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyModifier;

public record Binding(InputConstants.Key input, KeyModifier modifier) {

    public static final Binding UNBOUND = new Binding(InputConstants.UNKNOWN, KeyModifier.NONE);

    public static Binding of(KeyMapping mapping) {
        return new Binding(mapping.getKey(), mapping.getKeyModifier());
    }

    public static Binding parse(String saved) {
        int split = saved.lastIndexOf('|');
        if (split <= 0) {
            return null;
        }
        InputConstants.Key input = InputConstants.getKey(saved.substring(0, split));
        return new Binding(input, KeyModifier.valueFromString(saved.substring(split + 1)));
    }

    public String save() {
        return this.input.getName() + "|" + this.modifier.name();
    }

    public boolean unbound() {
        return this.input.equals(InputConstants.UNKNOWN);
    }

    public void applyTo(KeyMapping mapping) {
        mapping.setKeyModifierAndCode(this.modifier, this.input);
    }
}
