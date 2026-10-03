package ac.cult.cultac.codec;

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.Objects;

/** Repairs only the redundant direct state in ViaBackwards' older typed-provider representation. */
final class ModelRegistryProjection {
    static byte[] enchantments(byte[] bytes) {
        ByteBuf input = Unpooled.wrappedBuffer(bytes);
        try {
            int id = Types.VAR_INT.readPrimitive(input);
            String key = Types.STRING.read(input);
            if (!key.equals("minecraft:enchantment") && !key.equals("enchantment")) return bytes;
            var entries = Types.REGISTRY_ENTRY_ARRAY.read(input);
            if (input.isReadable()) throw new IllegalStateException("Trailing registry bytes");
            boolean changed = false;
            for (var entry : entries) if (entry.tag() != null) changed |= update(entry.tag());
            if (!changed) return bytes;
            ByteBuf output = Unpooled.buffer();
            try {
                Types.VAR_INT.writePrimitive(output, id);
                Types.STRING.write(output, key);
                Types.REGISTRY_ENTRY_ARRAY.write(output, entries);
                return ByteBufUtil.getBytes(output);
            } finally {
                output.release();
            }
        } finally {
            input.release();
        }
    }

    private static boolean update(Tag tag) {
        boolean changed = false;
        if (tag instanceof CompoundTag compound) {
            String type = compound.getString("type");
            if ("minecraft:replace_disk".equals(type)
                    || "replace_disk".equals(type)
                    || "minecraft:replace_block".equals(type)
                    || "replace_block".equals(type)) {
                CompoundTag provider = compound.getCompoundTag("block_state");
                if (provider != null
                        && ("simple".equals(provider.getString("type"))
                                || "minecraft:simple".equals(provider.getString("type")))
                        && provider.contains("id")) {
                    CompoundTag state = provider.getCompoundTag("state");
                    if (state == null
                            || !Objects.equals(provider.get("id"), state.get("id"))
                            || !Objects.equals(provider.get("properties"), state.get("properties"))) {
                        throw new IllegalStateException("Conflicting direct state and typed provider projection");
                    }
                    // 1.21.11 dispatches on 'type' and ignores the duplicate root fields.
                    // 26.3 Codec.xor(FULL_BLOCK_STATE, TYPED_PROVIDER) rejects that ambiguity.
                    // Keep the exact typed provider, state and effect. Physical bytes stay intact.
                    provider.remove("id");
                    provider.remove("properties");
                    changed = true;
                }
            }
            for (var entry : compound.entrySet()) changed |= update(entry.getValue());
        } else if (tag instanceof ListTag<?> list) {
            for (Tag value : list.getValue()) changed |= update(value);
        }
        return changed;
    }
}
