package ac.cult.cultac.platform.velocity;

import ac.cult.cultac.platform.api.manager.PermissionRegistrationManager;
import ac.cult.cultac.platform.api.permissions.PermissionDefaultValue;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VelocityPermissions implements PermissionRegistrationManager {
    private final Map<String, Boolean> defaults = new ConcurrentHashMap<>();

    @Override
    public void registerPermission(String name, PermissionDefaultValue value) {
        // Velocity has no operator status. Administrative permissions require an explicit grant.
        defaults.put(name, value == PermissionDefaultValue.TRUE || value == PermissionDefaultValue.NOT_OP);
    }

    boolean has(CommandSource source, String node) {
        return has(source, node, defaults.getOrDefault(node, false));
    }

    static boolean has(CommandSource source, String node, boolean fallback) {
        Tristate value = source.getPermissionValue(node);
        return value == Tristate.UNDEFINED ? fallback : value == Tristate.TRUE;
    }
}
