package hackradio.biomeextractor.neoforge;

import com.mojang.blaze3d.platform.InputConstants.Type;
import hackradio.biomeextractor.BiomeExtractorCommon;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

// Notice: No 'bus =' parameter! NeoForge handles it automatically now.
@EventBusSubscriber(modid = BiomeExtractorCommon.MOD_ID, value = Dist.CLIENT)
public class BiomeExtractorClientNeoForge {

    // The B Key (Also using the new modern constructor)
    public static final KeyMapping TOGGLE_GRID_KEY = new KeyMapping(
            "key.biomeextractor.toggle_grid",
            Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            NeoForgeClientSetup.CUSTOM_CATEGORY // THE FIX: Grab the shared object from the other file!
    );

    // 1. Tooltips
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        BiomeExtractorCommon.appendBiomeTooltip(event.getItemStack(), event.getToolTip());
    }

    // 2. Listen for Key Presses
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE_GRID_KEY.consumeClick()) {
            BiomeExtractorCommon.showBiomeGrid = !BiomeExtractorCommon.showBiomeGrid;
        }
    }

    // 3. Inject into the 3D Render Pipeline
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        // 1. Fetch the platform's multi-buffer source context natively from the event structure
        var bufferSource = event.getMultiBufferSource();
        if (bufferSource == null) return;

        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var matrix = event.getPoseStack();

        // 2. Pass the dynamic multi-buffer source provider onward
        BiomeExtractorCommon.renderBiomeGrid(matrix, camera, bufferSource);
    }


}