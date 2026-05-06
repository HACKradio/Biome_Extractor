package hackradio.biomeextractor;

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
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
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

import java.util.BitSet;
import java.util.function.Supplier;

public class BiomeExtractorCommon {

    public static final String MOD_ID = "biomeextractor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // This is a Supplier because NeoForge doesn't hand over the component immediately.
    public static Supplier<DataComponentType<String>> STORED_BIOME;

    /**
     * The Master Switch. Fabric and NeoForge will call this and pass in their specific RegistryHelper.
     */
    public static void init(IRegistryHelper registryHelper) {
        STORED_BIOME = registryHelper.registerBiomeComponent("stored_biome");
        LOGGER.info("Biome Extractor Common Logic Initialized!");
    }

    // The toggle switch for our visualizer
    public static boolean showBiomeGrid = false;

    // --- STEP 1: THE HARVEST (Breaking the block) ---
    public static boolean handleBlockBreak(Level level, Player player, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return true;

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) return true;

        // 26.1 Mapping: Identifier instead of ResourceLocation
        ResourceKey<Enchantment> extractorKey = ResourceKey.create(
                Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath(MOD_ID, "biome_extractor")
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
                        .map(key -> key.identifier().toString())
                        .orElse("minecraft:plains");

                ItemStack droppedBlock = new ItemStack(state.getBlock());

                droppedBlock.set(STORED_BIOME.get(), biomeId);

                // 26.1 Mapping: popResource is often changed to Block.dropResources or similar, but the most stable native method is directly spawning the entity.
                Block.popResource(level, pos, droppedBlock);
                level.destroyBlock(pos, false);

                return false;
            }
        }
        return true;
    }

    // --- STEP 2: THE TRANSPLANT (Placing the block) ---
    public static InteractionResult handleBlockPlace(Player player, Level level, InteractionHand hand, BlockPos placedPos) {
        if (level.isClientSide()) return InteractionResult.PASS;

        ItemStack itemInHand = player.getItemInHand(hand);

        if (itemInHand.has(STORED_BIOME.get())) {
            String biomeId = itemInHand.get(STORED_BIOME.get());

            // 26.1 Mapping: lookupOrThrow
            var biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
            var newBiomeKey = ResourceKey.create(Registries.BIOME, Identifier.parse(biomeId));
            var newBiomeHolder = biomeRegistry.get(newBiomeKey); // get() instead of getHolder()

            if (newBiomeHolder.isPresent()) {
                LevelChunk chunk = level.getChunkAt(placedPos);
                LevelChunkSection section = chunk.getSections()[chunk.getSectionIndex(placedPos.getY())];

                @SuppressWarnings("unchecked")
                PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();

                int sectionX = (placedPos.getX() >> 2) & 3;
                int sectionY = (placedPos.getY() >> 2) & 3;
                int sectionZ = (placedPos.getZ() >> 2) & 3;

                biomes.set(sectionX, sectionY, sectionZ, newBiomeHolder.get());
                chunk.setLoaded(true);

                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.players().forEach(p -> p.connection.send(
                            new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), (BitSet) null, (BitSet) null)
                    ));
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
            tooltipLines.add(Component.translatable("tooltip.biomeextractor.stored", biomeId).withStyle(ChatFormatting.AQUA));
        }
    }

    // --- STEP 4: THE MATRIX (3D Rendering) ---
    public static void renderBiomeGrid(PoseStack poseStack, Camera camera, VertexConsumer buffer) {
        if (!showBiomeGrid) return;

        Vec3 camPos = camera.position();

        // Calculate the absolute world coordinates of the center 4x4x4 biome block
        int baseBiomeX = (Mth.floor(camPos.x) >> 2) << 2;
        int baseBiomeY = (Mth.floor(camPos.y) >> 2) << 2;
        int baseBiomeZ = (Mth.floor(camPos.z) >> 2) << 2;

        // How many layers out do you want to draw? (1 = a 3x3x3 grid of boxes)
        int radius = 1;

        // Loop through the X, Y, and Z axes to draw the neighbors
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {

                    poseStack.pushPose(); // Save the camera's true position

                    // Multiply our loop offset by 4 (the size of a biome chunk) to get the neighbor's coordinate
                    double targetX = baseBiomeX + (x * 4);
                    double targetY = baseBiomeY + (y * 4);
                    double targetZ = baseBiomeZ + (z * 4);

                    // Shift the tripod to this specific box's corner
                    poseStack.translate(targetX - camPos.x, targetY - camPos.y, targetZ - camPos.z);

                    // --- SMART UI ---
                    // Check if this specific box in the loop is the one the player is standing in (offset 0,0,0)
                    boolean isCenterBox = (x == 0 && y == 0 && z == 0);

                    // Center box is full Neon Aqua (FF). Outer boxes are half-transparent (88).
                    int boxColor = isCenterBox ? (int) 0xFF00FFFFL : (int) 0x8800FFFFL;

                    // Center box has bold lines (3.0F). Outer boxes are thin (1.0F).
                    float boxThickness = isCenterBox ? 3.0F : 1.0F;

                    // Draw the 4x4x4 box with our custom style
                    ShapeRenderer.renderShape(
                            poseStack,
                            buffer,
                            Shapes.create(0.0, 0.0, 0.0, 4.0, 4.0, 4.0),
                            0.0, 0.0, 0.0,
                            boxColor,
                            boxThickness
                    );

                    poseStack.popPose(); // Put the tripod back for the next loop!
                }
            }
        }
    }
}