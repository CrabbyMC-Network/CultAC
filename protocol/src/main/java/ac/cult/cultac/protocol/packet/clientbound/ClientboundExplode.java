package ac.cult.cultac.protocol.packet.clientbound;

import ac.cult.cultac.protocol.value.Vec3d;

public record ClientboundExplode(Vec3d center, Vec3d knockback) implements ClientboundPacket {}
