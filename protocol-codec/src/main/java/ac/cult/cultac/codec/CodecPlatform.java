package ac.cult.cultac.codec;

import com.viaversion.viaversion.configuration.AbstractViaConfig;
import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform;
import java.io.File;
import java.util.logging.Logger;

/** Private codec configuration; no proxy injection, online-player registry or update jobs. */
final class CodecPlatform extends UserConnectionViaVersionPlatform {
    CodecPlatform(File directory) {
        super(directory);
    }

    @Override
    public String getPlatformName() {
        return "CultAC packet model";
    }

    @Override
    public String getPlatformVersion() {
        return "1";
    }

    @Override
    public Logger createLogger(String name) {
        return Logger.getLogger("CultAC-codecs");
    }

    @Override
    protected AbstractViaConfig createConfig() {
        return new AbstractViaConfig(null, Logger.getLogger("CultAC-codecs")) {
            @Override
            public void reload() {}

            @Override
            public boolean isCheckForUpdates() {
                return false;
            }
        };
    }
}
