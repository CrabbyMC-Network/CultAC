package ac.cult.cultac.bedrock.bridge;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.bridge.wire.*;
import ac.cult.cultac.network.*;
import ac.cult.cultac.network.event.*;
import ac.cult.cultac.network.packet.NmsPacketUtil;
import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.player.CultPlayer;
import ac.cult.cultac.bedrock.protocol.*;
import ac.cult.cultac.bedrock.prediction.model.PlayerDimensionsState;
import ac.cult.cultac.utils.anticheat.LogUtil;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Authenticated proxy transport; every simulation mutation stays on the backend packet owner. */
public final class ProxyBridgeRuntime extends UserLifecycleListener implements PacketReceiveHandler<Packet<?>>,PacketSendHandler<Packet<?>> {
    private static volatile ProxyBridgeRuntime active;
    private final ProxyBridgeSessions sessions;
    private final Map<User,Lease> leases=new IdentityHashMap<>();
    // Logged-in Bedrock users whose client has not yet proven it processed this backend's login.
    private final Set<User> awaitingAttach=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final int MAX_BOUNDARIES=8192;
    private static final class Marker { boolean sent; long lastAuthTick=-1; final long request; Marker(long request){this.request=request;} }
    private static final class Lease {
        final ProxyBridgeSessions.Session session;final ac.cult.cultac.bridge.wire.InventoryFragments inventoryFragments=new ac.cult.cultac.bridge.wire.InventoryFragments();final Map<Integer,Marker> markers=new HashMap<>();long lastRequest=-1,teleportSequence,correctionSequence;
        Lease(ProxyBridgeSessions.Session session){this.session=session;}
    }
    private ProxyBridgeRuntime(byte[] key){super(PacketListenerPriority.MONITOR);sessions=new ProxyBridgeSessions(key);}
    public static void register(CultNetworkManager network,PacketRegistrar registrar) {
        Path file=CultAPI.INSTANCE.getPlugin().getDataFolder().toPath().resolve("proxy-bridge.key");
        if(!Files.isRegularFile(file))return;
        try {
            byte[] key=Base64.getDecoder().decode(Files.readString(file).trim());
            initializeMath();
            var bridge=new ProxyBridgeRuntime(key);active=bridge;
            network.registerListener(bridge);
            network.registerReceiveTap(PacketListenerPriority.LOWEST,bridge);
            registrar.send(ClientboundPingPacket.class,PacketListenerPriority.MONITOR,bridge);
            LogUtil.info("Authenticated proxy Bedrock bridge enabled.");
        }catch(Exception failure){throw new IllegalStateException("Invalid proxy bridge key file",failure);}
    }
    /**
     * The bundled Cloudburst math 2.0-SNAPSHOT finds its vector/imaginary implementations with
     * ServiceLoader.load(Class), i.e. the thread context class loader. On Netty threads that is the
     * server's loader, which cannot see this plugin's classes. Resolve and cache both providers once
     * here with the plugin's own loader.
     */
    private static void initializeMath() {
        Thread thread=Thread.currentThread();ClassLoader previous=thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(ProxyBridgeRuntime.class.getClassLoader());
            // Direct references keep both classes through shadow minimization.
            org.cloudburstmc.math.vector.Vector3f.from(0,0,0);
            org.cloudburstmc.math.imaginary.Quaternionf.from(0,0,0,1);
        } finally { thread.setContextClassLoader(previous); }
    }
    @Override public void onUserLogin(UserLoginEvent event) {
        User user=event.getUser();CultPlayer player=CultAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
        if(player==null || !player.isBedrockMovement() || user.getBedrockBridgeConnection()!=null)return;
        // During a Velocity server switch, backend plugin messages are forwarded while the new
        // login is still held back, so a challenge sent now can reach Geyser before that login
        // and be reset by it. Wait until the client acknowledges this backend's teleport.
        synchronized(leases){awaitingAttach.add(user);}
    }
    private void attach(User user) {
        user.execute(()->{
            if(active!=this)return;
            CultPlayer player=CultAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
            if(player==null || player.user!=user || !player.isBedrockMovement())return;
            var session=sessions.open(user,bytes->user.sendPacket(new ClientboundCustomPayloadPacket(new DiscardedPayload(Identifier.parse(BridgeEnvelopeCodec.CHANNEL),bytes))));
            synchronized(leases){leases.put(user,new Lease(session));}
        });
    }
    @Override public void onUserDisconnect(UserDisconnectEvent event){close(event.getUser());}
    private void close(User owner){sessions.close(owner);synchronized(leases){leases.remove(owner);awaitingAttach.remove(owner);}}
    @Override public void handle(PacketReceiveEvent event,CultPlayer player,Packet<?> packet) {
        if(packet instanceof ServerboundAcceptTeleportationPacket) {
            User owner=event.getUser();boolean attach;
            synchronized(leases){attach=awaitingAttach.remove(owner);}
            if(attach)attach(owner);
            return;
        }
        if(!(packet instanceof ServerboundCustomPayloadPacket custom)||!BridgeEnvelopeCodec.CHANNEL.equals(NmsPacketUtil.payloadChannel(custom.payload())))return;
        event.setCancelled(true);
        byte[] bytes=NmsPacketUtil.payloadData(event);User owner=event.getUser();
        owner.execute(()->{
            Lease lease;synchronized(leases){lease=leases.get(owner);}if(lease==null)return;
            CultPlayer current=CultAPI.INSTANCE.getPlayerDataManager().getPlayer(owner);
            if(current==null || current.user!=owner || !current.isBedrockMovement()){close(owner);return;}
            try {var envelope=lease.session.receive(owner,bytes);current.discountRateLimitedPacket();process(current,lease,envelope);}
            // LinkageError covers a broken runtime classpath (e.g. a missing ServiceLoader implementation):
            // the lease must still close instead of staying half-bound.
            catch(RuntimeException|LinkageError failure){close(owner);LogUtil.warn("Proxy Bedrock bridge rejected a connection: "+failure);owner.closeConnection();}
        });
    }
    @Override public void handle(PacketSendEvent event,CultPlayer player,Packet<?> packet) {
        if(!(packet instanceof ClientboundPingPacket ping)||player==null||!player.packetStateData.lastServerTransWasValid)return;
        Lease lease;synchronized(leases){lease=leases.get(event.getUser());}
        if(lease!=null){
            bounded(lease);lease.markers.put(ping.getId(),new Marker(0));
            var registration=new BridgeControlMessage.Latency(BridgeControlMessage.Latency.PING_REGISTER,0,ping.getId());
            event.getPacketsBeforeSend().add(new ClientboundCustomPayloadPacket(new DiscardedPayload(Identifier.parse(BridgeEnvelopeCodec.CHANNEL),lease.session.encode(BridgeEnvelope.Kind.LATENCY_RECEIPT,registration.encode()))));
        }
    }
    private void process(CultPlayer player,Lease lease,BridgeEnvelope envelope) {
        switch(envelope.kind()) {
            case HELLO -> {
                var context=lease.session.context();
                // Gateway HELLO follows a real initial native latency receipt.
                player.checkManager.getSimulationProcessor().handleBedrockActorCreation(context.actorRuntimeId());
                player.bedrockState.confirmCollisionDefinition(new PlayerDimensionsState(context.width(),context.height()));
            }
            case CLIENT_PACKET -> {
                if(lease.inventoryFragments.incomplete())throw new IllegalArgumentException("Incomplete native inventory projection");
                var input=AuthInputMessage.decode(envelope.body());if(input.protocol()!=lease.session.context().protocol())throw new IllegalArgumentException("Changed input protocol");
                var result=ProxyBridgeInputProcessor.process(player,envelope.sequence(),input);
                lease.session.send(BridgeEnvelope.Kind.INPUT_RESULT,result.encode());
            }
            case ACTOR_CONTEXT -> {
                var state=ActorStateMessage.decode(envelope.body());request(lease,state.request());
                var before=player.getLastClientboundBedrockTransaction();var boundary=player.createBedrockTransactionAfterClientbound();
                var marker=new Marker(state.request());
                var apply=ProxyBridgeActorState.capture(player,state,before,boundary,()->marker.lastAuthTick);
                player.addBedrockTransactionTask(boundary,apply);boundary(lease,state.request(),boundary.id(),marker);
            }
            case TELEPORT_EMISSION -> {
                var emission=TeleportEmissionMessage.decode(envelope.body());request(lease,emission.request());
                var coordinates=new BedrockCoordinateFrame(emission.originX(),emission.originZ(),emission.originRevision());
                var operation=new BedrockTeleportOperation(emission.operation(),BedrockTeleportProvenance.values()[emission.provenance()],emission.setbackTransaction()<0?null:emission.setbackTransaction());
                var before=player.getLastClientboundBedrockTransaction();int proof=before==null?player.lastTransactionReceived.get():before.transaction();
                var raw=emission.rawEye();Vec3 local=new Vec3(raw.x(),raw.y(),raw.z());var feet=emission.feet();
                long revision=player.getSetbackTeleportUtil().addImmediateBedrockTransportTeleport(new Vec3(feet.x(),feet.y(),feet.z()),emission.onGround(),coordinates,local,operation,proof);
                player.getSetbackTeleportUtil().requireBedrockTransportReceipt(revision);
                var receipt=player.createBedrockTransactionAfterClientbound();
                player.addBedrockTransactionTask(receipt,()->player.getSetbackTeleportUtil().confirmBedrockOrigin(revision,operation,coordinates,local));
                boundary(lease,emission.request(),receipt.id());
            }
            case LATENCY_RECEIPT -> {
                var receipt=BridgeControlMessage.Latency.decode(envelope.body());
                if(receipt.type()==BridgeControlMessage.Latency.REQUEST){request(lease,receipt.request());var marker=player.createBedrockTransactionAfterClientbound();boundary(lease,receipt.request(),marker.id());return;}
                var marker=lease.markers.get(receipt.marker());
                if(marker==null || marker.request!=receipt.request())throw new IllegalArgumentException("Unknown native receipt");
                if(receipt.type()==BridgeControlMessage.Latency.SENT){if(marker.sent)throw new IllegalArgumentException("Duplicate native write");marker.sent=true;player.markBedrockTransactionClientbound(receipt.marker());}
                else if(receipt.type()==BridgeControlMessage.Latency.ACK){if(!marker.sent)throw new IllegalArgumentException("Receipt before native write");marker.lastAuthTick=receipt.lastAuthTick();if(marker.lastAuthTick<0)throw new IllegalArgumentException("Missing native input receipt boundary");lease.markers.remove(receipt.marker());if(!ac.cult.cultac.events.packets.listeners.PacketPingListener.acceptBedrockResponse(player,receipt.marker()))throw new IllegalArgumentException("Invalid native acknowledgement");}
                else throw new IllegalArgumentException("Unexpected latency control");
            }
            case INVENTORY_DIFF -> {var body=lease.inventoryFragments.accept(envelope.body());if(body!=null)ProxyBridgeInventoryActions.apply(player,body);}
            case CLOSE -> close(player.user);
            default -> throw new IllegalArgumentException("Unexpected bridge message");
        }
    }
    private static void request(Lease lease,long request){if(request<=lease.lastRequest)throw new IllegalArgumentException("Repeated native state request");lease.lastRequest=request;}
    private static void bounded(Lease lease){if(lease.markers.size()>=MAX_BOUNDARIES)throw new IllegalStateException("Unacknowledged native state overflow");}
    private static void boundary(Lease lease,long request,int marker){boundary(lease,request,marker,new Marker(request));}
    private static void boundary(Lease lease,long request,int marker,Marker state){bounded(lease);lease.markers.put(marker,state);lease.session.send(BridgeEnvelope.Kind.LATENCY_RECEIPT,new BridgeControlMessage.Latency(BridgeControlMessage.Latency.BOUNDARY,request,marker).encode());}
    public static boolean hasTransport(User user) {
        var runtime=active;if(runtime==null)return false;
        synchronized(runtime.leases){return runtime.leases.containsKey(user);}
    }
    public static boolean requiresNativeReceipt(CultPlayer player) {
        return active!=null && player.isBedrockMovement() && player.user.getBedrockBridgeConnection()==null;
    }
    public static boolean sendPlayerTeleport(User user,Vec3 position,float yaw,float pitch,boolean onGround,int transaction) {
        var runtime=active;if(runtime==null)return false;
        Lease lease;synchronized(runtime.leases){lease=runtime.leases.get(user);}if(lease==null || lease.session.context()==null)return false;
        user.execute(()->{
            CultPlayer player=CultAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
            if(player==null || !player.getSetbackTeleportUtil().isCurrentBedrockSetback(transaction))return;
            var feet=new AuthInputMessage.Double3(position.x,position.y,position.z);
            var emission=new TeleportEmissionMessage(0,++lease.teleportSequence,BedrockTeleportProvenance.CULT_SETBACK.ordinal(),transaction,feet,new AuthInputMessage.Float3((float)position.x,(float)position.y,(float)position.z),yaw,pitch,onGround,-1,0,0,0);
            lease.session.send(BridgeEnvelope.Kind.SERVER_TELEPORT,emission.encode());
        });return true;
    }
    public static boolean sendMovementCorrection(User user,BedrockMovementCorrection correction) {
        var runtime=active;if(runtime==null)return false;
        Lease lease;synchronized(runtime.leases){lease=runtime.leases.get(user);}if(lease==null || lease.session.context()==null)return false;
        if(!user.getPacketExecutor().inEventLoop())throw new IllegalStateException("Correction outside packet owner");
        var player=CultAPI.INSTANCE.getPlayerDataManager().getPlayer(user);
        if(player==null || player.getSetbackTeleportUtil().hasPendingBedrockTransportTeleport() || player.bedrockState.movementCorrections.generation()!=correction.controlGeneration())return false;
        var vehicle=player.compensatedEntities.getSelf().getRiding();
        if(correction.vehicle() && (vehicle==null || vehicle.getEntityId()!=correction.vehicleId() || vehicle.bedrockRuntimeId!=correction.runtimeId()) || !correction.vehicle() && vehicle!=null)return false;
        var c=correction.coordinates();var pos=correction.position();var velocity=correction.velocity();
        var message=new CorrectionMessage(++lease.correctionSequence,correction.controlGeneration(),correction.vehicleId(),correction.runtimeId(),correction.tick(),new AuthInputMessage.Double3(pos.x,pos.y,pos.z),new AuthInputMessage.Double3(velocity.x,velocity.y,velocity.z),correction.yaw(),correction.pitch(),correction.onGround(),c.originX(),c.originZ(),c.revision(),correction.teleportTransaction(),correction.angularVelocity(),correction.vehicle());
        lease.session.send(BridgeEnvelope.Kind.SERVER_CORRECTION,message.encode());return true;
    }
    public static void stop(){var runtime=active;active=null;if(runtime!=null){runtime.sessions.closeAll();synchronized(runtime.leases){runtime.leases.clear();runtime.awaitingAttach.clear();}}}
}
