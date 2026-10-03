/*
 * Equipment framing adapted from PacketEvents WrapperPlayServerEntityEquipment:
 * https://github.com/retrooper/packetevents/tree/5da85d7ad888f69732e0726aad8dde8f0e0ef4c1
 * Copyright (C) 2022 retrooper and contributors.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed without any warranty; see the GNU General Public
 * License for details. A copy is available at https://www.gnu.org/licenses/.
 * Adapted 2026-09-29 to the pinned release and vanilla ByteBuf item value helper.
 */
package ac.cult.cultac.network.codec;

import ac.cult.cultac.network.packet.EntityMetadata;
import ac.cult.cultac.network.packet.InventoryPackets;
import ac.cult.cultac.network.packet.RegistryData;
import ac.cult.cultac.network.packet.WorldPackets;
import ac.cult.cultac.network.protocol.util.SpigotConversionUtil;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.PacketCatalog;
import ac.cult.cultac.protocol.PacketCodec;
import ac.cult.cultac.protocol.PacketType;
import ac.cult.cultac.protocol.ProtocolContext;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.WritablePacketCodec;
import ac.cult.cultac.protocol.packet.Packets;
import ac.cult.cultac.protocol.wire.NbtSkipper;
import ac.cult.cultac.protocol.wire.Wire;
import ac.cult.cultac.utils.inventory.InventoryClick;
import ac.cult.cultac.utils.inventory.inventory.WindowClickType;
import com.mojang.datafixers.util.Pair;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;

/**
 * Platform value codecs for the shared byte dispatch catalog; never reads native packets.
 * Click and section-update framing follow PacketEvents' WrapperPlayClientClickWindow and
 * WrapperPlayServerMultiBlockChange (5da85d7ad888f69732e0726aad8dde8f0e0ef4c1),
 * independently verified against native codecs by the per-version conformance tests.
 * Item/component/hash serialization remains vanilla's responsibility.
 */
public final class NativePacketCodecs {
    private static final ProtocolVersion NATIVE_VERSION =
            ProtocolVersion.of(net.minecraft.SharedConstants.getProtocolVersion());
    private static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ItemStack> CREATIVE_ITEM =
            creativeItemCodec();

    private NativePacketCodecs() {}

    @SuppressWarnings("unchecked")
    private static net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ItemStack> creativeItemCodec() {
        try {
            net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ItemStack> values;
            try {
                values = (net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ItemStack>) ItemStack.class
                        .getField("OPTIONAL_UNTRUSTED_STREAM_CODEC")
                        .get(null);
            } catch (NoSuchFieldException earlier) {
                values = ItemStack.OPTIONAL_STREAM_CODEC;
            }
            var codec = ItemStack.validatedStreamCodec(values);
            // Paper adds a recursion guard around the vanilla creative-slot codec.
            // Preserve it on Paper; the standalone model uses the official codec.
            try {
                return (net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, ItemStack>) ByteBufCodecs.class
                        .getMethod("trackDepth", net.minecraft.network.codec.StreamCodec.class)
                        .invoke(null, codec);
            } catch (NoSuchMethodException vanilla) {
                return codec;
            }
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    /** Native values require the intercepted wire to match the host's exact protocol. */
    public static List<PacketType<?>> catalog(RegistryAccess registries) {
        return catalog(context -> registries);
    }

    public static List<PacketType<?>> connectionCatalog() {
        return catalog(context -> context.state()
                .require(ac.cult.cultac.utils.minecraft.MinecraftRegistries.class)
                .access());
    }

    private static List<PacketType<?>> catalog(Function<ProtocolContext, RegistryAccess> registries) {
        PacketCatalog serverbound = PacketCatalog.serverbound();
        PacketCatalog clientbound = PacketCatalog.clientbound();
        PacketCatalog.Scope inventory = serverbound.in(ConnectionPhase.PLAY).since(NATIVE_VERSION);
        PacketCatalog.Scope play = clientbound.in(ConnectionPhase.PLAY);
        PacketCatalog.Scope pinned = play.since(NATIVE_VERSION);
        pinned.add(
                "set_entity_data",
                EntityMetadata.class,
                writer(registries, NativePacketCodecs::metadata, (output, packet) -> {
                    output.writeVarInt(packet.id());
                    for (var entry : packet.packedItems()) output.writeBytes(entry.bytes());
                    output.writeByte(255);
                }));
        clientbound
                .in(ConnectionPhase.CONFIGURATION)
                .since(NATIVE_VERSION)
                .add("registry_data", RegistryData.class, new PacketCodec<>() {
                    @Override
                    public boolean requiresModelValues() {
                        return true;
                    }

                    @Override
                    public RegistryData read(ByteBuf input, ProtocolContext context) {
                        requireNativeWire(context);
                        var key = NativeValueCodecs.KEY.decode(input);
                        var entries = new ArrayList<RegistrySynchronization.PackedRegistryEntry>();
                        int count = Wire.readLength(input, input.readableBytes());
                        for (int i = 0; i < count; i++)
                            entries.add(RegistrySynchronization.PackedRegistryEntry.STREAM_CODEC.decode(input));
                        return new RegistryData(key, entries);
                    }
                });
        clientbound
                .in(ConnectionPhase.CONFIGURATION, ConnectionPhase.PLAY)
                .since(NATIVE_VERSION)
                .add("update_tags", ac.cult.cultac.network.packet.RegistryTags.class, new PacketCodec<>() {
                    @Override
                    public boolean requiresModelValues() {
                        return true;
                    }

                    @Override
                    public ac.cult.cultac.network.packet.RegistryTags read(ByteBuf input, ProtocolContext context) {
                        requireNativeWire(context);
                        return new ac.cult.cultac.network.packet.RegistryTags(
                                ByteBufCodecs.map(HashMap::new, NativeValueCodecs.KEY, NativeValueCodecs.TAGS)
                                        .decode(input));
                    }
                });
        inventory.add("container_click", InventoryClick.class, reader(registries, NativePacketCodecs::click));
        inventory.add(
                "set_creative_mode_slot",
                InventoryPackets.CreativeSlot.class,
                reader(
                        registries,
                        input -> new InventoryPackets.CreativeSlot(
                                input.readShort(),
                                SpigotConversionUtil.fromNmsItemStack(CREATIVE_ITEM.decode(input)))));
        pinned.add(
                "container_set_content",
                InventoryPackets.Content.class,
                reader(
                        registries,
                        input -> new InventoryPackets.Content(
                                input.readContainerId(),
                                input.readVarInt(),
                                ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(input).stream()
                                        .map(SpigotConversionUtil::fromNmsItemStack)
                                        .toList(),
                                item(input))));
        pinned.add(
                "container_set_slot",
                InventoryPackets.Slot.class,
                writer(
                        registries,
                        input -> new InventoryPackets.Slot(
                                input.readContainerId(), input.readVarInt(), input.readShort(), item(input)),
                        (output, slot) -> {
                            output.writeContainerId(slot.windowId());
                            output.writeVarInt(slot.stateId());
                            output.writeShort(slot.slot());
                            ItemStack.OPTIONAL_STREAM_CODEC.encode(
                                    output, SpigotConversionUtil.toNmsItemStack(slot.item()));
                        }));
        pinned.add(
                "set_player_inventory",
                InventoryPackets.PlayerInventory.class,
                reader(registries, input -> new InventoryPackets.PlayerInventory(input.readVarInt(), item(input))));
        pinned.add(
                "set_cursor_item",
                InventoryPackets.Cursor.class,
                reader(registries, input -> new InventoryPackets.Cursor(item(input))));
        pinned.add(
                "set_equipment", InventoryPackets.Equipment.class, reader(registries, NativePacketCodecs::equipment));
        pinned.add(
                "merchant_offers",
                InventoryPackets.Offers.class,
                reader(
                        registries,
                        input -> new InventoryPackets.Offers(
                                input.readContainerId(),
                                MerchantOffers.STREAM_CODEC.decode(input).stream()
                                        .map(offer -> new InventoryPackets.MerchantOffer(
                                                SpigotConversionUtil.fromNmsItemStack(offer.getCostA()),
                                                SpigotConversionUtil.fromNmsItemStack(offer.getCostB()),
                                                SpigotConversionUtil.fromNmsItemStack(offer.getResult()),
                                                offer.isOutOfStock()))
                                        .toList()),
                        false));
        play.add(
                "block_update",
                WorldPackets.BlockUpdate.class,
                writer(
                        registries,
                        input -> new WorldPackets.BlockUpdate(
                                BlockPos.STREAM_CODEC.decode(input),
                                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)
                                        .decode(input)),
                        (output, update) -> {
                            BlockPos.STREAM_CODEC.encode(output, update.position());
                            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY).encode(output, update.state());
                        }));
        play.add(
                "section_blocks_update",
                WorldPackets.SectionBlocksUpdate.class,
                reader(registries, NativePacketCodecs::sectionUpdates));
        pinned.add("level_chunk_with_light", WorldPackets.Chunk.class, reader(registries, NativePacketCodecs::chunk));
        pinned.add("light_update", WorldPackets.LightUpdate.class, reader(registries, input -> {
            int x = input.readVarInt(), z = input.readVarInt();
            return new WorldPackets.LightUpdate(x, z, NativeValueCodecs.light(input, x, z));
        }));
        return Stream.of(Packets.all(), serverbound.types(), clientbound.types())
                .flatMap(List::stream)
                .toList();
    }

    private static <R> PacketCodec<R> reader(
            Function<ProtocolContext, RegistryAccess> registries, Function<RegistryFriendlyByteBuf, R> reader) {
        return reader(registries, reader, true);
    }

    private static <R> PacketCodec<R> reader(
            Function<ProtocolContext, RegistryAccess> registries,
            Function<RegistryFriendlyByteBuf, R> reader,
            boolean entirePayload) {
        return new PacketCodec<>() {
            @Override
            public boolean requiresModelValues() {
                return true;
            }

            @Override
            public R read(ByteBuf input, ProtocolContext context) {
                requireNativeWire(context);
                return reader.apply(new RegistryFriendlyByteBuf(input, registries.apply(context)));
            }

            @Override
            public boolean readsEntirePayload() {
                return entirePayload;
            }
        };
    }

    private static InventoryClick click(RegistryFriendlyByteBuf input) {
        int containerId = input.readContainerId();
        int stateId = input.readVarInt();
        int slot = input.readShort();
        int button = input.readByte();
        WindowClickType type = WindowClickType.VALUES[
                NativeValueCodecs.CLICK_TYPE.decode(input).ordinal()];
        int size = ByteBufCodecs.readCount(input, 128);
        var changed = new HashMap<Integer, net.minecraft.world.item.ItemStack>(size);
        for (int i = 0; i < size; i++) {
            changed.put((int) input.readShort(), clickItem(input));
        }
        return new InventoryClick(containerId, stateId, slot, button, type, changed, clickItem(input));
    }

    private static ItemStack clickItem(RegistryFriendlyByteBuf input) {
        return NATIVE_VERSION.atLeast(ProtocolVersion.V1_21_5)
                ? SpigotConversionUtil.fromHashedStack(HashedStack.STREAM_CODEC.decode(input))
                : item(input);
    }

    private static <R> WritablePacketCodec<R> writer(
            Function<ProtocolContext, RegistryAccess> registries,
            Function<RegistryFriendlyByteBuf, R> reader,
            BiConsumer<RegistryFriendlyByteBuf, R> writer) {
        return new WritablePacketCodec<>() {
            @Override
            public boolean requiresModelValues() {
                return true;
            }

            @Override
            public R read(ByteBuf input, ProtocolContext context) {
                requireNativeWire(context);
                return reader.apply(new RegistryFriendlyByteBuf(input, registries.apply(context)));
            }

            @Override
            public void write(ByteBuf output, ProtocolContext context, R packet) {
                requireNativeWire(context);
                writer.accept(new RegistryFriendlyByteBuf(output, registries.apply(context)), packet);
            }
        };
    }

    private static void requireNativeWire(ProtocolContext context) {
        if (context.version() != NATIVE_VERSION)
            throw new ac.cult.cultac.protocol.ProtocolResolutionException(
                    "Native value codec needs " + NATIVE_VERSION + " wire bytes, received " + context.version());
    }

    private static net.minecraft.world.item.ItemStack item(RegistryFriendlyByteBuf input) {
        return SpigotConversionUtil.fromNmsItemStack(ItemStack.OPTIONAL_STREAM_CODEC.decode(input));
    }

    /** Sequential index/type/value loop; serializers belong to the native wire version. */
    private static EntityMetadata metadata(RegistryFriendlyByteBuf input) {
        int entityId = input.readVarInt();
        var entries = new ArrayList<EntityMetadata.Entry>();
        int index;
        while ((index = input.readUnsignedByte()) != 255) {
            int start = input.readerIndex() - 1;
            int type = input.readVarInt();
            var serializer = EntityDataSerializers.getSerializer(type);
            if (serializer == null)
                throw new io.netty.handler.codec.DecoderException("Unknown metadata serializer " + type);
            Object value = null;
            if (NATIVE_VERSION != ProtocolVersion.V26_3)
                value = serializer.codec().decode(input);
            else
                switch (type) {
                    // Preserve native value semantics for the types consumed by existing listeners.
                    case 0, 1, 3, 8, 11, 12, 19 -> value = serializer.codec().decode(input);
                    case 4 -> Wire.readString(input, 32767);
                    case 5 -> NbtSkipper.skip(input, 512);
                    case 6 -> {
                        if (input.readBoolean()) NbtSkipper.skip(input, 512);
                    }
                    // Unconsumed fixed-size/vector values need no object allocation.
                    case 9, 39 -> input.skipBytes(12);
                    case 10 -> input.skipBytes(8);
                    case 40 -> input.skipBytes(16);
                    // Items, particles and registry holders have serializer-specific boundaries.
                    // Vanilla reads those values from bytes; no native packet/list decoder is used.
                    default -> serializer.codec().decode(input);
                }
            entries.add(new EntityMetadata.Entry(
                    index, value, ByteBuffer.wrap(ByteBufUtil.getBytes(input, start, input.readerIndex() - start))));
        }
        return new EntityMetadata(entityId, entries);
    }

    /** PacketEvents WrapperPlayServerEntityEquipment.readEquipment's modern layout; vanilla reads item values. */
    private static InventoryPackets.Equipment equipment(RegistryFriendlyByteBuf input) {
        int entityId = input.readVarInt();
        var slots = new ArrayList<Pair<EquipmentSlot, ItemStack>>();
        int entry;
        do {
            entry = input.readUnsignedByte();
            slots.add(Pair.of(EquipmentSlot.VALUES.get(entry & 0x7F), ItemStack.OPTIONAL_STREAM_CODEC.decode(input)));
        } while ((entry & 0x80) != 0);
        return new InventoryPackets.Equipment(entityId, slots);
    }

    /** Sequential entries, as in PacketEvents WrapperPlayServerMultiBlockChange; positions use vanilla helpers. */
    private static WorldPackets.SectionBlocksUpdate sectionUpdates(RegistryFriendlyByteBuf input) {
        SectionPos section = SectionPos.of(input.readLong());
        int count = input.readVarInt();
        var updates = new ArrayList<WorldPackets.BlockUpdate>(count);
        for (int i = 0; i < count; i++) {
            long entry = input.readVarLong();
            short position = (short) (entry & 0xFFF);
            updates.add(new WorldPackets.BlockUpdate(
                    section.relativeToBlockPos(position), Block.BLOCK_STATE_REGISTRY.byId((int) (entry >>> 12))));
        }
        return new WorldPackets.SectionBlocksUpdate(updates);
    }

    private static WorldPackets.Chunk chunk(RegistryFriendlyByteBuf input) {
        int x = input.readInt();
        int z = input.readInt();
        // Heightmaps changed from NBT to an enum/long-array map in 1.21.5.
        if (!NATIVE_VERSION.atLeast(ProtocolVersion.V1_21_5)) NbtSkipper.skip(input, 512);
        else {
            int heightmaps = ByteBufCodecs.readCount(input, Integer.MAX_VALUE);
            for (int i = 0; i < heightmaps; i++) {
                input.readVarInt();
                int longs = ByteBufCodecs.readCount(input, input.readableBytes() / Long.BYTES);
                input.skipBytes(Math.multiplyExact(longs, Long.BYTES));
            }
        }
        byte[] sections = input.readByteArray(2097152); // Same bound as vanilla's chunk value codec.
        int blockEntities = ByteBufCodecs.readCount(input, Integer.MAX_VALUE);
        var tickers = new ArrayList<BlockPos>();
        for (int i = 0; i < blockEntities; i++) {
            int xz = input.readUnsignedByte();
            int y = input.readShort();
            var type = ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).decode(input);
            if ("potent_sulfur"
                    .equals(ac.cult.cultac.utils.nmsutil.NmsIdentifierUtil.registryPath(
                            BuiltInRegistries.BLOCK_ENTITY_TYPE, type))) {
                tickers.add(new BlockPos((x << 4) + (xz >> 4), y, (z << 4) + (xz & 15)));
            }
            NbtSkipper.skip(input, 512); // Only the block entity's position/type is consumed.
        }
        var light = NativeValueCodecs.light(input, x, z);
        return new WorldPackets.Chunk(x, z, ByteBuffer.wrap(sections), tickers, light);
    }
}
