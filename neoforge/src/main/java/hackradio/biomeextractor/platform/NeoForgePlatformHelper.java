package hackradio.biomeextractor.platform;

import hackradio.biomeextractor.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {

        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        // 1.21.1 uses a direct static call!
        return !net.neoforged.fml.loading.FMLLoader.isProduction();
    }
}