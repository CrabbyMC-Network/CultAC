package ac.cult.cultac.bridge.geyser;

import ac.cult.cultac.bridge.wire.InventoryDeltaMessage;
import ac.cult.cultac.bridge.wire.NamedValueCodec;
import ac.cult.cultac.bridge.wire.InventoryFragments;
import java.util.*;
import java.util.function.Consumer;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

/** Reports only changes the stock Geyser action translator accepted into its cache. */
final class GatewayInventoryActions {
    private GatewayInventoryActions() { }
    static void translate(GeyserSession session, BedrockPacket packet, Runnable delegate, Consumer<byte[]> emit) {
        if (packet instanceof NetworkStackLatencyPacket || packet instanceof MovePlayerPacket
                || packet instanceof PlayerAuthInputPacket input && input.getItemStackRequest() == null) {
            delegate.run(); return;
        }
        var inventory = session.getPlayerInventory(); var holder = session.getInventoryHolder();
        var menu = holder == null ? null : holder.inventory();
        var before = snapshot(inventory); var menuBefore = menu == null ? null : snapshot(menu);
        var cursor = inventory.getCursor().copy().getItemStack();
        delegate.run();
        var changes = new ArrayList<InventoryDeltaMessage.Slot>();
        changes(session, 0, before, inventory, changes);
        if (menu != null && session.getInventoryHolder() == holder) changes(session, menu.getJavaId(), menuBefore, menu, changes);
        // Java selected-slot, open/close, use and release packets retain their existing backend handlers.
        // Only cache values absent from those packets cross this additional observation channel.
        for (var changed : changes) InventoryFragments.emit(new InventoryDeltaMessage(List.of(changed), false, null).encode(), emit);
        var afterCursor = inventory.getCursor().copy().getItemStack();
        if (!Objects.equals(cursor, afterCursor)) InventoryFragments.emit(new InventoryDeltaMessage(List.of(), true, item(session, afterCursor)).encode(), emit);
    }
    private static ItemStack[] snapshot(Inventory inventory) {
        var values = new ItemStack[inventory.getSize()];
        for (int n = 0; n < values.length; n++) values[n] = inventory.getItem(n).copy().getItemStack();
        return values;
    }
    private static void changes(GeyserSession session, int window, ItemStack[] before, Inventory inventory, List<InventoryDeltaMessage.Slot> changes) {
        for (int n = 0; n < before.length; n++) {
            var after = inventory.getItem(n).copy().getItemStack();
            if (!Objects.equals(before[n], after)) changes.add(new InventoryDeltaMessage.Slot(window, n, item(session, after)));
        }
    }
    static InventoryDeltaMessage.Item item(GeyserSession session, ItemStack source) {
        if (source == null || source.getAmount() <= 0) return null;
        var mapping = session.getItemMappings().getMapping(source.getId()).getJavaItem();
        if (mapping.javaId() != source.getId()) throw new IllegalArgumentException("Unknown item mapping");
        var components = new ArrayList<InventoryDeltaMessage.Component>();
        if (source.getDataComponentsPatch() != null) source.getDataComponentsPatch().getDataComponents().forEach((type, component) -> {
            String name = type.getKey().asString();
            Object value;
            if (component.getValue() == null) value = null;
            else if (name.equals("minecraft:creative_slot_lock")) value = true;
            else if (name.equals("minecraft:additional_trade_cost") || name.equals("minecraft:map_post_processing")) value = component.getValue();
            else value = GatewayComponentValues.serialize(session.getRegistryCache(), component);
            components.add(new InventoryDeltaMessage.Component(name, component.getValue() == null, NamedValueCodec.encode(value)));
        });
        return new InventoryDeltaMessage.Item(mapping.javaIdentifier(), source.getAmount(), components);
    }
}

