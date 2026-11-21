package com.xulai.keyswitch.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public record Binding(InputConstants.Key input) {

    public static final Binding UNBOUND = new Binding(InputConstants.UNKNOWN);

    public static Binding of(KeyMapping mapping) {
        Binding binding = parse(mapping.saveString());
        return binding == null ? UNBOUND : binding;
    }

    public static Binding parse(String saved) {
        int split = saved.lastIndexOf('|');
        String name = split > 0 ? saved.substring(0, split) : saved;
        try {
            return new Binding(InputConstants.getKey(name));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String save() {
        return this.input.getName();
    }

    public boolean unbound() {
        return this.input.equals(InputConstants.UNKNOWN);
    }

    public void applyTo(KeyMapping mapping) {
        mapping.setKey(this.input);
    }
}
