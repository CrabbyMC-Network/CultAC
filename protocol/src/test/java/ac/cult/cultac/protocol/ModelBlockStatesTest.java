package ac.cult.cultac.protocol;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.protocol.data.ModelBlockStates;
import ac.cult.cultac.protocol.data.ModelRegistryData;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelBlockStatesTest {
    @Test
    void newerHostIdsCanUseAnOlderModelOnlyWhenTheOriginalStateHasAProvenRepresentation() {
        for (var pair : List.of(
                List.of(ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_3),
                List.of(ProtocolVersion.V26_3, ProtocolVersion.V1_21_11),
                List.of(ProtocolVersion.V26_3, ProtocolVersion.V26_2))) {
            var source = ModelRegistryData.load(pair.get(0));
            var target = ModelRegistryData.load(pair.get(1));
            var mappings = ModelBlockStates.load(source.version(), target.version());
            for (int id = 0; id < source.blockStates().size(); id++) {
                int exact = target.blockStateId(source.blockStateName(id));
                if (exact < 0) continue;
                assertEquals(exact, mappings.toModel(id));
                assertEquals(id, mappings.toHost(exact));
            }
            assertThrows(MalformedPacketException.class, () -> mappings.toModel(-1));
            assertThrows(MalformedPacketException.class, () -> mappings.toHost(mappings.targetCount()));
        }
        var newer = ModelRegistryData.load(ProtocolVersion.V1_21_11);
        var mappings = ModelBlockStates.load(ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_3);
        int heart = newer.blockStates()
                .indexOf(newer.blockStates().stream()
                        .filter(state ->
                                state.startsWith("minecraft:creaking_heart[") && state.contains("natural=true"))
                        .findFirst()
                        .orElseThrow());
        assertThrows(ProtocolResolutionException.class, () -> mappings.toModel(heart));
        int ghast = newer.blockStates()
                .indexOf(newer.blockStates().stream()
                        .filter(state -> state.startsWith("minecraft:dried_ghast["))
                        .findFirst()
                        .orElseThrow());
        assertThrows(ProtocolResolutionException.class, () -> mappings.toModel(ghast));
    }

    @Test
    void exactStatesRoundTripAcrossBothHostAndBackingFamilies() {
        for (var pair : List.of(
                List.of(ProtocolVersion.V1_21_3, ProtocolVersion.V1_21_11),
                List.of(ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_11),
                List.of(ProtocolVersion.V26_2, ProtocolVersion.V26_3),
                List.of(ProtocolVersion.V26_3, ProtocolVersion.V26_3))) {
            var source = ModelRegistryData.load(pair.get(0));
            var target = ModelRegistryData.load(pair.get(1));
            var mappings = ModelBlockStates.load(pair.get(0), pair.get(1));
            assertEquals(source.blockStates().size(), mappings.sourceCount());
            assertEquals(target.blockStates().size(), mappings.targetCount());
            for (int id = 0; id < mappings.sourceCount(); id++) {
                int exact = target.blockStateId(source.blockStateName(id));
                if (exact >= 0) {
                    assertEquals(exact, mappings.toModel(id));
                    assertEquals(id, mappings.toHost(exact));
                }
            }
        }
    }

    @Test
    void renamedStatesInvertButLossyAndNewStatesCannotBecomeInventedHostStates() {
        var source = ModelRegistryData.load(ProtocolVersion.V1_21_3);
        var target = ModelRegistryData.load(ProtocolVersion.V1_21_11);
        var mappings = ModelBlockStates.load(source.version(), target.version());
        int chain = source.blockStateId("minecraft:chain[axis=x,waterlogged=true]");
        assertEquals(chain, mappings.toHost(mappings.toModel(chain)));
        int heart = source.blockStateId("minecraft:creaking_heart[axis=z,creaking=active]");
        assertThrows(ProtocolResolutionException.class, () -> mappings.toHost(mappings.toModel(heart)));
        int newState = target.blockStates()
                .indexOf(target.blockStates().stream()
                        .filter(state -> state.startsWith("minecraft:dried_ghast["))
                        .findFirst()
                        .orElseThrow());
        assertThrows(ProtocolResolutionException.class, () -> mappings.toHost(newState));
        assertThrows(MalformedPacketException.class, () -> mappings.toModel(-1));
        assertThrows(MalformedPacketException.class, () -> mappings.toHost(mappings.targetCount()));
    }
}
