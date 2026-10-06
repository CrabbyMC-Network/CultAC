package ac.cult.cultac.bedrock.bridge;

import ac.cult.cultac.bridge.wire.InventoryDeltaMessage;
import ac.cult.cultac.bridge.wire.NamedValueCodec;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.network.protocol.util.SpigotConversionUtil;
import ac.cult.cultac.utils.nmsutil.NmsIdentifierUtil;
import java.util.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Signed post-translation observations, never an inventory request or a permission bypass. */
final class ProxyBridgeInventoryActions {
    private ProxyBridgeInventoryActions() { }
    static void apply(CultPlayer player, byte[] body) {
        var message = InventoryDeltaMessage.decode(body);
        // Decode fully before mutating, including every named component.
        var windows = new LinkedHashMap<Integer, Map<Integer, org.bukkit.inventory.ItemStack>>();
        for (var slot : message.slots()) windows.computeIfAbsent(slot.window(), x -> new LinkedHashMap<>())
                .put(slot.index(), SpigotConversionUtil.fromNmsItemStack(item(slot.item())));
        var cursor = message.cursorChanged() ? SpigotConversionUtil.fromNmsItemStack(item(message.cursor())) : null;
        windows.forEach((window, values) -> player.getInventory().applyBedrockSlots(window, values));
        if (message.cursorChanged()) player.getInventory().applyBedrockCursor(cursor);
    }
    private static ItemStack item(InventoryDeltaMessage.Item source) {
        if (source == null) return ItemStack.EMPTY;
        var item = NmsIdentifierUtil.registryOptional(BuiltInRegistries.ITEM, source.name()).orElseThrow();
        var stack = new ItemStack(item, source.amount());
        var ops = MinecraftServer.getServer().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var seen = new HashSet<String>();
        for (var component : source.components()) {
            if (!seen.add(component.name())) throw new IllegalArgumentException("Duplicate named item component");
            var type = NmsIdentifierUtil.registryValue(BuiltInRegistries.DATA_COMPONENT_TYPE, component.name());
            if (type == null) throw new IllegalArgumentException("Unknown named item component");
            Object value = NamedValueCodec.decode(component.value());
            if (component.removed()) stack.remove(type);
            else if (component.name().equals("minecraft:creative_slot_lock")) setValue(stack, type, net.minecraft.util.Unit.INSTANCE);
            else if (component.name().equals("minecraft:additional_trade_cost")) setValue(stack, type, (Integer) value);
            else if (component.name().equals("minecraft:map_post_processing")) setValue(stack, type, net.minecraft.world.item.component.MapPostProcessing.ID_MAP.apply((Integer) value));
            else set(stack, type, ops, tag(value));
        }
        return stack;
    }
    private static <T> void set(ItemStack stack, DataComponentType<T> type, com.mojang.serialization.DynamicOps<Tag> ops, Tag value) {
        if (type.codec() == null) throw new IllegalArgumentException("Component has no named codec");
        stack.set(type, type.codec().parse(ops, value).getOrThrow());
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setValue(ItemStack stack, DataComponentType type, Object value) { stack.set(type, value); }
    private static Tag tag(Object value) {
        return switch (value) {
            case String v -> StringTag.valueOf(v); case Boolean v -> ByteTag.valueOf(v); case Byte v -> ByteTag.valueOf(v);
            case Short v -> ShortTag.valueOf(v); case Integer v -> IntTag.valueOf(v); case Long v -> LongTag.valueOf(v);
            case Float v -> FloatTag.valueOf(v); case Double v -> DoubleTag.valueOf(v);
            case byte[] v -> new ByteArrayTag(v); case int[] v -> new IntArrayTag(v); case long[] v -> new LongArrayTag(v);
            case Map<?, ?> v -> { var tag = new CompoundTag(); v.forEach((k, item) -> tag.put((String) k, tag(item))); yield tag; }
            case List<?> v -> { var tag = new ListTag(); v.forEach(item -> tag.add(tag(item))); yield tag; }
            case null -> EndTag.INSTANCE;
            default -> throw new IllegalArgumentException("Unsupported named component");
        };
    }
}
