package org.Cry_Legende.sHDialogs;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Seule classe qui touche à PlaceholderAPI : sans lui elle n'est jamais chargée.
 */
final class PapiSupport {

    private PapiSupport() {
    }

    static String set(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
