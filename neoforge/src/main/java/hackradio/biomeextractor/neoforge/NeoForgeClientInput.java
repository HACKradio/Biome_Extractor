package hackradio.biomeextractor.neoforge;

import hackradio.biomeextractor.BiomeExtractorCommon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

// The Game Bus handles live, in-game events (like pressing a button while walking around)
@EventBusSubscriber(modid = BiomeExtractorCommon.MOD_ID, value = Dist.CLIENT)
public class NeoForgeClientInput {

    @net.neoforged.bus.api.SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {

        // A while loop is the best practice here. It ensures that if the player mashes the V key
        // superfast, it registers every single click during that tick!
        while (NeoForgeClientSetup.CYCLE_SIZE_KEY.consumeClick()) {

            // THE FIX: Use ClientPacketDistributor to send from the client!
            ClientPacketDistributor.sendToServer(
                    new hackradio.biomeextractor.network.CycleSizePayload()
            );
        }
    }
}