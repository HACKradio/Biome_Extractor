package hackradio.biomeextractor.neoforge;

import hackradio.biomeextractor.BiomeExtractorCommon;
import hackradio.biomeextractor.network.CycleSizePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

// The @Mod annotation tells NeoForge this is the entrypoint
@Mod(BiomeExtractorCommon.MOD_ID)
public class BiomeExtractorNeoForge {

    public BiomeExtractorNeoForge(IEventBus modEventBus) {
        // 1. Boot up the Common logic and hand it the NeoForge tools
        NeoForgeRegistryHelper registryHelper = new NeoForgeRegistryHelper(BiomeExtractorCommon.MOD_ID);
        BiomeExtractorCommon.init(registryHelper);

        // 2. Hand our filled-out registry waiting list to the Mod Event Bus
        registryHelper.getRegister().register(modEventBus);

        // 3. Wire up the Harvest and Transplant Events to the main Gameplay Bus
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(this::onRightClickBlock);

        // NEW: Tell NeoForge to load our Data Components!
        NeoForgeRegistryHelper.DATA_COMPONENT_TYPES.register(modEventBus);

        // Listen for network registration
        modEventBus.addListener(this::registerPayloads);
    }

    private void onBlockBreak(BreakBlockEvent event) {
        // Route NeoForge's event into our Common method
        boolean shouldContinueNormalDrop = BiomeExtractorCommon.handleBlockBreak(
                (Level) event.getLevel(),
                event.getPlayer(),
                event.getPos(),
                event.getState()
        );

        // Our Common logic returns false if it successfully harvested the biome.
        // If false, we tell NeoForge to cancel the normal dirt drop!
        if (!shouldContinueNormalDrop) {
            event.setCanceled(true);
        }
    }

    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getFace() == null) return;

        // Figure out exactly where the block is being placed
        var placedPos = event.getPos().relative(event.getFace());

        // Route NeoForge's event into our Common method
        InteractionResult result = BiomeExtractorCommon.handleBlockPlace(
                event.getEntity(),
                event.getLevel(),
                event.getHand(),
                placedPos
        );

        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
    // Tells NeoForge what to do when it receives your network envelope
    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(BiomeExtractorCommon.MOD_ID);

        registrar.playToServer(
                CycleSizePayload.TYPE,
                CycleSizePayload.CODEC,
                (_, context) -> {
                    // context.enqueueWork() is critical! It ensures the code runs on the main server thread, preventing crashes.
                    context.enqueueWork(() -> {
                        if (context.player() instanceof ServerPlayer serverPlayer) {
                            // The gray text vanishes! We pass the player to the method we built earlier.
                            BiomeExtractorCommon.handleCycleSize(serverPlayer);
                        }
                    });
                }
        );
    }
}