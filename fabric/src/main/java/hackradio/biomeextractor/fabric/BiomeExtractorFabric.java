package hackradio.biomeextractor.fabric;

import hackradio.biomeextractor.BiomeExtractorCommon;
import hackradio.biomeextractor.network.CycleSizePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class BiomeExtractorFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // 1. Boot up the Common logic and hand it the Fabric tools!
        BiomeExtractorCommon.init(new FabricRegistryHelper(BiomeExtractorCommon.MOD_ID));

        // 2. Wire up the Harvest Event
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, _) -> {
            // Route Fabric's event directly into our Common method
            return BiomeExtractorCommon.handleBlockBreak(level, player, pos, state);
        });

        // 3. Wire up the Transplant Event
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            // Figure out exactly where the block is being placed
            var placedPos = hitResult.getBlockPos().relative(hitResult.getDirection());

            // Route Fabric's event directly into our Common method
            return BiomeExtractorCommon.handleBlockPlace(player, level, hand, placedPos);
        });

        // 1. Register the payload type and codec so Fabric knows it exists
        PayloadTypeRegistry.serverboundPlay()
                .register(CycleSizePayload.TYPE, CycleSizePayload.CODEC);

        // 2. Tell the server what to do when it receives the packet
        ServerPlayNetworking.registerGlobalReceiver(
                CycleSizePayload.TYPE,
                (_, context) -> {
                    // The gray text vanishes here too!
                    BiomeExtractorCommon.handleCycleSize(context.player());
                }
        );
    }
}