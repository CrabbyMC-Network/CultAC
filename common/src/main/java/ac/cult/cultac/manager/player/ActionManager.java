package ac.cult.cultac.manager.player;

import ac.cult.cultac.checks.CultProcessor;
import ac.cult.cultac.checks.type.CheckListener;
import ac.cult.cultac.checks.type.ClientTickEndListener;
import ac.cult.cultac.network.CultPacketHandler;
import ac.cult.cultac.network.event.PacketReceiveEvent;
import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.network.protocol.util.SpigotConversionUtil;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.protocol.packet.clientbound.ClientboundSetHeldSlot;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundInteract;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundMovePlayer;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerAction;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundPlayerInput;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundSetCarriedItem;
import ac.cult.cultac.protocol.packet.serverbound.ServerboundUseItem;
import ac.cult.cultac.protocol.value.GameMode;
import ac.cult.cultac.protocol.value.InteractAction;
import ac.cult.cultac.protocol.value.PlayerAction;
import ac.cult.cultac.utils.data.packetentity.PacketEntity;
import lombok.Getter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.equipment.Equippable;

@Getter
public class ActionManager extends CultProcessor implements CheckListener, ClientTickEndListener {
    private static final DataComponentType<?> BLOCKS_ATTACKS_COMPONENT = findDataComponent("BLOCKS_ATTACKS");

    private boolean attacking = false;
    private boolean digging = false;
    private boolean blocking = false;
    private boolean releasing = false;

    private int blockingTicks = 0;

    public long lastAttack = 0;

    public int flyingPacketsSinceAttack;

    public long lastFlyingPacketTime;

    private int targetId = 0;
    private int lastTargetId = 0;

    private PacketEntity target = null;

    public ActionManager(CultPlayer cultPlayer) {
        super(cultPlayer);
    }

    private InteractionHand hand;

    private InteractAction lastAction = null;

    private void handleInteract(ServerboundInteract interact) {
        if (interact != null) {
            if (interact.action() == InteractAction.ATTACK) {
                attack(interact.entityId());
            }
        }
    }

    public void useItem(InteractionHand usedHand) {
        hand = usedHand;
        ItemStack heldStack = player.getInventory().getHandItem(hand);
        if (heldStack == null) heldStack = ItemStack.EMPTY;

        this.blocking = heldStack.getItem() == net.minecraft.world.item.Items.SHIELD
                || (player.getClientVersion().isOlderThan(ClientVersion.V_1_9)
                        && ac.cult.cultac.utils.inventory.ItemUtil.name(heldStack.getItem())
                                .endsWith("_SWORD"));

        if (canStartUsingItem(heldStack)) {
            player.packetStateData.setSlowedByUsingItem(true);
            player.packetStateData.itemInUseHand = hand;
        } else if (!player.packetStateData.isSlowedByUsingItem() || player.packetStateData.itemInUseHand == hand) {
            // A rejected use in the other hand does not stop an existing use.
            player.packetStateData.setSlowedByUsingItem(false);
        }
    }

    private void handlePlayerAction(ServerboundPlayerAction packet) {
        if (packet.action() == PlayerAction.RELEASE_USE_ITEM) {
            releaseItem();
        }
    }

    public void releaseItem() {
        this.releasing = true;
        this.blocking = false;
        player.packetStateData.setSlowedByUsingItem(false);
    }

    public void attack(int entityId) {
        this.lastAction = InteractAction.ATTACK;
        this.lastTargetId = this.targetId;
        this.targetId = entityId;
        this.target = player.compensatedEntities.getEntity(this.targetId);
        this.flyingPacketsSinceAttack = 0;
        this.attacking = true;
        this.lastAttack = System.currentTimeMillis();
    }

    private boolean canStartUsingItem(ItemStack stack) {
        if (stack == null
                || stack.isEmpty()
                || (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)
                        && player.checkManager.getCompensatedCooldown().hasItem(stack))) {
            return false;
        }

        ClientVersion version = player.getClientVersion();
        net.minecraft.world.item.ItemStack nms = SpigotConversionUtil.toNmsItemStack(stack);

        if (version.isNewerThanOrEquals(ClientVersion.V_1_21_4)) {
            if (stack.getItem() == net.minecraft.world.item.Items.GOAT_HORN
                    || stack.getItem() == net.minecraft.world.item.Items.SHIELD
                    || stack.getItem() == net.minecraft.world.item.Items.SPYGLASS) {
                return true;
            }
            if (stack.getItem() == net.minecraft.world.item.Items.BOW
                    || stack.getItem() == net.minecraft.world.item.Items.CROSSBOW) {
                // Baseline deliberately does not infer projectile availability.
                return false;
            }
            if (stack.getItem() == net.minecraft.world.item.Items.TRIDENT) {
                return !nms.nextDamageWillBreak()
                        && ac.cult.cultac.utils.inventory.ItemUtil.enchantmentLevel(
                                        stack, net.minecraft.world.item.enchantment.Enchantments.RIPTIDE)
                                <= 0;
            }

            Consumable consumable = nms.get(DataComponents.CONSUMABLE);
            if (consumable != null) {
                FoodProperties food = nms.get(DataComponents.FOOD);
                return consumable.consumeSeconds() > 0.0F
                        && (food == null
                                || food.canAlwaysEat()
                                || player.food < 20
                                || player.gamemode == GameMode.CREATIVE);
            }

            Equippable equippable = nms.get(DataComponents.EQUIPPABLE);
            return (equippable == null || !equippable.swappable())
                    && BLOCKS_ATTACKS_COMPONENT != null
                    && nms.has(BLOCKS_ATTACKS_COMPONENT);
        }

        // The compact legacy path retained by this fork. These are the baseline
        // items whose use can be proven without guessing projectile inventory.
        net.minecraft.world.item.Item material = stack.getItem();
        // 1.8 ItemSword#onItemRightClick sets a 72000-tick BLOCK use action.
        if (version.isOlderThan(ClientVersion.V_1_9)
                && ac.cult.cultac.utils.inventory.ItemUtil.name(material).endsWith("_SWORD")) return true;
        if (material == net.minecraft.world.item.Items.SHIELD
                || material == net.minecraft.world.item.Items.SPYGLASS
                || material == net.minecraft.world.item.Items.GOAT_HORN) {
            return true;
        }
        if (material == net.minecraft.world.item.Items.TRIDENT) {
            return !nms.nextDamageWillBreak()
                    && ac.cult.cultac.utils.inventory.ItemUtil.enchantmentLevel(
                                    stack, net.minecraft.world.item.enchantment.Enchantments.RIPTIDE)
                            <= 0;
        }
        if (material == net.minecraft.world.item.Items.BOW || material == net.minecraft.world.item.Items.CROSSBOW) {
            return false;
        }

        Consumable consumable = nms.get(DataComponents.CONSUMABLE);
        FoodProperties food = nms.get(DataComponents.FOOD);
        return consumable != null
                && consumable.consumeSeconds() > 0.0F
                && (food == null || food.canAlwaysEat() || player.food < 20 || player.gamemode == GameMode.CREATIVE);
    }

    private static DataComponentType<?> findDataComponent(String fieldName) {
        try {
            return (DataComponentType<?>)
                    DataComponents.class.getField(fieldName).get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @CultPacketHandler
    public void onInteract(
            PacketReceiveEvent<ServerboundInteract> event, CultPlayer player, ServerboundInteract packet) {
        handleInteract(packet);
    }

    @CultPacketHandler
    public void onUseItem(PacketReceiveEvent<ServerboundUseItem> event, CultPlayer player, ServerboundUseItem packet) {
        useItem(SpigotConversionUtil.toNmsHand(packet.hand()));
    }

    @CultPacketHandler
    public void onPlayerAction(
            PacketReceiveEvent<ServerboundPlayerAction> event, CultPlayer player, ServerboundPlayerAction packet) {
        handlePlayerAction(packet);
    }

    @CultPacketHandler
    public void onPlayerInput(
            PacketReceiveEvent<ServerboundPlayerInput> event, CultPlayer player, ServerboundPlayerInput packet) {
        ServerboundPlayerInput input = packet;
        player.packetStateData.knownInput = new ac.cult.cultac.utils.data.KnownInput(
                input.forward(),
                input.backward(),
                input.left(),
                input.right(),
                input.jump(),
                input.shift(),
                input.sprint());
    }

    @CultPacketHandler
    public void onSetCarriedItem(
            PacketReceiveEvent<ServerboundSetCarriedItem> event, CultPlayer player, ServerboundSetCarriedItem packet) {
        selectHotbarSlot();
    }

    public void selectHotbarSlot() {
        if (hand != null && hand != InteractionHand.OFF_HAND) {
            this.blocking = false;
        }
    }

    @CultPacketHandler
    public void onMovePlayerPos(
            PacketReceiveEvent<ServerboundMovePlayer> event, CultPlayer player, ServerboundMovePlayer packet) {
        clearMainHandUseAfterSlotChange();
    }

    @Override
    public void onPlayerTickEnd(final PacketReceiveEvent event) {
        endClientTick();
    }

    public void endClientTick() {
        clearMainHandUseAfterSlotChange();
        ++player.totalFlyingPacketsSent;
        ++this.flyingPacketsSinceAttack;
        this.attacking = false;
        this.digging = false;
        this.releasing = false;
        this.lastFlyingPacketTime = System.currentTimeMillis();
        //
        if (this.blocking) {
            ++this.blockingTicks;
        } else {
            this.blockingTicks = 0;
        }
    }

    @CultPacketHandler
    public void onSetHeldSlot(
            ac.cult.cultac.network.event.PacketSendEvent<ClientboundSetHeldSlot> event,
            CultPlayer player,
            ClientboundSetHeldSlot packet) {
        this.blocking = false;
        player.packetStateData.setSlowedByUsingItem(false);
    }

    public boolean attackedWithin(long windowMs) {
        return System.currentTimeMillis() - this.lastAttack < windowMs;
    }

    public void onRespawn() {
        this.target = null;
        this.lastTargetId = 0;
        this.targetId = 0;
        this.blocking = false;
        player.packetStateData.setSlowedByUsingItem(false);
    }

    private void clearMainHandUseAfterSlotChange() {
        if (player.packetStateData.isSlowedByUsingItem()
                && player.packetStateData.itemInUseHand == InteractionHand.MAIN_HAND
                && player.packetStateData.getSlowedByUsingItemSlot() != player.packetStateData.lastSlotSelected) {
            player.packetStateData.setSlowedByUsingItem(false);
        }
    }
}
