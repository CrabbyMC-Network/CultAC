package ac.cult.cultac.manager.player.features.types;

import ac.cult.cultac.player.CultPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.feature.FeatureState;

public class ForceSlowMovementFeature implements CultFeature {

    @Override
    public String getName() {
        return "ForceSlowMovement";
    }

    @Override
    public void setState(CultPlayer player, ConfigManager config, FeatureState state) {
        switch (state) {
            case ENABLED -> player.setForceSlowMovement(true);
            case DISABLED -> player.setForceSlowMovement(false);
            default -> player.setForceSlowMovement(isEnabledInConfig(player, config));
        }
    }

    @Override
    public boolean isEnabled(CultPlayer player) {
        return player.isForceSlowMovement();
    }

    @Override
    public boolean isEnabledInConfig(CultPlayer player, ConfigManager config) {
        return config.getBooleanElse("force-slow-movement", true);
    }
}
