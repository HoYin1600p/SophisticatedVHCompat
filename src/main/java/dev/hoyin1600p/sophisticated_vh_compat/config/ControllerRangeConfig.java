package dev.hoyin1600p.sophisticated_vh_compat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hoyin1600p.sophisticated_vh_compat.SophisticatedVHCompat;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ControllerRangeConfig {
    public static final int DEFAULT_RANGE = 24;
    public static final int WARNING_RANGE = 50;
    public static final int MAX_RANGE = 96;

    private static final String COMMENT = "Controlled by Sophisticated VH Compat. Use /svhc controller-range enable to activate.";
    private static final String ENABLED_KEY = "enabled";
    private static final String RANGE_KEY = "controllerRange";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConfigValues INITIAL_VALUES = load();
    private static boolean enabled = INITIAL_VALUES.enabled();
    private static int controllerRange = INITIAL_VALUES.range();

    private ControllerRangeConfig() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        ControllerRangeConfig.enabled = enabled;
        save(enabled, controllerRange);
    }

    public static int getControllerRange() {
        return controllerRange;
    }

    public static void setControllerRange(int range) {
        controllerRange = clamp(range);
        save(enabled, controllerRange);
    }

    public static Path getConfigPath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve("sophisticated-storage-override")
                .resolve("controller-range.json");
    }

    private static ConfigValues load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            save(false, DEFAULT_RANGE);
            return new ConfigValues(false, DEFAULT_RANGE);
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            boolean configuredEnabled = json.has(ENABLED_KEY) && json.get(ENABLED_KEY).getAsBoolean();
            int configuredRange = json.has(RANGE_KEY) ? clamp(json.get(RANGE_KEY).getAsInt()) : DEFAULT_RANGE;
            return new ConfigValues(configuredEnabled, configuredRange);
        } catch (Exception exception) {
            SophisticatedVHCompat.LOGGER.error("Failed to read Sophisticated Storage controller range config at {}", path, exception);
        }

        save(false, DEFAULT_RANGE);
        return new ConfigValues(false, DEFAULT_RANGE);
    }

    private static int clamp(int range) {
        return Math.max(1, Math.min(MAX_RANGE, range));
    }

    private static void save(boolean enabled, int range) {
        Path path = getConfigPath();
        JsonObject json = new JsonObject();
        json.addProperty("_comment", COMMENT);
        json.addProperty(ENABLED_KEY, enabled);
        json.addProperty(RANGE_KEY, clamp(range));

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            SophisticatedVHCompat.LOGGER.error("Failed to write Sophisticated Storage controller range config at {}", path, exception);
        }
    }

    private record ConfigValues(boolean enabled, int range) {
    }
}
