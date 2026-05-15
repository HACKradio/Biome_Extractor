package hackradio.biomeextractor.neoforge;

import com.mojang.blaze3d.platform.InputConstants.Type;
import hackradio.biomeextractor.BiomeExtractorCommon;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import static hackradio.biomeextractor.neoforge.BiomeExtractorClientNeoForge.GRID_KEYBIND;

@EventBusSubscriber(modid = BiomeExtractorCommon.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class NeoForgeClientSetup {

    // 1.21.1 Standard: Raw String for the category
    public static final KeyMapping CYCLE_SIZE_KEY = new KeyMapping(
            "key.biomeextractor.cycle_size",
            Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.biomeextractor"
    );

    @net.neoforged.bus.api.SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(CYCLE_SIZE_KEY);
        event.register(GRID_KEYBIND);
        // Register your B key here too!
    }
}