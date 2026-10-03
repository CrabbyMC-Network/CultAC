package ac.cult.cultac.network;

import ac.cult.cultac.bedrock.replay.offline.OfflineCultTestBootstrap;
import ac.cult.cultac.utils.minecraft.IsolatedMinecraft;

/** Offline packet actions use the same startup service and registry context as the plugin. */
final class VanillaActionFixture implements AutoCloseable {
    private final AutoCloseable server;

    VanillaActionFixture() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        var base = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        var registries = new net.minecraft.core.RegistryAccess.ImmutableRegistryAccess(java.util.stream.Stream.concat(
                        base.registries(),
                        OfflineCultTestBootstrap.vanillaRegistries().registries()))
                .freeze();
        server = OfflineCultTestBootstrap.withServerRegistries(registries);
        try {
            IsolatedMinecraft.start();
        } catch (Exception | Error failure) {
            server.close();
            throw failure;
        }
    }

    @Override
    public void close() throws Exception {
        try {
            IsolatedMinecraft.stop();
        } finally {
            server.close();
        }
    }
}
