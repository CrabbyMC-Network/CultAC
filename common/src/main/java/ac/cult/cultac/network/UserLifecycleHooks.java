package ac.cult.cultac.network;

import ac.cult.cultac.network.protocol.player.User;
import ac.cult.cultac.platform.api.player.PlatformPlayer;

/** Notifications only; the session owner performs required creation and teardown. */
public interface UserLifecycleHooks {
    UserLifecycleHooks NONE = new UserLifecycleHooks() {};

    default void onAuthenticated(User user) {}

    default void onLogin(User user, PlatformPlayer player) {}
}
