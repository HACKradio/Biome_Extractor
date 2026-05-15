package hackradio.biomeextractor.neoforge;

import com.mojang.blaze3d.platform.InputConstants.Type;
import hackradio.biomeextractor.BiomeExtractorCommon;
import hackradio.biomeextractor.network.CycleSizePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

// This controls the main class. It defaults to the Game Bus (for live events like rendering)
@EventBusSubscriber(modid = "biomeextractor", value = Dist.CLIENT)
public class BiomeExtractorClientNeoForge {

    // Define the Keybind
    public static final KeyMapping GRID_KEYBIND = new KeyMapping(
            "key.biomeextractor.toggle_grid",
            Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.biomeextractor"
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
        // Inside your ClientTickEvent loop:
        while (NeoForgeClientSetup.CYCLE_SIZE_KEY.consumeClick()) {
            // 1.21.1 Standard: PacketDistributor
            PacketDistributor.sendToServer(
                    new CycleSizePayload()
            );
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
}