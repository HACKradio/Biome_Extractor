package hackradio.biomeextractor;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Supplier;

public class BiomeExtractorCommon {

    public static final String MOD_ID = "biomeextractor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // This is a Supplier because NeoForge doesn't hand over the component immediately.
    public static Supplier<DataComponentType<String>> STORED_BIOME;
    public static Supplier<DataComponentType<Integer>> EXTRACTOR_SIZE;

    public static void init(IRegistryHelper registryHelper) {
        STORED_BIOME = registryHelper.registerBiomeComponent("stored_biome");
        EXTRACTOR_SIZE = registryHelper.registerIntegerComponent("extractor_size");
        LOGGER.info("Biome Extractor Common Logic Initialized!");
    }

    // The toggle switch for our visualizer
    public static boolean showBiomeGrid = false;

    // --- STEP 1: THE HARVEST (Breaking the block) ---
    @SuppressWarnings("SameReturnValue")
    public static boolean handleBlockBreak(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return true;

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) return true;

        // 26.1 Mapping: Identifier instead of ResourceLocation
        ResourceKey<Enchantment> extractorKey = ResourceKey.create(
                Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "biome_extractor")
        );

        // 26.1 Mapping: registryAccess().lookupOrThrow() instead of registryOrThrow()
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var optionalEnchantment = registry.get(extractorKey); // get() instead of getHolder()

        if (optionalEnchantment.isPresent()) {
            int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(optionalEnchantment.get(), heldItem);

            if (enchantLevel > 0) {
                var biomeHolder = level.getBiome(pos);

                // 26.1 Mapping: .unwrapKey().map(key -> key.location().toString()) might now just be .unwrapKey().get().location().toString() or require the Identifier cast.
                String biomeId = biomeHolder.unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("minecraft:plains");

                ItemStack droppedBlock = new ItemStack(state.getBlock());

                droppedBlock.set(STORED_BIOME.get(), biomeId);

                // Pop our custom biome block into the world
                Block.popResource(level, pos, droppedBlock);

                // The Ghost Break: Silently turn the block to air to prevent vanilla dupes
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

                return true;
            }
        }
        return true;
    }

    // --- STEP 2: THE TRANSPLANT (Placing the block) ---
    @SuppressWarnings("SameReturnValue")
    public static InteractionResult handleBlockPlace(Player player, Level level, InteractionHand hand, BlockPos placedPos) {
        if (level.isClientSide()) return InteractionResult.PASS;

        ItemStack itemInHand = player.getItemInHand(hand);

        if (itemInHand.has(STORED_BIOME.get())) {
            String biomeId = itemInHand.get(STORED_BIOME.get());
            int mode = itemInHand.getOrDefault(EXTRACTOR_SIZE.get(), 0);

            var biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
            assert biomeId != null;
            var newBiomeKey = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(biomeId));
            var newBiomeHolder = biomeRegistry.get(newBiomeKey);

            if (newBiomeHolder.isPresent()) {

                // Dynamic radius based on the item size
                int yRadius = (mode > 0) ? 1 : 0;

                // Base 4x4x4 grid coordinates
                int centerX = placedPos.getX() >> 2;
                int centerY = placedPos.getY() >> 2;
                int centerZ = placedPos.getZ() >> 2;

                // We use a Set so we don't accidentally send the same chunk to the client twice!
                java.util.Set<LevelChunk> updatedChunks = new java.util.HashSet<>();

                for (int x = -mode; x <= mode; x++) {
                    for (int y = -yRadius; y <= yRadius; y++) {
                        for (int z = -mode; z <= mode; z++) {

                            int targetX = centerX + x;
                            int targetY = centerY + y;
                            int targetZ = centerZ + z;

                            // Revert back to world coordinates to safely grab the chunk
                            BlockPos targetPos = new BlockPos(targetX << 2, targetY << 2, targetZ << 2);
                            LevelChunk chunk = level.getChunkAt(targetPos);

                            int sectionIndex = chunk.getSectionIndex(targetPos.getY());

                            // Bounds check (prevents crashing if you brush at the top/bottom of the world)
                            if (sectionIndex >= 0 && sectionIndex < chunk.getSections().length) {
                                LevelChunkSection section = chunk.getSections()[sectionIndex];

                                PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();

                                // Get the local section coordinate (0-3) using a bitwise AND
                                biomes.set(targetX & 3, targetY & 3, targetZ & 3, newBiomeHolder.get());

                                chunk.setUnsaved(true);
                                updatedChunks.add(chunk);
                            }
                        }
                    }
                }

                // --- THE NEW, OPTIMIZED NETWORK PACKET ---
                if (level instanceof ServerLevel serverLevel) {
                    // Send all affected chunks in ONE packet!
                    ClientboundChunksBiomesPacket biomePacket =
                            ClientboundChunksBiomesPacket.forChunks(new java.util.ArrayList<>(updatedChunks));

                    // Broadcast to players standing near the original placement block
                    LevelChunk centerChunk = level.getChunkAt(placedPos);
                    serverLevel.getChunkSource().chunkMap.getPlayers(centerChunk.getPos(), false)
                            .forEach(serverPlayer -> serverPlayer.connection.send(biomePacket));
                }
            }
        }
        return InteractionResult.PASS;
    }

    // --- STEP 3: THE TOOLTIP (Client visual) ---
    public static void appendBiomeTooltip(ItemStack stack, List<Component> tooltipLines) {
        if (stack.has(STORED_BIOME.get())) {
            String biomeId = stack.get(STORED_BIOME.get());

            // Adds a blue text line to the item's hover display
            tooltipLines.add(Component.translatable("tooltip.biomeextractor.stored", Objects.requireNonNull(biomeId)).withStyle(ChatFormatting.GREEN));
        }
    }

    // --- STEP 4: THE MATRIX (3D Rendering) ---
    public static void renderBiomeGrid(PoseStack poseStack, Camera camera, VertexConsumer buffer) {
        if (!showBiomeGrid) return;

        // Grab the local player to check their item size!
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack heldItem = player.getMainHandItem();
        if (!heldItem.has(STORED_BIOME.get())) return;

        // Read the size mode from the item (0 = small, 1 = medium, 2 = large)
        int mode = heldItem.getOrDefault(EXTRACTOR_SIZE.get(), 0);

        // Dynamic radius based on the item size
        int yRadius = (mode > 0) ? 1 : 0;

        Vec3 camPos = camera.getPosition();
        int baseBiomeX = (Mth.floor(camPos.x) >> 2) << 2;
        int baseBiomeY = (Mth.floor(camPos.y) >> 2) << 2;
        int baseBiomeZ = (Mth.floor(camPos.z) >> 2) << 2;

        // Loop using our dynamic radius!
        for (int x = -mode; x <= mode; x++) {
            for (int y = -yRadius; y <= yRadius; y++) {
                for (int z = -mode; z <= mode; z++) {

                    poseStack.pushPose();

                    double targetX = baseBiomeX + (x * 4);
                    double targetY = baseBiomeY + (y * 4);
                    double targetZ = baseBiomeZ + (z * 4);

                    poseStack.translate(targetX - camPos.x, targetY - camPos.y, targetZ - camPos.z);

                    boolean isCenterBox = (x == 0 && y == 0 && z == 0);
                    int boxColor = isCenterBox ? (int) 0xFF00FFFFL : (int) 0x8800FFFFL;
                    float boxThickness = isCenterBox ? 3.0F : 1.0F;

                    RenderSystem.lineWidth(boxThickness);

                    float a = ((boxColor >> 24) & 0xFF) / 255.0F;
                    float r = ((boxColor >> 16) & 0xFF) / 255.0F;
                    float g = ((boxColor >> 8) & 0xFF) / 255.0F;
                    float b = (boxColor & 0xFF) / 255.0F;

                    LevelRenderer.renderLineBox(
                            poseStack,
                            buffer,
                            new AABB(0.0, 0.0, 0.0, 4.0, 4.0, 4.0),
                            r, g, b, a
                    );

                    poseStack.popPose();
                }
            }
        }
    }

    public static void handleCycleSize(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.world.item.ItemStack mainHand = player.getMainHandItem();

        if (mainHand.has(STORED_BIOME.get())) {
            int currentMode = mainHand.getOrDefault(EXTRACTOR_SIZE.get(), 0);
            int newMode = (currentMode + 1) % 3;
            mainHand.set(EXTRACTOR_SIZE.get(), newMode);

            String sizeText = switch (newMode) {
                case 0 -> "Small (4x4x4)";
                case 1 -> "Medium (12x12x12)";
                case 2 -> "Large (20x20x12)";
                default -> "Unknown";
            };

            // 1.21.1 Standard: displayClientMessage
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Transplant Size: " + sizeText)
                            .withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD),
                    true
            );
        }
    }
}