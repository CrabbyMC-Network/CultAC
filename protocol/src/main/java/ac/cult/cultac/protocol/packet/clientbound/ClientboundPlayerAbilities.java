package ac.cult.cultac.protocol.packet.clientbound;

public record ClientboundPlayerAbilities(
        boolean invulnerable, boolean flying, boolean canFly, boolean instabuild, float flyingSpeed, float walkingSpeed)
        implements ClientboundPacket {}
