package hackradio.biomeextractor.fabric;

import hackradio.biomeextractor.IRegistryHelper;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

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
                ResourceLocation.fromNamespaceAndPath(modId, name),
                DataComponentType.<String>builder().persistent(Codec.STRING).build()
        );

        return () -> component;
    }
}