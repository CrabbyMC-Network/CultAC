package ac.cult.cultac.codec;

import com.google.common.collect.Range;
import com.viaversion.viabackwards.ViaBackwards;
import com.viaversion.viabackwards.ViaBackwardsConfig;
import com.viaversion.viabackwards.api.ViaBackwardsPlatform;
import com.viaversion.viabackwards.api.data.TranslatableMappings;
import com.viaversion.viabackwards.protocol.registration.BackwardsRegistrations;
import com.viaversion.viaversion.ViaManagerImpl;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.data.MappingDataLoader;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.commands.ViaCommandHandler;
import com.viaversion.viaversion.platform.NoopInjector;
import com.viaversion.viaversion.protocols.base.v1_16.ClientboundBaseProtocol1_16;
import com.viaversion.viaversion.protocols.base.v1_7.ServerboundBaseProtocol1_7;
import java.io.File;
import java.util.logging.Logger;

/** Only the configuration/PLAY conversion paths between 1.21.3 and 26.3 are registered. */
final class ModernProtocols {
    private static final String[][] EDGES = {
        {"1_21_2", "1_21_4"}, {"1_21_4", "1_21_5"}, {"1_21_5", "1_21_6"},
        {"1_21_6", "1_21_7"}, {"1_21_7", "1_21_9"}, {"1_21_9", "1_21_11"},
        {"1_21_11", "26_1"}, {"26_1", "26_2"}, {"26_2", "26_3"}
    };

    static ViaManagerImpl open(File directory) throws ReflectiveOperationException {
        var platform = new CodecPlatform(directory);
        var manager =
                new ViaManagerImpl(platform, new NoopInjector(), new ViaCommandHandler(false), ViaPlatformLoader.NOOP);
        Via.init(manager);
        try {
            manager.getConfigurationProvider().register(platform.getConf());
            MappingDataLoader.loadGlobalIdentifiers();
            var protocols = manager.getProtocolManager();
            var base = protocols.getBaseProtocol();
            base.initialize();
            base.register(manager.getProviders());
            protocols.registerBaseProtocol(
                    Direction.CLIENTBOUND, new ClientboundBaseProtocol1_16(), Range.atLeast(ProtocolVersion.v1_21_2));
            protocols.registerBaseProtocol(
                    Direction.SERVERBOUND, new ServerboundBaseProtocol1_7(), Range.atLeast(ProtocolVersion.v1_21_2));
            // Upstream's StructuredDataKey/VersionedTypes initializers are circular.
            // Its normal full registration initializes keys first; retain that order.
            Class.forName("com.viaversion.viaversion.api.minecraft.data.StructuredDataKey");
            // The preceding edge supplies the 1.21.2 component/particle codec fillers.
            protocols.registerProtocol(
                    new com.viaversion.viaversion.protocols.v1_21to1_21_2.Protocol1_21To1_21_2(),
                    ProtocolVersion.v1_21_2,
                    ProtocolVersion.v1_21);
            for (int i = 0; i < EDGES.length; i++) {
                String old = EDGES[i][0], newer = EDGES[i][1];
                protocols.registerProtocol(
                        instantiate("com.viaversion.viaversion.protocols.v" + old + "to" + newer + ".Protocol" + old
                                + "To" + newer),
                        ProtocolVersion.getProtocol(769 + i),
                        ProtocolVersion.getProtocol(768 + i));
            }
            var backwards = new ViaBackwardsPlatform() {
                @Override
                public Logger getLogger() {
                    return platform.getLogger();
                }

                @Override
                public void disable() {
                    throw new IllegalStateException("Private codecs unavailable");
                }

                @Override
                public File getDataFolder() {
                    return directory;
                }
            };
            var config = new ViaBackwardsConfig(new File(directory, "backwards.yml"), platform.getLogger());
            config.reload();
            manager.getConfigurationProvider().register(config);
            ViaBackwards.init(backwards, config);
            TranslatableMappings.loadTranslatables();
            BackwardsRegistrations.apply();
            for (int i = 0; i < EDGES.length; i++) {
                String old = EDGES[i][0], newer = EDGES[i][1];
                protocols.registerProtocol(
                        instantiate("com.viaversion.viabackwards.protocol.v" + newer + "to" + old + ".Protocol" + newer
                                + "To" + old),
                        ProtocolVersion.getProtocol(768 + i),
                        ProtocolVersion.getProtocol(769 + i));
            }
            // Wait through the registered futures rather than running plugin startup tasks.
            for (var protocol : protocols.getProtocols()) {
                protocols.completeMappingDataLoading(protocol.getClass());
            }
            protocols.checkForMappingCompletion(true);
            return manager;
        } catch (ReflectiveOperationException | RuntimeException | Error failure) {
            manager.destroy();
            throw failure;
        }
    }

    private static Protocol<?, ?, ?, ?> instantiate(String name) throws ReflectiveOperationException {
        return (Protocol<?, ?, ?, ?>) Class.forName(name).getConstructor().newInstance();
    }
}
