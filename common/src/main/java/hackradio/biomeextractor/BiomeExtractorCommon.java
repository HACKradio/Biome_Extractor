package hackradio.biomeextractor;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.client.renderer.ShapeRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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

    // NEW: Tracks the current size mode (0=Small, 1=Medium, 2=Large)
    public static Supplier<DataComponentType<Integer>> EXTRACTOR_SIZE;

    public static void init(IRegistryHelper registryHelper) {
        STORED_BIOME = registryHelper.registerBiomeComponent("stored_biome");
        EXTRACTOR_SIZE = registryHelper.registerIntegerComponent("extractor_size");
        LOGGER.info("Biome Extractor Common Logic Initialized!");
        BiomeExtractorConfig.load();
    }

    // The toggle switch for our visualizer
    public static boolean showBiomeGrid = false;

    // --- STEP 1: THE HARVEST (Breaking the block) ---
    @SuppressWarnings("SameReturnValue")
    public static boolean handleBlockBreak(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return true;

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) return true;

        ResourceKey<Enchantment> extractorKey = ResourceKey.create(
                Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath(MOD_ID, "biome_extractor")
        );

        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var optionalEnchantment = registry.get(extractorKey);

        if (optionalEnchantment.isPresent()) {
            int enchantLevel = EnchantmentHelper.getItemEnchantmentLevel(optionalEnchantment.get(), heldItem);

            if (enchantLevel > 0) {

                // --- CONFIG CHECK 1: The Smart Whitelist (Tags + Blocks) ---
                if (BiomeExtractorConfig.INSTANCE.useWhitelist) {
                    boolean isAllowed = false;
                    for (String entry : BiomeExtractorConfig.INSTANCE.allowedBlocks) {
                        if (entry.startsWith("#")) {
                            // It is a Tag! (e.g., #minecraft:dirt)
                            var tagKey = TagKey.create(
                                    Registries.BLOCK,
                                    Identifier.parse(entry.substring(1)) // Remove the '#' to parse properly in 26.1 mappings
                            );
                            if (state.is(tagKey)) {
                                isAllowed = true;
                                break;
                            }
                        } else {
                            // It is a specific block! (e.g., minecraft:gravel)
                            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                            if (blockId.equals(entry)) {
                                isAllowed = true;
                                break;
                            }
                        }
                    }

                    if (!isAllowed) {
                        return true; // Not in whitelist! Drop standard vanilla block.
                    }
                }

                // --- CONFIG CHECK 2: Proper Tool Check ---
                if (BiomeExtractorConfig.INSTANCE.requireCorrectTool) {
                    boolean isRightTool = false;

                    if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && heldItem.is(ItemTags.PICKAXES)) isRightTool = true;
                    else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && heldItem.is(ItemTags.SHOVELS)) isRightTool = true;
                    else if (state.is(BlockTags.MINEABLE_WITH_AXE) && heldItem.is(ItemTags.AXES)) isRightTool = true;
                    else if (state.is(BlockTags.MINEABLE_WITH_HOE) && heldItem.is(ItemTags.HOES)) isRightTool = true;

                    if (!isRightTool) {
                        return true; // Wrong tool! Drop vanilla block.
                    }
                }

                // --- ORIGINAL SUCCESS LOGIC ---
                var biomeHolder = level.getBiome(pos);
                String biomeId = biomeHolder.unwrapKey()
                        .map(key -> key.identifier().toString())
                        .orElse("minecraft:plains");

                ItemStack droppedBlock = new ItemStack(state.getBlock());
                droppedBlock.set(STORED_BIOME.get(), biomeId);

                Block.popResource(level, pos, droppedBlock);

                // The Ghost Break: Replace with air to prevent vanilla dupes
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

                // Return TRUE to let the event finish natively for durability and VeinMiner compat!
                return true;
            }
        }
        return true;
    }

    // --- STEP 2: THE TRANSPLANT (Placing the block) [26.1 Modern Mappings] ---
    @SuppressWarnings("SameReturnValue")
    public static InteractionResult handleBlockPlace(Player player, Level level, InteractionHand hand, BlockPos placedPos) {
        if (level.isClientSide()) return InteractionResult.PASS;

        ItemStack itemInHand = player.getItemInHand(hand);

        if (itemInHand.has(STORED_BIOME.get())) {
            String biomeId = itemInHand.get(STORED_BIOME.get());
            var biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
            assert biomeId != null;

            var newBiomeKey = ResourceKey.create(Registries.BIOME, Identifier.parse(biomeId));
            var newBiomeHolder = biomeRegistry.get(newBiomeKey);

            if (newBiomeHolder.isPresent()) {

                // 1. Determine the size mode from the item (Default to 0 / Small)
                int mode = itemInHand.getOrDefault(EXTRACTOR_SIZE.get(), 0);

                // 2. Set radius's based on mode (Small: 0x0x0, Medium: 1x1x1, Large: 2x1x2)
                int yRadius = (mode > 0) ? 1 : 0;

                // 3. Keep track of which chunks we actually touched so we don't spam network packets
                java.util.Set<LevelChunk> modifiedChunks = new java.util.HashSet<>();

                // 4. The 3D Loop!
                for (int dx = -mode; dx <= mode; dx++) {
                    for (int dy = -yRadius; dy <= yRadius; dy++) {
                        for (int dz = -mode; dz <= mode; dz++) {

                            // Calculate the absolute position of this specific biome section
                            BlockPos targetPos = placedPos.offset(dx * 4, dy * 4, dz * 4);

                            // Fetch the correct chunk for this specific position (CRITICAL for large areas)
                            LevelChunk chunk = level.getChunkAt(targetPos);
                            int sectionIndex = chunk.getSectionIndex(targetPos.getY());

                            // Safety check: Don't try to place biomes above the world height or below bedrock!
                            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) {
                                continue;
                            }

                            LevelChunkSection section = chunk.getSections()[sectionIndex];

                            PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();

                            // Convert world coordinates to chunk-local section coordinates (0-3)
                            int sectionX = (targetPos.getX() >> 2) & 3;
                            int sectionY = (targetPos.getY() >> 2) & 3;
                            int sectionZ = (targetPos.getZ() >> 2) & 3;

                            biomes.set(sectionX, sectionY, sectionZ, newBiomeHolder.get());

                            chunk.markUnsaved();
                            modifiedChunks.add(chunk); // Add to our list to update the clients later
                        }
                    }
                }

                // 5. Broadcast network packets ONLY for the chunks we actually touched
                if (level instanceof ServerLevel serverLevel) {
                    for (LevelChunk modifiedChunk : modifiedChunks) {
                        ClientboundChunksBiomesPacket biomePacket =
                                ClientboundChunksBiomesPacket.forChunks(java.util.List.of(modifiedChunk));

                        serverLevel.getChunkSource().chunkMap.getPlayers(modifiedChunk.getPos(), false).forEach(serverPlayer -> serverPlayer.connection.send(biomePacket));
                    }
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

        // Grab the player to check their item mode
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack mainHand = mc.player.getMainHandItem();
        int mode = mainHand.getOrDefault(EXTRACTOR_SIZE.get(), 0);

        // Dynamic radius's based on the item
        int yRadius = (mode > 0) ? 1 : 0;

        Vec3 camPos = camera.position();
        int baseBiomeX = (Mth.floor(camPos.x) >> 2) << 2;
        int baseBiomeY = (Mth.floor(camPos.y) >> 2) << 2;
        int baseBiomeZ = (Mth.floor(camPos.z) >> 2) << 2;

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

                    ShapeRenderer.renderShape(
                            poseStack, buffer, Shapes.create(0.0, 0.0, 0.0, 4.0, 4.0, 4.0),
                            0.0, 0.0, 0.0, boxColor, boxThickness
                    );
                    poseStack.popPose();
                }
            }
        }
    }

    // --- STEP 5: THE NETWORK HANDLER (Cycling the Size) ---
    public static void handleCycleSize(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();

        // Check if they are actually holding the Extractor
        if (mainHand.has(STORED_BIOME.get())) {

            // Get the current mode (default to 0)
            int currentMode = mainHand.getOrDefault(EXTRACTOR_SIZE.get(), 0);

            // Math trick: Modulo 3 means it will cycle: 0 -> 1 -> 2 -> 0 -> 1...
            int newMode = (currentMode + 1) % 3;

            // Save the new mode to the item
            mainHand.set(EXTRACTOR_SIZE.get(), newMode);

            // Determine the text to show the player
            String sizeText = switch (newMode) {
                case 0 -> "Small (4x4x4)";
                case 1 -> "Medium (12x12x12)";
                case 2 -> "Large (20x20x12)";
                default -> "Unknown";
            };

            // 26.1 Mapping: displayClientMessage is now sendSystemMessage
            player.sendSystemMessage(
                    Component.literal("Transplant Size: " + sizeText)
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                    true
            );
        }
    }
}