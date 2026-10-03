package ac.cult.cultac.events.packets.listeners;

import static org.junit.Assert.assertEquals;

import ac.cult.cultac.network.PacketHandlerScanner;
import ac.cult.cultac.network.TestProtocolRuntime;
import ac.cult.cultac.protocol.ConnectionPhase;
import ac.cult.cultac.protocol.ProtocolVersion;
import ac.cult.cultac.protocol.data.ProtocolData;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.server.Bootstrap;
import org.junit.Test;

public final class CheckManagerListenerReceiveCatalogTest {
    @Test
    public void decodedPlayCatalogIncludesEveryVanillaServerboundWireId() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var version = ProtocolVersion.of(SharedConstants.getProtocolVersion());
        var runtime = TestProtocolRuntime.create(ProtocolData.load(version));
        var scanner = new PacketHandlerScanner(runtime);
        var families = CheckManagerListener.receiveDispatchPacketTypes(scanner);
        Set<String> expected = new HashSet<>();
        GameProtocols.SERVERBOUND_TEMPLATE
                .details()
                .listPackets((type, id) -> expected.add(type.id().toString()));
        Set<String> actual = new HashSet<>();
        for (var family : families) {
            if (family.phases().contains(ConnectionPhase.PLAY)) actual.addAll(family.wireNames(runtime.data()));
        }
        assertEquals(expected, actual);
        assertEquals(families.size(), new HashSet<>(families).size());
    }
}
