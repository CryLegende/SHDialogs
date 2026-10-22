package org.Cry_Legende.sHDialogs;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/**
 * Lance une commande comme si le joueur l'avait tapée dans le chat.
 * <p>
 * Player.performCommand ne déclenche pas PlayerCommandPreprocessEvent, alors que beaucoup de commandes d'Opale en dépendent :
 * DeluxeMenus ouvre ses menus (/is, /warp, /kits, /shop...) en interceptant cet événement, et les plugins de combat ou de
 * restriction de commandes l'écoutent aussi. On le déclenche donc nous-mêmes avant d'exécuter la commande.
 * Toujours exécutée par le joueur : jamais par la console.
 */
final class CommandRunner {

    enum Result {
        /** Un plugin a pris la commande en charge (menu DeluxeMenus, refus...) : il a déjà répondu au joueur. */
        HANDLED,
        /** La commande a été exécutée par le joueur. */
        EXECUTED,
        /** Aucune commande de ce nom sur le serveur. */
        UNKNOWN
    }

    private CommandRunner() {
    }

    /**
     * @param command commande sans « / » initial
     */
    static Result asPlayer(Player player, String command) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/" + command);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Result.HANDLED;
        }
        String message = event.getMessage();
        while (message.startsWith("/")) {
            message = message.substring(1);
        }
        message = message.trim();
        if (message.isEmpty()) {
            return Result.UNKNOWN;
        }
        String label = message.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (Bukkit.getCommandMap().getCommand(label) == null) {
            return Result.UNKNOWN;
        }
        player.performCommand(message);
        return Result.EXECUTED;
    }
}
