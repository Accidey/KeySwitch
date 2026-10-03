package com.xulai.keyswitch.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.xulai.keyswitch.Config;
import com.xulai.keyswitch.KeySwitch;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class KeySwitchEngine {

    private static final Map<String, Binding> REGISTRY = new HashMap<>();
    private static final Map<String, List<String>> BLOCKED = new LinkedHashMap<>();
    private static final Map<InputConstants.Key, List<KeyMapping>> GROUPS = new LinkedHashMap<>();
    private static final Map<KeyMapping, Boolean> APPLY = new IdentityHashMap<>();

    private static final List<String> VANILLA_CATEGORIES = List.of(
            KeyMapping.CATEGORY_CREATIVE,
            KeyMapping.CATEGORY_GAMEPLAY,
            KeyMapping.CATEGORY_INTERFACE,
            KeyMapping.CATEGORY_INVENTORY,
            KeyMapping.CATEGORY_MISC,
            KeyMapping.CATEGORY_MULTIPLAYER,
            KeyMapping.CATEGORY_MOVEMENT);

    private static Map<String, String> modNames;
    private static String saved = "";
    private static boolean initialised;
    private static boolean frozen;

    public static void init(Minecraft mc) {
        if (initialised) {
            return;
        }
        JsonObject root = SwitchStore.read();
        SwitchStore.readListMap(root, "choices", BLOCKED);
        SwitchStore.readBindingMap(root, "bindings", REGISTRY);
        boolean repaired = false;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null) {
                continue;
            }
            Binding live = Binding.of(mapping);
            Binding stored = REGISTRY.get(mapping.getName());
            if (!live.unbound() || stored == null) {
                REGISTRY.put(mapping.getName(), live);
            } else if (!stored.unbound() && !isIntendedBlocked(mapping, stored)) {
                stored.applyTo(mapping);
                repaired = true;
            }
        }
        initialised = true;
        sync(mc);
        if (repaired) {
            restoreEverywhere(mc);
            mc.options.save();
            sync(mc);
            KeySwitch.LOGGER.info("[keyswitch] 上次退出时有按键仍处于屏蔽状态，已把真实绑定写回 options.txt");
        }
        logRegistry(mc);
    }

    private static boolean isIntendedBlocked(KeyMapping mapping, Binding known) {
        List<String> blocked = BLOCKED.get(known.input().getName());
        return blocked != null && blocked.contains(mapping.getName());
    }

    public static void sync(Minecraft mc) {
        if (!initialised || frozen) {
            return;
        }
        GROUPS.clear();
        APPLY.clear();
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null) {
                continue;
            }
            REGISTRY.computeIfAbsent(mapping.getName(), name -> Binding.of(mapping));
            Binding real = REGISTRY.get(mapping.getName());
            if (!real.unbound()) {
                GROUPS.computeIfAbsent(real.input(), name -> new ArrayList<>()).add(mapping);
            }
        }
        for (List<KeyMapping> mappings : GROUPS.values()) {
            mappings.sort(Comparator.comparing(KeyMapping::getName));
        }
        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : GROUPS.entrySet()) {
            List<String> blocked = BLOCKED.get(entry.getKey().getName());
            if (blocked == null) {
                continue;
            }
            List<String> alive = new ArrayList<>();
            for (KeyMapping mapping : entry.getValue()) {
                if (blocked.contains(mapping.getName())) {
                    alive.add(mapping.getName());
                }
            }
            if (alive.isEmpty()) {
                BLOCKED.remove(entry.getKey().getName());
            } else {
                BLOCKED.put(entry.getKey().getName(), alive);
            }
        }
        for (Map.Entry<InputConstants.Key, List<KeyMapping>> entry : GROUPS.entrySet()) {
            List<String> blocked = BLOCKED.getOrDefault(entry.getKey().getName(), List.of());
            for (KeyMapping mapping : entry.getValue()) {
                APPLY.put(mapping, blocked.contains(mapping.getName()) && !isProtected(mapping));
            }
        }
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null) {
                continue;
            }
            Binding real = REGISTRY.get(mapping.getName());
            if (Boolean.TRUE.equals(APPLY.get(mapping))) {
                if (real.unbound()) {
                    continue;
                }
                if (!mapping.isUnbound()) {
                    Binding live = Binding.of(mapping);
                    if (!live.equals(real)) {
                        REGISTRY.put(mapping.getName(), live);
                    }
                    Binding.UNBOUND.applyTo(mapping);
                    mapping.setDown(false);
                }
            } else if (mapping.isUnbound() && !real.unbound()) {
                real.applyTo(mapping);
                mapping.setDown(false);
            } else {
                Binding live = Binding.of(mapping);
                if (!live.equals(real)) {
                    REGISTRY.put(mapping.getName(), live);
                }
            }
        }
        KeyMapping.resetMapping();
        save();
    }

    public static void startEditing(Minecraft mc) {
        if (!initialised || frozen) {
            return;
        }
        frozen = true;
        restoreEverywhere(mc);
        KeySwitch.LOGGER.debug("[keyswitch] 进入按键绑定界面，先还原所有被屏蔽的按键");
    }

    public static void stopEditing(Minecraft mc) {
        if (!initialised) {
            return;
        }
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping != null) {
                REGISTRY.put(mapping.getName(), Binding.of(mapping));
            }
        }
        frozen = false;
        sync(mc);
    }

    private static void restoreEverywhere(Minecraft mc) {
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null) {
                continue;
            }
            Binding real = REGISTRY.get(mapping.getName());
            if (real != null && !real.unbound() && (mapping.isUnbound() || !Binding.of(mapping).equals(real))) {
                real.applyTo(mapping);
                mapping.setDown(false);
            }
        }
        KeyMapping.resetMapping();
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static List<KeyMapping> mappingsOf(InputConstants.Key input) {
        return GROUPS.getOrDefault(input, List.of());
    }

    public static boolean isConflict(InputConstants.Key input) {
        List<KeyMapping> mappings = GROUPS.get(input);
        if (mappings == null || mappings.size() < 2) {
            return false;
        }
        for (KeyMapping mapping : mappings) {
            if (!isProtected(mapping)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSwitchable(InputConstants.Key input) {
        List<KeyMapping> mappings = GROUPS.get(input);
        if (mappings == null || mappings.size() < 2) {
            return false;
        }
        for (KeyMapping mapping : mappings) {
            if (isProtected(mapping)) {
                return false;
            }
        }
        return true;
    }

    public static List<InputConstants.Key> conflictKeys() {
        List<InputConstants.Key> keys = new ArrayList<>();
        for (InputConstants.Key input : GROUPS.keySet()) {
            if (isConflict(input)) {
                keys.add(input);
            }
        }
        keys.sort(Comparator.comparing(InputConstants.Key::getName));
        return keys;
    }

    public static List<KeyMapping> activeOf(InputConstants.Key input) {
        List<KeyMapping> active = new ArrayList<>();
        for (KeyMapping mapping : mappingsOf(input)) {
            if (!Boolean.TRUE.equals(APPLY.get(mapping))) {
                active.add(mapping);
            }
        }
        return active;
    }

    public static boolean isBlocked(KeyMapping mapping) {
        return Boolean.TRUE.equals(APPLY.get(mapping));
    }

    public static void chooseSolo(Minecraft mc, InputConstants.Key input, KeyMapping mapping) {
        List<String> blocked = new ArrayList<>();
        for (KeyMapping other : mappingsOf(input)) {
            if (other != mapping && !isProtected(other)) {
                blocked.add(other.getName());
            }
        }
        if (blocked.isEmpty()) {
            BLOCKED.remove(input.getName());
        } else {
            BLOCKED.put(input.getName(), blocked);
        }
        sync(mc);
    }

    public static void toggleBlocked(Minecraft mc, InputConstants.Key input, KeyMapping mapping) {
        if (isProtected(mapping)) {
            return;
        }
        List<String> blocked = new ArrayList<>(BLOCKED.getOrDefault(input.getName(), List.of()));
        if (!blocked.remove(mapping.getName())) {
            blocked.add(mapping.getName());
        }
        if (blocked.isEmpty()) {
            BLOCKED.remove(input.getName());
        } else {
            BLOCKED.put(input.getName(), blocked);
        }
        sync(mc);
    }

    public static void release(Minecraft mc, InputConstants.Key input) {
        BLOCKED.remove(input.getName());
        sync(mc);
    }

    public static void choose(Minecraft mc, InputConstants.Key input, @Nullable KeyMapping mapping) {
        if (mapping == null) {
            release(mc, input);
        } else {
            chooseSolo(mc, input, mapping);
        }
    }

    public static Binding realOf(KeyMapping mapping) {
        Binding real = REGISTRY.get(mapping.getName());
        return real == null ? Binding.of(mapping) : real;
    }

    public static boolean isProtected(KeyMapping mapping) {
        return mapping == KeySwitchKeys.OVERVIEW || Config.PROTECTED_BINDINGS.get().contains(mapping.getName());
    }

    public static Component ownerOf(KeyMapping mapping) {
        if (VANILLA_CATEGORIES.contains(mapping.getCategory())) {
            return Component.translatable("text.keyswitch.owner.minecraft");
        }
        String token = tokenOf(mapping.getName(), "key.");
        if (token == null) {
            token = tokenOf(mapping.getCategory(), "key.categories.");
        }
        String owner = token == null ? null : modNames().get(token);
        return owner == null
                ? Component.translatable("text.keyswitch.owner.unknown")
                : Component.literal(owner);
    }

    private static String tokenOf(String value, String prefix) {
        if (!value.startsWith(prefix)) {
            return null;
        }
        String remainder = value.substring(prefix.length());
        int dot = remainder.indexOf('.');
        return dot <= 0 ? null : remainder.substring(0, dot);
    }

    private static Map<String, String> modNames() {
        if (modNames == null) {
            Map<String, String> names = new HashMap<>();
            ModList.get().getMods().forEach(info -> names.put(info.getModId(), info.getDisplayName()));
            modNames = names;
        }
        return modNames;
    }

    private static void save() {
        Map<String, String> bindings = new HashMap<>();
        REGISTRY.forEach((name, binding) -> bindings.put(name, binding.save()));
        JsonObject root = new JsonObject();
        root.add("bindings", SwitchStore.toJsonObject(bindings));
        JsonObject choices = new JsonObject();
        BLOCKED.forEach((input, names) -> {
            JsonArray array = new JsonArray();
            names.forEach(array::add);
            choices.add(input, array);
        });
        root.add("choices", choices);
        String serialized = root.toString();
        if (!serialized.equals(saved)) {
            saved = serialized;
            SwitchStore.write(root);
        }
    }

    public static void logRegistry(Minecraft mc) {
        List<InputConstants.Key> conflicts = conflictKeys();
        KeySwitch.LOGGER.info("[keyswitch] 已登记 {} 个按键绑定，检测到 {} 个按键被多个绑定共用",
                REGISTRY.size(), conflicts.size());
        for (InputConstants.Key input : conflicts) {
            List<KeyMapping> active = activeOf(input);
            List<String> names = new ArrayList<>();
            for (KeyMapping mapping : mappingsOf(input)) {
                names.add(mapping.getName() + (isProtected(mapping) ? "(白名单)" : isBlocked(mapping) ? "(已屏蔽)" : ""));
            }
            KeySwitch.LOGGER.info("[keyswitch]   {} -> {}，当前触发 {}", input.getName(), names,
                    active.stream().map(KeyMapping::getName).toList());
        }
    }

    private KeySwitchEngine() {
    }
}
