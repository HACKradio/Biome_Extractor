package hackradio.biomeextractor.network;

import hackradio.biomeextractor.BiomeExtractorCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CycleSizePayload() implements CustomPacketPayload {

    public static final Type<CycleSizePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(BiomeExtractorCommon.MOD_ID, "cycle_size"));
    public static final StreamCodec<FriendlyByteBuf, CycleSizePayload> CODEC = StreamCodec.unit(new CycleSizePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}