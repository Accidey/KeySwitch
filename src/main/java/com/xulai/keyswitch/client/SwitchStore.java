package com.xulai.keyswitch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xulai.keyswitch.KeySwitch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;

public final class SwitchStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("keyswitch").resolve("state.json");
    }

    public static JsonObject read() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return new JsonObject();
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            return root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
        } catch (Exception exception) {
            KeySwitch.LOGGER.warn("[keyswitch] 读取 {} 失败，按空记录继续", path, exception);
            return new JsonObject();
        }
    }

    public static void write(JsonObject root) {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            KeySwitch.LOGGER.warn("[keyswitch] 写入 {} 失败", path, exception);
        }
    }

    public static void readStringMap(JsonObject root, String section, Map<String, String> target) {
        JsonElement element = root.get(section);
        if (element == null || !element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                target.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    public static void readBindingMap(JsonObject root, String section, Map<String, Binding> target) {
        JsonElement element = root.get(section);
        if (element == null || !element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) {
                continue;
            }
            Binding binding = Binding.parse(entry.getValue().getAsString());
            if (binding != null) {
                target.put(entry.getKey(), binding);
            }
        }
    }

    public static void readListMap(JsonObject root, String section, Map<String, List<String>> target) {
        JsonElement element = root.get(section);
        if (element == null || !element.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (JsonElement item : entry.getValue().getAsJsonArray()) {
                if (item.isJsonPrimitive()) {
                    names.add(item.getAsString());
                }
            }
            if (!names.isEmpty()) {
                target.put(entry.getKey(), names);
            }
        }
    }

    public static JsonObject toJsonObject(Map<String, String> values) {
        JsonObject root = new JsonObject();
        values.forEach(root::addProperty);
        return root;
    }

    private SwitchStore() {
    }
}
