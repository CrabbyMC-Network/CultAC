package ac.cult.cultac.protocol;

import static org.junit.jupiter.api.Assertions.*;

import ac.cult.cultac.protocol.data.ModelRegistryData;
import ac.cult.cultac.protocol.data.ModelRegistryNames;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelRegistryNamesTest {
    @Test
    void namesStayExactExceptForTheProvenChainRename() {
        for (var pair : List.of(
                List.of(ProtocolVersion.V1_21_3, ProtocolVersion.V1_21_11),
                List.of(ProtocolVersion.V1_21_11, ProtocolVersion.V1_21_11),
                List.of(ProtocolVersion.V26_2, ProtocolVersion.V26_3),
                List.of(ProtocolVersion.V26_3, ProtocolVersion.V26_3))) {
            var source = ModelRegistryData.load(pair.get(0));
            var target = ModelRegistryData.load(pair.get(1));
            var names = ModelRegistryNames.load(pair.get(0), pair.get(1));
            for (String item : source.registry("minecraft:item").names()) {
                String modelItem = names.modelItem(item);
                assertTrue(target.registry("minecraft:item").id(modelItem) >= 0);
                assertEquals(item, names.hostItem(modelItem));
                if (target.registry("minecraft:item").id(item) >= 0) assertEquals(item, modelItem);
            }
            for (String block : source.registry("minecraft:block").names()) {
                assertTrue(target.registry("minecraft:block").id(names.modelBlock(block)) >= 0);
                if (target.registry("minecraft:block").id(block) >= 0) assertEquals(block, names.modelBlock(block));
            }
        }
        var names = ModelRegistryNames.load(ProtocolVersion.V1_21_3, ProtocolVersion.V1_21_11);
        assertEquals("minecraft:iron_chain", names.modelBlock("minecraft:chain"));
        assertEquals("minecraft:iron_chain", names.modelItem("minecraft:chain"));
        assertEquals("minecraft:chain", names.hostItem("minecraft:iron_chain"));
    }
}
