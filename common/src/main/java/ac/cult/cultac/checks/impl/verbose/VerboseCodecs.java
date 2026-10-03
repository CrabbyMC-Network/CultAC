package ac.cult.cultac.checks.impl.verbose;

import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.value.PlayerAction;
import ac.cult.cultac.protocol.value.PlayerCommandAction;
import ac.cult.cultac.utils.nmsutil.NmsIdentifierUtil;
import ac.grim.grimac.api.storage.verbose.VerboseSchema;
import ac.grim.grimac.api.storage.verbose.VerboseTags;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cult's verbose template tags plus the write-side value encoders that pair
 * with them. Registered once; checks declare templates referencing these tag
 * names and write values through the matching {@code VerboseCodecs.x(...)}
 * encoder.
 */
public final class VerboseCodecs {
    /** {@code {packet}} sentinel for "no packet". */
    public static final int PACKET_NONE = Integer.MIN_VALUE;
    /** {@code {packet}} sentinel for transaction/pong. */
    public static final int PACKET_TRANSACTION = Integer.MIN_VALUE + 1;

    static {
        VerboseTags.registerEnum("face", BlockFace.values());
        var version = ProtocolVersion.of(SharedConstants.getProtocolVersion());
        var digging = java.util.Arrays.stream(PlayerAction.values())
                .filter(action ->
                        (action != PlayerAction.CHANGE_DESTROY_DIRECTION || version.atLeast(ProtocolVersion.V26_3))
                                && (action != PlayerAction.STAB || version.atLeast(ProtocolVersion.V1_21_11)))
                .toArray(PlayerAction[]::new);
        VerboseTags.registerEnum("digging", digging);
        VerboseTags.registerEnumLower("digging_lower", digging);
        VerboseTags.registerEnum("clicktype", ac.cult.cultac.utils.inventory.inventory.WindowClickType.values());
        VerboseTags.registerEnumLower(
                "clicktype_lower", ac.cult.cultac.utils.inventory.inventory.WindowClickType.values());
        VerboseTags.registerEnum("entityaction", PlayerCommandAction.values());
        VerboseTags.registerEnum("hand", InteractionHand.values());
        VerboseTags.register(
                "block", List.of(VerboseSchema.TypeTag.ZZ), (in, ctx, out, fmt) -> out.append(blockName(in.rzz())));
        VerboseTags.register(
                "item", List.of(VerboseSchema.TypeTag.ZZ), (in, ctx, out, fmt) -> out.append(itemTypeName(in.rzz())));
        VerboseTags.register(
                "packet", List.of(VerboseSchema.TypeTag.STR), (in, ctx, out, fmt) -> out.append(in.rstr()));
        VerboseTags.register(
                "entity",
                List.of(VerboseSchema.TypeTag.VI),
                (in, ctx, out, fmt) -> out.append(entityTypeName(in.rvi())));
        VerboseTags.register(
                "offset",
                List.of(VerboseSchema.TypeTag.F64),
                (in, ctx, out, fmt) -> out.append(humanFormattedOffset(in.rf64())));
        VerboseTags.register(
                "stdnum",
                List.of(VerboseSchema.TypeTag.F64),
                (in, ctx, out, fmt) -> out.append(formatNumberStandard(in.rf64())));
    }

    private VerboseCodecs() {}

    /**
     * Force tag registration. Call before templates parse and before history
     * rows render; class initialization performs the actual work once.
     */
    public static void ensureRegistered() {
        // Static initializer has run by the time this returns.
    }

    /** Null-safe enum encoding for the {@code registerEnum} family of tags. */
    public static int enumId(@Nullable Enum<?> value) {
        return VerboseTags.enumId(value);
    }

    /** Keep stored action IDs aligned with the server's existing verbose decoder. */
    public static int digging(@Nullable PlayerAction action) {
        return action == null ? 0 : action.wireId(ProtocolVersion.of(SharedConstants.getProtocolVersion())) + 1;
    }

    /**
     * Encoder for {@code {block}}: the protocol-defined per-version block id,
     * so any product following the MC protocol decodes the same name.
     * {@code -1} when the block has no id in the player's version
     * (server-only states render as {@code unknown}).
     */
    public static int block(@NotNull Block type, @NotNull ClientVersion version) {
        return BuiltInRegistries.BLOCK.getId(type);
    }

    /** Encoder for {@code {item}}: the server's built-in registry item id. */
    public static int item(@NotNull Item type, @NotNull ClientVersion version) {
        return BuiltInRegistries.ITEM.getId(type);
    }

    /** Encoder for {@code {entity}}: the server's built-in registry entity-type id. */
    public static int entity(@NotNull EntityType<?> type, @NotNull ClientVersion version) {
        return BuiltInRegistries.ENTITY_TYPE.getId(type);
    }

    private static @NotNull String blockName(int id) {
        if (id < 0) return "unknown";
        Block type = BuiltInRegistries.BLOCK.byId(id);
        return type == null ? "unknown(" + id + ")" : NmsIdentifierUtil.registryKey(BuiltInRegistries.BLOCK, type);
    }

    private static @NotNull String itemTypeName(int id) {
        if (id < 0) return "";
        Item type = BuiltInRegistries.ITEM.byId(id);
        return type == null ? "unknown(" + id + ")" : NmsIdentifierUtil.registryKey(BuiltInRegistries.ITEM, type);
    }

    private static @NotNull String entityTypeName(int entityId) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.byId(entityId);
        return entityType == null
                ? "unknown"
                : NmsIdentifierUtil.registryKey(BuiltInRegistries.ENTITY_TYPE, entityType);
    }

    private static @NotNull String humanFormattedOffset(double offset) {
        String humanFormattedOffset;
        if (offset < 0.001) { // 1.129E-3
            humanFormattedOffset = String.format("%.4E", offset);
            // Squeeze out an extra digit here by E-03 to E-3
            humanFormattedOffset = humanFormattedOffset.replace("E-0", "E-");
        } else {
            // 0.00112945678 -> .001129
            humanFormattedOffset = String.format("%6f", offset);
            // I like the leading zero, but removing it lets us add another digit to the end
            humanFormattedOffset = humanFormattedOffset.replace("0.", ".");
        }
        return humanFormattedOffset;
    }

    private static @NotNull String formatNumberStandard(double value) {
        double abs = Math.abs(value);
        if (abs < 1e-7) return "0";
        String formatted;
        if (abs < 0.001) {
            formatted = String.format("%.4E", value);
            return formatted.replace("E-0", "E-");
        }
        formatted = String.format("%6f", value);
        return formatted.replace("0.", ".");
    }
}
