package hackradio.biomeextractor;

import net.minecraft.core.component.DataComponentType;
import java.util.function.Supplier;

public interface IRegistryHelper {
    // Defines the rule: any loader using this helper MUST know how to register a data component.
    Supplier<DataComponentType<String>> registerBiomeComponent(String name);
}