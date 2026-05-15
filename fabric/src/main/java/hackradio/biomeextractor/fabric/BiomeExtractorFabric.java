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
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            // Your existing code stays exactly the same here!
            // We just ignore the 'blockEntity' variable since we don't need it.

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

        PayloadTypeRegistry.playC2S()
                .register(CycleSizePayload.TYPE, CycleSizePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(
                CycleSizePayload.TYPE,
                (payload, context) -> BiomeExtractorCommon.handleCycleSize(context.player())
        );
    }
}