package hackradio.biomeextractor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class BiomeExtractorCommon {

    public static final String MOD_ID = "biomeextractor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Supplier<DataComponentType<String>> STORED_BIOME;
    public static Supplier<DataComponentType<Integer>> EXTRACTOR_SIZE;

    public static void init(IRegistryHelper registryHelper) {
        STORED_BIOME = registryHelper.registerBiomeComponent("stored_biome");
        EXTRACTOR_SIZE = registryHelper.registerIntegerComponent("extractor_size");
        LOGGER.info("Biome Extractor Common Logic Initialized!");
        BiomeExtractorConfig.load();
    }

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
                // --- CONFIG CHECK 1: The Smart Whitelist ---
                if (BiomeExtractorConfig.INSTANCE.useWhitelist) {
                    boolean isAllowed = false;
                    for (String entry : BiomeExtractorConfig.INSTANCE.allowedBlocks) {
                        if (entry.startsWith("#")) {
                            var tagKey = TagKey.create(Registries.BLOCK, Identifier.parse(entry.substring(1)));
                            if (state.is(tagKey)) {
                                isAllowed = true;
                                break;
                            }
                        } else {
                            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                            if (blockId.equals(entry)) {
                                isAllowed = true;
                                break;
                            }
                        }
                    }
                    if (!isAllowed) return true;
                }

                // --- CONFIG CHECK 2: Proper Tool Check ---
                if (BiomeExtractorConfig.INSTANCE.requireCorrectTool) {
                    boolean isRightTool = false;
                    if (state.is(BlockTags.MINEABLE_WITH_PICKAXE) && heldItem.is(ItemTags.PICKAXES)) isRightTool = true;
                    else if (state.is(BlockTags.MINEABLE_WITH_SHOVEL) && heldItem.is(ItemTags.SHOVELS)) isRightTool = true;
                    else if (state.is(BlockTags.MINEABLE_WITH_AXE) && heldItem.is(ItemTags.AXES)) isRightTool = true;
                    else if (state.is(BlockTags.MINEABLE_WITH_HOE) && heldItem.is(ItemTags.HOES)) isRightTool = true;

                    if (!isRightTool) return true;
                }

                // --- SUCCESS LOGIC ---
                var biomeHolder = level.getBiome(pos);
                String biomeId = biomeHolder.unwrapKey()
                        .map(key -> key.identifier().toString())
                        .orElse("minecraft:plains");

                ItemStack droppedBlock = new ItemStack(state.getBlock());
                droppedBlock.set(STORED_BIOME.get(), biomeId);
                Block.popResource(level, pos, droppedBlock);

                // Return TRUE to let the event finish natively.
                // We do NOT cancel the vanilla drop here so other mods can properly process the interaction.
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
            var biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
            assert biomeId != null;

            var newBiomeKey = ResourceKey.create(Registries.BIOME, Identifier.parse(biomeId));
            var newBiomeHolder = biomeRegistry.get(newBiomeKey);

            if (newBiomeHolder.isPresent()) {
                int mode = itemInHand.getOrDefault(EXTRACTOR_SIZE.get(), 0);
                int yRadius = (mode > 0) ? 1 : 0;
                java.util.Set<LevelChunk> modifiedChunks = new java.util.HashSet<>();

                for (int dx = -mode; dx <= mode; dx++) {
                    for (int dy = -yRadius; dy <= yRadius; dy++) {
                        for (int dz = -mode; dz <= mode; dz++) {
                            BlockPos targetPos = placedPos.offset(dx * 4, dy * 4, dz * 4);
                            LevelChunk chunk = level.getChunkAt(targetPos);
                            int sectionIndex = chunk.getSectionIndex(targetPos.getY());

                            if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;

                            LevelChunkSection section = chunk.getSections()[sectionIndex];
                            PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();

                            int sectionX = (targetPos.getX() >> 2) & 3;
                            int sectionY = (targetPos.getY() >> 2) & 3;
                            int sectionZ = (targetPos.getZ() >> 2) & 3;

                            biomes.set(sectionX, sectionY, sectionZ, newBiomeHolder.get());

                            chunk.markUnsaved();
                            modifiedChunks.add(chunk);
                        }
                    }
                }

                if (level instanceof ServerLevel serverLevel) {
                    for (LevelChunk modifiedChunk : modifiedChunks) {
                        ClientboundChunksBiomesPacket biomePacket = ClientboundChunksBiomesPacket.forChunks(List.of(modifiedChunk));
                        serverLevel.getChunkSource().chunkMap.getPlayers(modifiedChunk.getPos(), false).forEach(serverPlayer -> serverPlayer.connection.send(biomePacket));
                    }
                }
            }
        }
        return InteractionResult.PASS;
    }

    // --- STEP 3: THE TOOLTIP (26.2 Updated) ---
    public static void appendBiomeTooltip(ItemStack stack, List<Component> tooltipLines) {
        if (stack.has(STORED_BIOME.get())) {
            String biomeId = stack.get(STORED_BIOME.get());
            tooltipLines.add(
                    Component.translatable("tooltip.biomeextractor.stored", Objects.requireNonNull(biomeId))
                            .withStyle(Style.EMPTY.withColor(TextColor.parseColor("#55FF55").getOrThrow()))
            );
        }
    }

    // --- STEP 4: THE MATRIX (Bulletproof 3D Rendering - Updated for 26.2) ---
    public static void renderBiomeGrid(PoseStack poseStack, Camera camera, MultiBufferSource bufferSource) {
        if (!showBiomeGrid) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack mainHand = mc.player.getMainHandItem();
        int mode = mainHand.getOrDefault(EXTRACTOR_SIZE.get(), 0);
        int yRadius = (mode > 0) ? 1 : 0;

        Vec3 camPos = camera.position();
        int baseBiomeX = (Mth.floor(camPos.x) >> 2) << 2;
        int baseBiomeY = (Mth.floor(camPos.y) >> 2) << 2;
        int baseBiomeZ = (Mth.floor(camPos.z) >> 2) << 2;

        // Grab the active transformation matrix
        Matrix4f matrix = poseStack.last().pose();

        // FETCH THE CONSUMER OUTSIDE THE NESTED LOOPS
        // We bind directly to RenderType.lines() via the core buffer source provider context.
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());

        for (int x = -mode; x <= mode; x++) {
            for (int y = -yRadius; y <= yRadius; y++) {
                for (int z = -mode; z <= mode; z++) {

                    // 1. Calculate relative coordinates offset by the camera
                    float minX = (float) (baseBiomeX + (x * 4) - camPos.x);
                    float minY = (float) (baseBiomeY + (y * 4) - camPos.y);
                    float minZ = (float) (baseBiomeZ + (z * 4) - camPos.z);
                    float maxX = minX + 4.0f;
                    float maxY = minY + 4.0f;
                    float maxZ = minZ + 4.0f;

                    // 2. Set colors
                    boolean isCenterBox = (x == 0 && y == 0 && z == 0);
                    int r = isCenterBox ? 255 : 128;
                    int g = 0;
                    int b = 255;
                    int a = isCenterBox ? 255 : 153;

                    // 3. Let Minecraft's VoxelShape calculate the edges for us!
                    VoxelShape shape = Shapes.create(minX, minY, minZ, maxX, maxY, maxZ);
                    shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
                        // Calculate standard normals
                        float dx = (float)(x2 - x1);
                        float dy = (float)(y2 - y1);
                        float dz = (float)(z2 - z1);
                        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                        dx /= length;
                        dy /= length;
                        dz /= length;

                        // 26.2 FIX: Manually apply the PoseStack matrix to the coordinates!
                        Vector3f v1 = new Vector3f((float)x1, (float)y1, (float)z1);
                        Vector3f v2 = new Vector3f((float)x2, (float)y2, (float)z2);
                        matrix.transformPosition(v1);
                        matrix.transformPosition(v2);

                        // Push the mathematically transformed vertices directly to the GPU buffer.
                        buffer.addVertex(v1.x(), v1.y(), v1.z()).setColor(r, g, b, a).setNormal(dx, dy, dz);
                        buffer.addVertex(v2.x(), v2.y(), v2.z()).setColor(r, g, b, a).setNormal(dx, dy, dz);
                    });
                }
            }
        }
    }


    // --- STEP 5: THE NETWORK HANDLER (26.2 Updated) ---
    public static void handleCycleSize(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();

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

            player.sendSystemMessage(
                    Component.literal("Transplant Size: " + sizeText)
                            .withStyle(Style.EMPTY
                                    .withColor(TextColor.parseColor("#55FFFF").getOrThrow())
                                    .withBold(true)
                            ),
                    true
            );
        }
    }
}