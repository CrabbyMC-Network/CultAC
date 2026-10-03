package ac.cult.cultac.manager.player.features.types;

import ac.cult.cultac.player.CultPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.feature.FeatureState;

public class ExperimentalChecksFeature implements CultFeature {

    @Override
    public String getName() {
        return "ExperimentalChecks";
    }

    @Override
    public void setState(CultPlayer player, ConfigManager config, FeatureState state) {
        switch (state) {
            case ENABLED -> player.setExperimentalChecks(true);
            case DISABLED -> player.setExperimentalChecks(false);
            default -> player.setExperimentalChecks(isEnabledInConfig(player, config));
        }
    }

    @Override
    public boolean isEnabled(CultPlayer player) {
        return player.isExperimentalChecks();
    }

    @Override
    public boolean isEnabledInConfig(CultPlayer player, ConfigManager config) {
        return config.getBooleanElse("experimental-checks", false);
    }
}
