package ac.cult.cultac.protocol;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.protocol.data.ModelIdMappings;
import ac.cult.cultac.protocol.data.ModelRegistryData;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelIdMappingsTest {
    private static final List<ProtocolVersion> VERSIONS =
            List.of(ProtocolVersion.V1_21_3, ProtocolVersion.V1_21_11, ProtocolVersion.V26_2, ProtocolVersion.V26_3);

    @Test
    void everyOfficialBlockAndItemIdHasAValidatedTargetAcrossAllSixPairs() {
        var models = VERSIONS.stream().map(ModelRegistryData::load).toList();
        for (int source = 0; source < VERSIONS.size(); source++)
            for (int target = source + 1; target < VERSIONS.size(); target++) {
                var mappings = ModelIdMappings.load(VERSIONS.get(source), VERSIONS.get(target));
                var old = models.get(source);
                var newer = models.get(target);
                assertEquals(old.blockStates().size(), mappings.blockStateCount());
                assertEquals(old.registry("minecraft:item").size(), mappings.itemCount());
                for (int id = 0; id < mappings.blockStateCount(); id++) {
                    int mapped = mappings.blockState(id);
                    assertTrue(mapped >= 0 && mapped < newer.blockStates().size());
                    int exact = newer.blockStateId(old.blockStateName(id));
                    if (exact >= 0) assertEquals(exact, mapped, old.blockStateName(id));
                }
                for (int id = 0; id < mappings.itemCount(); id++) {
                    int mapped = mappings.item(id);
                    assertTrue(mapped >= 0
                            && mapped < newer.registry("minecraft:item").size());
                    int exact = newer.registry("minecraft:item")
                            .id(old.registry("minecraft:item").name(id));
                    if (exact >= 0) assertEquals(exact, mapped);
                }
                assertSame(mappings, ModelIdMappings.load(VERSIONS.get(source), VERSIONS.get(target)));
            }
    }

    @Test
    void renamedChainAndHeartPropertiesFollowTheUpstreamMappings() {
        var old = ModelRegistryData.load(ProtocolVersion.V1_21_3);
        var newer = ModelRegistryData.load(ProtocolVersion.V1_21_11);
        var mappings = ModelIdMappings.load(old.version(), newer.version());
        int chain = old.blockStateId("minecraft:chain[axis=x,waterlogged=true]");
        assertEquals("minecraft:iron_chain[axis=x,waterlogged=true]", newer.blockStateName(mappings.blockState(chain)));
        int heart = old.blockStateId("minecraft:creaking_heart[axis=y,creaking=active]");
        assertEquals(
                "minecraft:creaking_heart[axis=y,creaking_heart_state=awake,natural=true]",
                newer.blockStateName(mappings.blockState(heart)));
        assertEquals(
                "minecraft:iron_chain",
                newer.registry("minecraft:item")
                        .name(mappings.item(old.registry("minecraft:item").id("minecraft:chain"))));
        assertThrows(MalformedPacketException.class, () -> mappings.blockState(-1));
        assertThrows(MalformedPacketException.class, () -> mappings.item(Integer.MAX_VALUE));
    }
}
