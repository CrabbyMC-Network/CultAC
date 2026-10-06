package ac.cult.cultac.bedrock.replay.offline;

import ac.cult.cultac.checks.impl.prediction.DesyncStatus;
import ac.cult.cultac.checks.impl.prediction.PredVector;
import ac.cult.cultac.checks.impl.prediction.SimulationContext;
import ac.cult.cultac.checks.impl.prediction.stage.VelocityTransformer;
import ac.cult.cultac.checks.impl.prediction.stage.world.WorldData;
import ac.cult.cultac.network.protocol.ClientVersion;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.utils.data.packetentity.PacketEntitySelf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PlayerJumpAttributeTest {
    private CultPlayer player;
    private Method jump;
    @Before public void setup() throws Exception {
        OfflineCultTestBootstrap.installConfig();
        player = new CultPlayer(new User(new User.Profile(UUID.randomUUID(), "Springs_Test"),
                null, null, null, new EmbeddedChannel()));
        version(ClientVersion.V_26_2);
        jump = VelocityTransformer.class.getDeclaredMethod("applyPossibleJumpStates", CultPlayer.class, PredVector.class);
        jump.setAccessible(true);
    }
    @After public void close() { OfflineBedrockReplayRunnerTest.closeOfflinePlayer(player); }

    @Test public void springsModifiersReachActualJumpCandidatesAtAllLevels() throws Exception {
        for (double bonus : new double[]{0.09, 0.14, 0.20, 0.26, 0.60}) {
            player.compensatedEntities.updateAttributes(player.entityID, List.of(
                    new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.JUMP_STRENGTH, (double)0.42F,
                            List.of(new AttributeModifier(Identifier.fromNamespaceAndPath("crabbyenchants", "springs-jump-strength"),
                                    bonus, AttributeModifier.Operation.ADD_VALUE)))));
            assertEquals((float)((double)0.42F + bonus), candidates(false, null, 0).getFirst().y, 0);
        }
    }
    @Test public void unequippingRestoresVanillaPrediction() throws Exception {
        strength(0.68); assertEquals((double)(float)0.68, candidates(false, null, 0).getFirst().y, 0);
        strength((double)0.42F); assertEquals((double)0.42F, candidates(false, null, 0).getFirst().y, 0);
    }
    @Test public void honeyAndJumpPotionFollowVanillaOperationOrder() throws Exception {
        strength(0.68);
        assertEquals((double)((float)0.68 * 0.5F + 0.1F * 2), candidates(true, 1, 0).getFirst().y, 0);
    }
    @Test public void zeroJumpStrengthDoesNotCreateAJumpCandidate() throws Exception {
        strength(0); assertTrue(candidates(false, null, 0).isEmpty());
        assertEquals((double)0.1F, candidates(false, 0, 0).getFirst().y, 0);
    }
    @Test public void unsupportedJavaClientsKeepTheirVanillaImpulse() throws Exception {
        version(ClientVersion.V_1_20_3); strength(0.68);
        assertEquals((double)0.42F, player.compensatedEntities.getSelf().jumpStrength, 0);
        assertEquals((double)0.42F, candidates(false, null, 0).getFirst().y, 0);
        version(ClientVersion.V_1_20_5); strength(0.68);
        assertEquals((double)(float)0.68, candidates(false, null, 0).getFirst().y, 0);
    }
    @Test public void currentUpwardVelocityIsPreservedOnlyOnModernClients() throws Exception {
        strength(0.68); assertEquals(0.9, candidates(false, null, 0.9).getFirst().y, 0);
        version(ClientVersion.V_1_21); assertEquals((double)(float)0.68, candidates(false, null, 0.9).getFirst().y, 0);
    }
    @Test public void stateSurvivesSelfReplacementAndClampsToVanillaRange() {
        strength(0.68);
        assertEquals((double)(float)0.68, new PacketEntitySelf(player, player.compensatedEntities.getSelf()).jumpStrength, 0);
        strength(99); assertEquals(32, player.compensatedEntities.getSelf().jumpStrength, 0);
        strength(-1); assertEquals(0, player.compensatedEntities.getSelf().jumpStrength, 0);
    }
    @Test public void queuedUpdatesWaitForTheirExistingTransactionBarrier() throws Exception {
        player.latencyUtils.addRealTimeTask(42, () -> strength(0.68));
        assertEquals((double)0.42F, candidates(false, null, 0).getFirst().y, 0);
        player.latencyUtils.handleNettySyncTransaction(41);
        assertEquals((double)0.42F, candidates(false, null, 0).getFirst().y, 0);
        player.latencyUtils.handleNettySyncTransaction(42);
        assertEquals((double)(float)0.68, candidates(false, null, 0).getFirst().y, 0);
    }
    private void strength(double value) {
        player.compensatedEntities.updateAttributes(player.entityID, List.of(
                new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.JUMP_STRENGTH, value, List.of())));
    }
    @SuppressWarnings("unchecked")
    private List<PredVector> candidates(boolean honey, Integer potion, double currentY) throws Exception {
        SimulationContext context = mock(SimulationContext.class);
        WorldData world = mock(WorldData.class);
        when(context.getWorldData()).thenReturn(world);
        when(world.getOnHoneyBlock()).thenReturn(DesyncStatus.fromBoolean(honey));
        when(context.getIsSprinting()).thenReturn(DesyncStatus.FALSE);
        when(context.getVersion()).thenReturn(player.getClientVersion());
        when(context.getJumpAmplifier()).thenReturn(potion);
        return (List<PredVector>)jump.invoke(new VelocityTransformer(context), player, new PredVector(new Vec3(0, currentY, 0)));
    }
    private void version(ClientVersion version) throws Exception {
        Field field = CultPlayer.class.getDeclaredField("resolvedClientVersion");
        field.setAccessible(true); field.set(player, version);
    }
}
