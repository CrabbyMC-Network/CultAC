package ac.cult.cultac.network;

import ac.cult.cultac.network.protocol.player.User;
import org.bukkit.entity.Player;

/** Notifications only; the session owner performs required creation and teardown. */
public interface UserLifecycleHooks {
    UserLifecycleHooks NONE = new UserLifecycleHooks() { };
    default void onAuthenticated(User user) { }
    default void onLogin(User user, Player player) { }
}
