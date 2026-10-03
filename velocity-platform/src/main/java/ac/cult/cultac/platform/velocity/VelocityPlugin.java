package ac.cult.cultac.platform.velocity;

import ac.grim.grimac.api.plugin.GrimPlugin;
import ac.grim.grimac.api.plugin.GrimPluginDescription;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

final class VelocityPlugin implements GrimPlugin {
    private final File directory;
    private final Logger logger;
    private final GrimPluginDescription description = new GrimPluginDescription() {
        @Override
        public String getVersion() {
            return "0.1.0";
        }

        @Override
        public String getDescription() {
            return "Compensated movement anticheat";
        }

        @Override
        public List<String> getAuthors() {
            return List.of("GrimCult");
        }
    };

    VelocityPlugin(Path directory, org.slf4j.Logger target) {
        this.directory = directory.toFile();
        this.logger = new Logger("CultAC", null) {};
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                int level = record.getLevel().intValue();
                if (level >= 1000) {
                    target.error(record.getMessage(), record.getThrown());
                } else if (level >= 900) {
                    target.warn(record.getMessage(), record.getThrown());
                } else if (level >= 800) {
                    target.info(record.getMessage(), record.getThrown());
                } else {
                    target.debug(record.getMessage(), record.getThrown());
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
    }

    @Override
    public GrimPluginDescription getDescription() {
        return description;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public File getDataFolder() {
        return directory;
    }
}
