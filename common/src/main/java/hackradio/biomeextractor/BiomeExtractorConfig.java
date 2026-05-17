package hackradio.biomeextractor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class BiomeExtractorConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config", BiomeExtractorCommon.MOD_ID + ".json");

    // --- CONFIG OPTIONS ---
    public boolean requireCorrectTool = true;
    public boolean useWhitelist = true;

    // We mix Tags (starting with #) and specific blocks!
    public List<String> allowedBlocks = new ArrayList<>(List.of(
            "#minecraft:dirt",
            "#minecraft:sand",
            "#minecraft:terracotta",
            "#minecraft:base_stone_overworld",
            "#minecraft:base_stone_nether",
            "#minecraft:leaves",
            "minecraft:gravel",
            "minecraft:moss_block"
    ));

    public static BiomeExtractorConfig INSTANCE = new BiomeExtractorConfig();

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, BiomeExtractorConfig.class);
            } catch (Exception e) {
                BiomeExtractorCommon.LOGGER.error("Failed to load config!", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            BiomeExtractorCommon.LOGGER.error("Failed to save config!", e);
        }
    }
}