package ac.cult.cultac.manager.init.stop;

import ac.cult.cultac.CultAPI;

public class TerminateNetworkManager implements StoppableInitable {
    @Override
    public void stop() {
        // Plugin lifecycle runs on the server thread. Keep later teardown behind
        // packet-owner cleanup; neither I/O nor a packet owner waits on this join.
        CultAPI.INSTANCE.getNetworkManager().stop().toCompletableFuture().join();
    }
}
