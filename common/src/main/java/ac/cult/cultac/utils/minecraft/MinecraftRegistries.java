package ac.cult.cultac.utils.minecraft;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.ResourceProvider;

/** The native registry and known-pack resources used to decode one client connection. */
public final class MinecraftRegistries {
    private final Supplier<? extends RegistryAccess> access;
    private final Supplier<? extends ResourceProvider> resources;

    public MinecraftRegistries(
            Supplier<? extends RegistryAccess> access, Supplier<? extends ResourceProvider> resources) {
        this.access = Objects.requireNonNull(access);
        this.resources = Objects.requireNonNull(resources);
    }

    public RegistryAccess access() {
        return Objects.requireNonNull(access.get(), "Connection registries unavailable");
    }

    public ResourceProvider resources() {
        return Objects.requireNonNull(resources.get(), "Known-pack resources unavailable");
    }
}
