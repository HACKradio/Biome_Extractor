package hackradio.biomeextractor.fabric;

import hackradio.biomeextractor.BiomeExtractorCommon;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

public class BiomeExtractorClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 1. Tooltips
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> BiomeExtractorCommon.appendBiomeTooltip(stack, lines));

        // 2. Register the Keybind (Default: B)
        KeyMapping gridKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.biomeextractor.toggle_grid",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "key.categories.misc"
        ));

        // 3. Listen for Key Presses
        ClientTickEvents.END_CLIENT_TICK.register(Client -> {
            while (gridKeybind.consumeClick()) {
                BiomeExtractorCommon.showBiomeGrid = !BiomeExtractorCommon.showBiomeGrid;
            }
        });

        // 4. Inject into the 3D Render Pipeline using Fabric's WorldRenderEvents
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {

            // 1. The Paintbrush (Using consumers and the singular RenderType)
            var buffer = Objects.requireNonNull(context.consumers()).getBuffer(net.minecraft.client.renderer.RenderType.lines());

            // 2. The Camera (The 1.21.1 context provides this natively, no bypass needed!)
            var camera = context.camera();

            // 3. The Matrix (Using the older matrixStack name)
            var matrix = context.matrixStack();

            BiomeExtractorCommon.renderBiomeGrid(matrix, camera, buffer);
        });
    }
}