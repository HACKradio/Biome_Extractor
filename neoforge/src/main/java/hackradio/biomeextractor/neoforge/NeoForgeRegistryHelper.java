package hackradio.biomeextractor.neoforge;

import com.mojang.serialization.Codec;
import hackradio.biomeextractor.BiomeExtractorCommon;
import hackradio.biomeextractor.IRegistryHelper;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class NeoForgeRegistryHelper implements IRegistryHelper {

    // Creates the specific registry for Data Components
    public static final DeferredRegister.DataComponents DATA_COMPONENT_TYPES =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BiomeExtractorCommon.MOD_ID);

    // NeoForge's waiting list for data components
    private final DeferredRegister<DataComponentType<?>> componentRegister;

    public NeoForgeRegistryHelper(String modId) {
        this.componentRegister = DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, modId);
    }

    // We need a way to hand this waiting list back to the main class later
    public DeferredRegister<DataComponentType<?>> getRegister() {
        return componentRegister;
    }

    @Override
    public Supplier<DataComponentType<String>> registerBiomeComponent(String name) {
        // NeoForge's .register() method conveniently returns the exact Supplier our interface demands!
        return componentRegister.register(name, () ->
                DataComponentType.<String>builder().persistent(Codec.STRING).build()
        );
    }
    // Add this inside your NeoForge RegistryHelper class
    @Override
    public Supplier<DataComponentType<Integer>> registerIntegerComponent(String name) {
        // Uses the specialized builder for NeoForge Data Components
        return DATA_COMPONENT_TYPES.registerComponentType(name, builder ->
                builder.persistent(Codec.INT)
                        .networkSynchronized(ByteBufCodecs.VAR_INT)
        );
    }
}