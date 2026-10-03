package ac.cult.cultac.manager.player.features.types;

import ac.cult.cultac.player.CultPlayer;
import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.feature.FeatureState;

public class ExemptElytraFeature implements CultFeature {

    @Override
    public String getName() {
        return "ExemptElytra";
    }

    @Override
    public void setState(CultPlayer player, ConfigManager config, FeatureState state) {
        switch (state) {
            case ENABLED -> player.setExemptElytra(true);
            case DISABLED -> player.setExemptElytra(false);
            default -> player.setExemptElytra(isEnabledInConfig(player, config));
        }
    }

    @Override
    public boolean isEnabled(CultPlayer player) {
        return player.isExemptElytra();
    }

    @Override
    public boolean isEnabledInConfig(CultPlayer player, ConfigManager config) {
        return config.getBooleanElse("exempt-elytra", false);
    }
}
