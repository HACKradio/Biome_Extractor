package hackradio.biomeextractor.fabric;

import hackradio.biomeextractor.BiomeExtractorCommon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

public class BiomeExtractorClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Listen for ANY item getting its tooltip drawn
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            // Pass the item and the list of text lines to our Common logic
            BiomeExtractorCommon.appendBiomeTooltip(stack, lines);
        });
    }
}