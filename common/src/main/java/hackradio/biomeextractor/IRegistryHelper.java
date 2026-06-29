package hackradio.biomeextractor;

import net.minecraft.core.component.DataComponentType;
import java.util.function.Supplier;

public interface IRegistryHelper {

    // Your existing string component
    Supplier<DataComponentType<String>> registerBiomeComponent(String name);

    // NEW: The integer component for our size cycles!
    Supplier<DataComponentType<Integer>> registerIntegerComponent(String name);
}