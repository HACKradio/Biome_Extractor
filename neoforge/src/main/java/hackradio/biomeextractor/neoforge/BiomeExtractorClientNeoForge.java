package hackradio.biomeextractor.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import hackradio.biomeextractor.BiomeExtractorCommon;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// This controls the main class. It defaults to the Game Bus (for live events like rendering)
@EventBusSubscriber(modid = "biomeextractor", value = net.neoforged.api.distmarker.Dist.CLIENT)
public class BiomeExtractorClientNeoForge {

    // Define the Keybind
    public static final KeyMapping GRID_KEYBIND = new KeyMapping(
            "key.biomeextractor.toggle_grid",
            InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_B,
            "key.categories.misc"
    );

    // 1. Tooltips
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        BiomeExtractorCommon.appendBiomeTooltip(event.getItemStack(), event.getToolTip());
    }

    // 2. Listen for Key Presses
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (GRID_KEYBIND.consumeClick()) {
            BiomeExtractorCommon.showBiomeGrid = !BiomeExtractorCommon.showBiomeGrid;
        }
    }

    // 3. Inject into the 3D Render Pipeline
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {

        // We must manually check if we are in the correct rendering stage
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {

            // Use the singular RenderType class and lowercase lines() method
            var buffer = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.lines());

            // 1.21.1's RenderLevelStageEvent provides the camera natively!
            var camera = event.getCamera();
            var matrix = event.getPoseStack();

            BiomeExtractorCommon.renderBiomeGrid(matrix, camera, buffer);
        }
    }

    // ==========================================
    // THE MOD BUS (Setup Events)
    // ==========================================
    // By adding "bus = EventBusSubscriber.Bus.MOD", we tell NeoForge this is only for startup!
    @EventBusSubscriber(modid = "biomeextractor", value = net.neoforged.api.distmarker.Dist.CLIENT, bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onRegisterKeyBinds(RegisterKeyMappingsEvent event) {
            // Register the keybind on the correct bus!
            event.register(GRID_KEYBIND);
        }

    }
}