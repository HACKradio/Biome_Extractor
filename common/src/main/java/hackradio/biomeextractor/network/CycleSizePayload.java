package hackradio.biomeextractor.network; // Adjust this to your actual package

import hackradio.biomeextractor.BiomeExtractorCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// A Java Record is the modern way to define packets in 1.21+
public record CycleSizePayload() implements CustomPacketPayload {

    // 26.1 Mapping: ResourceLocation.fromNamespaceAndPath
    public static final Type<CycleSizePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BiomeExtractorCommon.MOD_ID, "cycle_size"));

    // Since our packet carries no data, we use StreamCodec.unit() to create an empty codec
    public static final StreamCodec<RegistryFriendlyByteBuf, CycleSizePayload> CODEC =
            StreamCodec.unit(new CycleSizePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}