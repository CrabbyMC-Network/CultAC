package ac.cult.cultac.utils.anticheat;

import ac.cult.cultac.CultAPI;
import ac.cult.cultac.platform.api.sender.Sender;
import ac.cult.cultac.player.CultPlayer;
import ac.grim.grimac.api.GrimUser;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;

/**
 * Presentation helpers turning raw config strings and components into text a
 * human reader can consume: prefix substitution, legacy color-code translation,
 * per-user placeholder resolution, and dispatch to a command sender.
 */
@UtilityClass
public class HumanFormatter {

    /** Substitutes the configured prefix, then translates &-style color codes. */
    public String format(String input) {
        String substituted = formatWithNoColor(input);
        char[] chars = substituted.toCharArray();
        for (int i = 0; i + 1 < chars.length; i++) {
            char code = Character.toLowerCase(chars[i + 1]);
            if (chars[i] == '&' && "0123456789abcdefklmnorx".indexOf(code) >= 0) {
                chars[i] = '\u00a7';
                chars[i + 1] = code;
            }
        }
        return new String(chars);
    }

    /** Substitutes the configured prefix without touching color codes. */
    public String formatWithNoColor(String input) {
        String prefix = CultAPI.INSTANCE.getConfigManager().getConfig().getStringElse("prefix", "&bCult &8»");
        return input.replace("%prefix%", prefix);
    }

    /** Resolves user-scoped placeholders when the user is an in-game player. */
    public Component format(GrimUser user, Component component) {
        return user instanceof CultPlayer cultPlayer
                ? MessageUtil.replacePlaceholders(cultPlayer, component)
                : component;
    }

    /** Without a user there is no context to resolve placeholders against. */
    public Component format(Component component) {
        return component;
    }

    /** Sends the component to the sender, players and console alike. */
    public void message(Sender sender, Component component) {
        sender.sendMessage(component);
    }
}
