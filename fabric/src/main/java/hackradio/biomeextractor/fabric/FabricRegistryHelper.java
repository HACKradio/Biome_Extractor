package hackradio.biomeextractor.fabric;

import com.mojang.serialization.Codec;
import hackradio.biomeextractor.BiomeExtractorCommon;
import hackradio.biomeextractor.IRegistryHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public class FabricRegistryHelper implements IRegistryHelper {
    private final String modId;

    public FabricRegistryHelper(String modId) {
        this.modId = modId;
    }

    @Override
    public Supplier<DataComponentType<String>> registerBiomeComponent(String name) {
        // Registers the data component using Fabric's native system and the modern Identifier mapping
        DataComponentType<String> component = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(modId, name),
                DataComponentType.<String>builder().persistent(Codec.STRING).build()
        );

        return () -> component;
    }
    // Add this inside your Fabric RegistryHelper class
    @Override
    public Supplier<DataComponentType<Integer>> registerIntegerComponent(String name) {
        var component = DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                .build();

        // Assuming you are registering it directly to the vanilla registry
        var registered = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(BiomeExtractorCommon.MOD_ID, name),
                component
        );

        return () -> registered;
    }
}