package ac.cult.cultac.utils.latency;

import java.util.BitSet;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.DataLayer;

/** Packet-owned light values. Uniform sections retain a nibble instead of a 2 KiB array. */
final class CompensatedLight {
    private final DataLayer[] sky;
    private final DataLayer[] block;

    CompensatedLight(int sections) {
        sky = new DataLayer[sections + 2];
        block = new DataLayer[sections + 2];
    }

    void apply(ClientboundLightUpdatePacketData data) {
        var values = ac.cult.cultac.network.packet.LightValues.fromNative(data);
        apply(sky, values.skyYMask(), values.emptySkyYMask(), values.skyUpdates());
        apply(block, values.blockYMask(), values.emptyBlockYMask(), values.blockUpdates());
    }

    private static void apply(DataLayer[] layers, BitSet present, BitSet empty, List<byte[]> updates) {
        var incoming = updates.iterator();
        for (int i = 0; i < layers.length; i++) {
            if (present.get(i)) layers[i] = compact(incoming.next());
            else if (empty.get(i)) layers[i] = new DataLayer();
        }
    }

    private static DataLayer compact(byte[] data) {
        if (data.length == 2048) {
            int first = data[0] & 255;
            if ((first & 15) == first >>> 4) {
                boolean uniform = true;
                for (byte value : data)
                    if ((value & 255) != first) {
                        uniform = false;
                        break;
                    }
                if (uniform) return new DataLayer(first & 15);
            }
        }
        return new DataLayer(data);
    }

    int brightness(int x, int y, int z, int minY, boolean hasSkyLight) {
        int section = (y >> 4) - (minY >> 4) + 1;
        int blockValue = section >= 0 && section < block.length && block[section] != null
                ? block[section].get(x & 15, y & 15, z & 15)
                : 0;
        if (!hasSkyLight) return blockValue;
        // SkyLightSectionStorage reads the bottom row of the next available section
        // when a layer is missing, and reads 15 above the column's top light section.
        int skyValue = 15;
        for (int i = Math.max(0, section); i < sky.length; i++) {
            if (sky[i] != null) {
                skyValue = sky[i].get(x & 15, i == section ? y & 15 : 0, z & 15);
                break;
            }
        }
        return Math.max(blockValue, skyValue);
    }
}
