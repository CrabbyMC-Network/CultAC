package ac.cult.cultac.network;

import ac.cult.cultac.network.event.PacketListenerPriority;

/** Explicit registrations for families whose payload is not consumed. */
public interface OpaqueReceiveListener {
    void registerOpaqueReceivePackets(PacketRouteBuilder registrar, PacketListenerPriority priority);
}
