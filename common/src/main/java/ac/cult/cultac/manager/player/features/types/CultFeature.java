package ac.cult.cultac.manager.player.features.types;

import ac.cult.cultac.player.CultPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.feature.FeatureState;

public interface CultFeature {
    String getName();

    void setState(CultPlayer player, ConfigManager config, FeatureState state);

    boolean isEnabled(CultPlayer player);

    boolean isEnabledInConfig(CultPlayer player, ConfigManager config);
}
