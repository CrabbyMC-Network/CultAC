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
package ac.cult.cultac.platform.bukkit;

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
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
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
public final class BukkitPacketCodecs {
    private BukkitPacketCodecs() {}

    /** Inventory and chunks are pinned to 26.3; block updates retain the existing version coverage. */
    public static List<PacketType<?>> catalog(RegistryAccess registries) {
        PacketCatalog serverbound = PacketCatalog.serverbound();
        PacketCatalog clientbound = PacketCatalog.clientbound();
        PacketCatalog.Scope inventory = serverbound.in(ConnectionPhase.PLAY).since(ProtocolVersion.V26_3);
        PacketCatalog.Scope play = clientbound.in(ConnectionPhase.PLAY);
        PacketCatalog.Scope pinned = play.since(ProtocolVersion.V26_3);
        pinned.add(
                "set_entity_data",
                EntityMetadata.class,
                writer(registries, BukkitPacketCodecs::metadata, (output, packet) -> {
                    output.writeVarInt(packet.id());
                    for (var entry : packet.packedItems()) output.writeBytes(entry.bytes());
                    output.writeByte(255);
                }));
        clientbound
                .in(ConnectionPhase.CONFIGURATION)
                .since(ProtocolVersion.V26_3)
                .add("registry_data", RegistryData.class, new PacketCodec<>() {
                    @Override
                    public RegistryData read(ByteBuf input, ProtocolContext context) {
                        var key = ResourceKey.REGISTRY_STREAM_CODEC.decode(input);
                        // Other registry contents have no consumer; leave their bytes untouched.
                        var entries = new ArrayList<RegistrySynchronization.PackedRegistryEntry>();
                        if (key.equals(Registries.BLOCK_TRANSFORMER) || key.equals(Registries.BLOCK_STATE_PROVIDER)) {
                            int count = Wire.readLength(input, input.readableBytes());
                            for (int i = 0; i < count; i++)
                                entries.add(RegistrySynchronization.PackedRegistryEntry.STREAM_CODEC.decode(input));
                        }
                        return new RegistryData(key, entries);
                    }

                    @Override
                    public boolean readsEntirePayload() {
                        return false;
                    }
                });
        inventory.add("container_click", InventoryClick.class, reader(registries, BukkitPacketCodecs::click));
        inventory.add(
                "set_creative_mode_slot",
                InventoryPackets.CreativeSlot.class,
                reader(
                        registries,
                        input -> new InventoryPackets.CreativeSlot(
                                input.readShort(),
                                SpigotConversionUtil.fromNmsItemStack(
                                        ByteBufCodecs.trackDepth(ItemStack.validatedStreamCodec(
                                                        ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC))
                                                .decode(input)))));
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
                "set_equipment", InventoryPackets.Equipment.class, reader(registries, BukkitPacketCodecs::equipment));
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
                reader(registries, BukkitPacketCodecs::sectionUpdates));
        pinned.add(
                "level_chunk_with_light",
                WorldPackets.Chunk.class,
                reader(registries, BukkitPacketCodecs::chunk, false));
        return Stream.of(Packets.all(), serverbound.types(), clientbound.types())
                .flatMap(List::stream)
                .toList();
    }

    private static <R> PacketCodec<R> reader(RegistryAccess registries, Function<RegistryFriendlyByteBuf, R> reader) {
        return reader(registries, reader, true);
    }

    private static <R> PacketCodec<R> reader(
            RegistryAccess registries, Function<RegistryFriendlyByteBuf, R> reader, boolean entirePayload) {
        return new PacketCodec<>() {
            @Override
            public R read(ByteBuf input, ProtocolContext context) {
                return reader.apply(new RegistryFriendlyByteBuf(input, registries));
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
        WindowClickType type =
                WindowClickType.VALUES[ContainerInput.STREAM_CODEC.decode(input).ordinal()];
        int size = ByteBufCodecs.readCount(input, 128);
        var changed = new HashMap<Integer, org.bukkit.inventory.ItemStack>(size);
        for (int i = 0; i < size; i++) {
            changed.put(
                    (int) input.readShort(),
                    SpigotConversionUtil.fromHashedStack(HashedStack.STREAM_CODEC.decode(input)));
        }
        return new InventoryClick(
                containerId,
                stateId,
                slot,
                button,
                type,
                changed,
                SpigotConversionUtil.fromHashedStack(HashedStack.STREAM_CODEC.decode(input)));
    }

    private static <R> WritablePacketCodec<R> writer(
            RegistryAccess registries,
            Function<RegistryFriendlyByteBuf, R> reader,
            BiConsumer<RegistryFriendlyByteBuf, R> writer) {
        return new WritablePacketCodec<>() {
            @Override
            public R read(ByteBuf input, ProtocolContext context) {
                return reader.apply(new RegistryFriendlyByteBuf(input, registries));
            }

            @Override
            public void write(ByteBuf output, ProtocolContext context, R packet) {
                writer.accept(new RegistryFriendlyByteBuf(output, registries), packet);
            }
        };
    }

    private static org.bukkit.inventory.ItemStack item(RegistryFriendlyByteBuf input) {
        return SpigotConversionUtil.fromNmsItemStack(ItemStack.OPTIONAL_STREAM_CODEC.decode(input));
    }

    /** PacketEvents' sequential index/type/value loop, pinned to release 26.3 serializers. */
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
        // Heightmaps do not affect the anticheat. Skip the 26.3 enum/long-array map without allocating it.
        int heightmaps = ByteBufCodecs.readCount(input, Integer.MAX_VALUE);
        for (int i = 0; i < heightmaps; i++) {
            input.readVarInt();
            int longs = ByteBufCodecs.readCount(input, input.readableBytes() / Long.BYTES);
            input.skipBytes(Math.multiplyExact(longs, Long.BYTES));
        }
        byte[] sections = input.readByteArray(2097152); // Same bound as vanilla's chunk value codec.
        int blockEntities = ByteBufCodecs.readCount(input, Integer.MAX_VALUE);
        var tickers = new ArrayList<BlockPos>();
        for (int i = 0; i < blockEntities; i++) {
            int xz = input.readUnsignedByte();
            int y = input.readShort();
            var type = ByteBufCodecs.registry(Registries.BLOCK_ENTITY_TYPE).decode(input);
            if ("potent_sulfur"
                    .equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).getPath())) {
                tickers.add(new BlockPos((x << 4) + (xz >> 4), y, (z << 4) + (xz & 15)));
            }
            NbtSkipper.skip(input, 512); // Only the block entity's position/type is consumed.
        }
        // Lighting is deliberately left opaque; palette decoding uses the consumer's current dimension.
        return new WorldPackets.Chunk(x, z, ByteBuffer.wrap(sections), tickers);
    }
}
