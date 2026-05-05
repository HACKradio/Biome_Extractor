package hackradio.biomeextractor.neoforge;

import hackradio.biomeextractor.IRegistryHelper;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class NeoForgeRegistryHelper implements IRegistryHelper {

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
}