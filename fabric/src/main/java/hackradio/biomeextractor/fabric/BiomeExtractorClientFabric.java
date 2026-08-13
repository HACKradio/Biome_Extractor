package hackradio.biomeextractor.fabric;

import com.mojang.blaze3d.platform.InputConstants.Type;
import hackradio.biomeextractor.BiomeExtractorCommon;
import hackradio.biomeextractor.network.CycleSizePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class BiomeExtractorClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Tooltips
        ItemTooltipCallback.EVENT.register((stack, _, _, lines) -> BiomeExtractorCommon.appendBiomeTooltip(stack, lines));

        // Create the modern Category object
        KeyMapping.Category customCategory = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("biomeextractor", "custom_category")
        );

        // 2. Register the Keybind (Default: B)
        KeyMapping gridKeybind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.biomeextractor.toggle_grid",
                Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                customCategory
        ));

        // 3. Listen for Key Presses
        ClientTickEvents.END_CLIENT_TICK.register(_ -> {
            while (gridKeybind.consumeClick()) {
                BiomeExtractorCommon.showBiomeGrid = !BiomeExtractorCommon.showBiomeGrid;
            }
        });

        // 4. Inject into the 3D Render Pipeline
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            var buffer = context.bufferSource().getBuffer(RenderTypes.LINES);
            var camera = Minecraft.getInstance().gameRenderer.mainCamera();
            var matrix = context.poseStack();

            BiomeExtractorCommon.renderBiomeGrid(matrix, camera, buffer);
        });

        // 2. Create and register the V keybind
        KeyMapping cycleSizeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.biomeextractor.cycle_size", // Translation key
                Type.KEYSYM, // The modern input type
                GLFW.GLFW_KEY_V,  // Default Key (V)
                customCategory    // Category object
                )
        );

        // 2. Listen for the key press every client ticks
        ClientTickEvents.END_CLIENT_TICK.register(_ -> {

            // The same while loop trick to catch fast button mashing
            while (cycleSizeKey.consumeClick()) {

                // Send the empty envelope to the Server!
                ClientPlayNetworking.send(
                        new CycleSizePayload()
                );
            }
        });
    }
}