package hackradio.biomeextractor.fabric;

import hackradio.biomeextractor.BiomeExtractorCommon;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.lwjgl.glfw.GLFW;

public class BiomeExtractorClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Tooltips
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            BiomeExtractorCommon.appendBiomeTooltip(stack, lines);
        });

        // 2. Register the Keybind (Default: B)
        KeyMapping gridKeybind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.biomeextractor.toggle_grid",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KeyMapping.Category.MISC
        ));

        // 3. Listen for Key Presses
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (gridKeybind.consumeClick()) {
                BiomeExtractorCommon.showBiomeGrid = !BiomeExtractorCommon.showBiomeGrid;
            }
        });

        // 4. Inject into the 3D Render Pipeline using the new Level API
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {

            // 1. The Paintbrush (Using the bufferSource you found!)
            var buffer = context.bufferSource().getBuffer(RenderTypes.LINES);

            // 2. The Camera (Bypassing the yellow warning to ask Minecraft directly)
            var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();

            // 3. The Matrix (Using whichever poseStack/matrixStack method worked for you!)
            var matrix = context.poseStack();

            BiomeExtractorCommon.renderBiomeGrid(matrix, camera, buffer);
        });
    }
}