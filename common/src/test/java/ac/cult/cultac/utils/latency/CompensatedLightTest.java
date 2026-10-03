package ac.cult.cultac.utils.latency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.BitSet;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.DataLayer;
import org.junit.jupiter.api.Test;

class CompensatedLightTest {
    @Test
    void packetLayersPreserveLightAcrossNegativeHeightAndDimensionTypes() {
        var light = new CompensatedLight(24);
        var sky = new DataLayer(15);
        var block = new DataLayer();
        sky.set(3, 0, 5, 6);
        sky.set(3, 1, 5, 4);
        block.set(3, 1, 5, 11);
        light.apply(new ClientboundLightUpdatePacketData(
                bits(5), bits(5), new BitSet(), new BitSet(), List.of(sky.getData()), List.of(block.getData())));
        assertEquals(11, light.brightness(3, 1, 5, -64, true));
        assertEquals(11, light.brightness(3, 1, 5, -64, false));
        assertEquals(6, light.brightness(3, -20, 5, -64, true));
        assertEquals(0, light.brightness(3, -20, 5, -64, false));
        assertEquals(15, light.brightness(3, 320, 5, -64, true));
        light.apply(new ClientboundLightUpdatePacketData(
                new BitSet(), new BitSet(), bits(5), bits(5), List.of(), List.of()));
        assertEquals(0, light.brightness(3, 1, 5, -64, true));
        assertEquals(0, light.brightness(3, 1, 5, -64, false));
    }

    private static BitSet bits(int index) {
        var bits = new BitSet();
        bits.set(index);
        return bits;
    }
}
