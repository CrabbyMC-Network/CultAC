package ac.cult.cultac.utils.minecraft;

import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.data.ModelBlockStates;
import ac.cult.cultac.protocol.data.ModelRegistryNames;
import ac.cult.cultac.utils.anticheat.LogUtil;
import ac.cult.placement.PlacementRuntime;
import ac.cult.placement.api.GeometryTags;
import ac.cult.runtime.RuntimeModel;
import java.nio.file.Path;
import net.minecraft.world.level.block.Block;

/** Owns the acquired model services and each service's directed native-value boundary. */
// TODO: What the fuck is codex doing with the if (bedrock) return; hacks
public final class IsolatedMinecraft {
    private static volatile Binding primary;
    private static volatile Binding earlierActions;
    private static volatile GeometryTags nativeTags;

    private IsolatedMinecraft() {}

    /** All values in one action must use the same model, ID maps and tag snapshot. */
    public static final class Binding {
        private final PlacementRuntime runtime;
        private final ModelBlockStates states;
        private final ModelRegistryNames names;
        private volatile GeometryTags tags;

        private Binding(
                PlacementRuntime runtime, ModelBlockStates states, ModelRegistryNames names, GeometryTags tags) {
            this.runtime = runtime;
            this.states = states;
            this.names = names;
            this.tags = tags;
        }

        public PlacementRuntime runtime() {
            return runtime;
        }

        public int toModelState(int hostId) {
            return states.toModel(hostId);
        }

        public int toHostState(int modelId) {
            return states.toHost(modelId);
        }

        public String toModelItem(String hostName) {
            return names.modelItem(hostName);
        }

        public String toHostItem(String modelName) {
            return names.hostItem(modelName);
        }

        public String dimensionType(String json) {
            return ModelDimensions.project(json, states.source(), states.target());
        }

        public GeometryTags tagsFor(CultPlayer player) {
            return player.registryState == null
                    ? tags
                    : player.registryState.geometryTags(player.user.registries(), names);
        }
    }

    public static synchronized void start() {
        if (primary != null) throw new IllegalStateException("Isolated Minecraft already started");
        var host = ProtocolVersion.of(net.minecraft.SharedConstants.getProtocolVersion());
        var model = RuntimeModel.forBackendProtocol(host.protocol());
        Binding opened = null, earlier = null;
        try {
            opened = open(host, model, false);
            // A newer backing registry cannot give an older client new action semantics.
            // Acquire both supported families before accepting players, avoiding a download
            // or vanilla bootstrap on the first old-client action.
            if (model != RuntimeModel.JAVA_1_21_11) earlier = open(host, RuntimeModel.JAVA_1_21_11, true);
            earlierActions = earlier;
            primary = opened;
        } catch (Exception | Error failure) {
            close(earlier, failure);
            close(opened, failure);
            throw new IllegalStateException("Unable to acquire or start vanilla runtime", failure);
        }
    }

    private static Binding open(ProtocolVersion host, RuntimeModel model, boolean clientProjection) throws Exception {
        var target = ProtocolVersion.of(model.protocol());
        var mapped = clientProjection ? ModelBlockStates.project(host, target) : ModelBlockStates.load(host, target);
        var mappedNames =
                clientProjection ? ModelRegistryNames.project(host, target) : ModelRegistryNames.load(host, target);
        if (mapped.sourceCount() != Block.BLOCK_STATE_REGISTRY.size())
            throw new IllegalStateException("Host block registry does not match its pinned protocol data");
        if (!PlacementRuntime.available(model))
            throw new IllegalStateException("Missing acquisition metadata for " + model.minecraftId());
        PlacementRuntime opened = null;
        try {
            opened = PlacementRuntime.openVanilla(
                    Path.of(PlacementRuntime.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()),
                    ac.cult.cultac.CultAPI.INSTANCE
                            .getGrimPlugin()
                            .getDataFolder()
                            .toPath()
                            .resolve("runtime"),
                    model);
            if (opened.stateCount() != mapped.targetCount())
                throw new IllegalStateException(
                        "Acquired model block registry does not match its pinned protocol data");
            var captured = NativeGeometryTags.capture(mappedNames);
            opened.tags(captured);
            opened.prepareInteractions();
            LogUtil.info("Isolated vanilla " + model.minecraftId() + " "
                    + (clientProjection ? "older-client action" : "placement and geometry")
                    + " runtime started: " + opened.stateCount() + " states");
            return new Binding(opened, mapped, mappedNames, captured);
        } catch (Exception | Error failure) {
            if (opened != null) {
                try {
                    opened.close();
                } catch (Exception cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    public static synchronized void stop() {
        var current = primary;
        var earlier = earlierActions;
        primary = null;
        earlierActions = null;
        nativeTags = null;
        close(earlier, null);
        close(current, null);
    }

    private static void close(Binding binding, Throwable failure) {
        if (binding == null) return;
        try {
            binding.runtime.close();
        } catch (java.io.IOException cleanup) {
            if (failure == null) LogUtil.error("Unable to close vanilla runtime", cleanup);
            else failure.addSuppressed(cleanup);
        }
    }

    private static Binding compatible(CultPlayer player) {
        var current = primary;
        return player != null
                        && !player.isBedrockMovement()
                        && current != null
                        && player.getClientVersion().getProtocolVersion() >= ProtocolVersion.V1_21_3.protocol()
                        && player.getClientVersion().getProtocolVersion()
                                <= current.states.target().protocol()
                ? current
                : null;
    }

    /** Geometry retains its native host/model boundary. Actions select their own whole binding. */
    public static PlacementRuntime forPlayer(CultPlayer player) {
        var current = compatible(player);
        return current == null ? null : current.runtime;
    }

    public static Binding actionsFor(CultPlayer player) {
        // Geyser's native action observer used the host block-action implementation
        // before it moved into the isolated runtime. Keep that action binding separate
        // from Java movement geometry and Java client-version projections.
        if (player != null && player.isBedrockMovement()) return primary;
        var current = compatible(player);
        var earlier = earlierActions;
        return current != null
                        && earlier != null
                        && player.getClientVersion().getProtocolVersion() <= RuntimeModel.JAVA_1_21_11.protocol()
                ? earlier
                : current;
    }

    private static Binding requirePrimary() {
        var current = primary;
        if (current == null) throw new IllegalStateException("Isolated Minecraft is not running");
        return current;
    }

    public static int toModelState(int hostId) {
        return requirePrimary().toModelState(hostId);
    }

    public static int toHostState(int modelId) {
        return requirePrimary().toHostState(modelId);
    }

    public static String toModelItem(String hostName) {
        return requirePrimary().toModelItem(hostName);
    }

    public static String toHostItem(String modelName) {
        return requirePrimary().toHostItem(modelName);
    }

    public static GeometryTags tags() {
        return requirePrimary().tags;
    }

    public static GeometryTags tagsFor(CultPlayer player) {
        return requirePrimary().tagsFor(player);
    }

    /** Bind each model to the same native generation using its own directed name map. */
    public static void tags(GeometryTags tags) {
        if (nativeTags == tags) return;
        installTags(primary, tags);
        installTags(earlierActions, tags);
        nativeTags = tags;
    }

    private static void installTags(Binding binding, GeometryTags tags) {
        if (binding == null) return;
        var mapped = NativeGeometryTags.translate(tags, binding.names);
        binding.runtime.tags(mapped);
        binding.tags = mapped;
    }
}
