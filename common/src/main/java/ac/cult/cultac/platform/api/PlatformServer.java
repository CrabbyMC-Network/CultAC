package ac.cult.cultac.platform.api;

import ac.cult.cultac.platform.api.sender.Sender;

public interface PlatformServer {

    String getPlatformImplementationString();

    void dispatchCommand(Sender sender, String command);

    Sender getConsoleSender();

    void registerOutgoingPluginChannel(String name);

    void forwardAlert(String message);

    boolean isProxyForwardingEnabled();

    /** Applies an explicit validation stimulus, then reports delivery through the callback. */
    void applyValidationBlock(
            ac.cult.cultac.platform.api.player.PlatformPlayer player,
            int x,
            int y,
            int z,
            String state,
            boolean packetOnly,
            Runnable applied);

    double getTPS();
}
