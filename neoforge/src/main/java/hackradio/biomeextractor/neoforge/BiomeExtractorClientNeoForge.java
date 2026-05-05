package hackradio.biomeextractor.neoforge;

import hackradio.biomeextractor.BiomeExtractorCommon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

// This annotation explicitly isolates this code from dedicated servers
@EventBusSubscriber(modid = BiomeExtractorCommon.MOD_ID, value = Dist.CLIENT)
public class BiomeExtractorClientNeoForge {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        // Pass the item and the tooltip list to our Common logic
        BiomeExtractorCommon.appendBiomeTooltip(event.getItemStack(), event.getToolTip());
    }
}