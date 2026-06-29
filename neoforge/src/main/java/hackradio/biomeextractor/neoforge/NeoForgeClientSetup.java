package hackradio.biomeextractor.neoforge;

import com.mojang.blaze3d.platform.InputConstants.Type;
import hackradio.biomeextractor.BiomeExtractorCommon;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

// THE FIX: Added 'bus = EventBusSubscriber.Bus.MOD' so this runs during the loading screen!
@EventBusSubscriber(modid = BiomeExtractorCommon.MOD_ID, value = Dist.CLIENT)
public class NeoForgeClientSetup {

    public static final Category CUSTOM_CATEGORY =
            new Category(
                    Identifier.fromNamespaceAndPath("biomeextractor", "custom_category")
            );

    public static final KeyMapping CYCLE_SIZE_KEY = new KeyMapping(
            "key.biomeextractor.cycle_size",
            Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CUSTOM_CATEGORY
    );

    @net.neoforged.bus.api.SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        // 1. Tell the game the custom category exists
        event.registerCategory(CUSTOM_CATEGORY);

        // 2. Register both of your keys in one place!
        event.register(CYCLE_SIZE_KEY);
        event.register(BiomeExtractorClientNeoForge.TOGGLE_GRID_KEY);
    }
}