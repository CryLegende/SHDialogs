package org.Cry_Legende.sHDialogs;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import io.papermc.paper.tag.TagEntry;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.List;

/**
 * Exécuté avant la fin du chargement du serveur : le registre des dialogues et les tags ne sont modifiables qu'à ce moment.
 * Enregistre le dialogue skyhand:menu puis l'ajoute aux tags PAUSE_SCREEN_ADDITIONS (écran Échap) et QUICK_ACTIONS (actions
 * rapides), sans datapack.
 */
public final class SHDialogsBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        MenuConfig menu;
        try {
            YamlConfiguration config = ConfigFiles.load(context.getDataDirectory());
            menu = MenuConfig.read(config, message -> context.getLogger().warn(message));
        } catch (IOException | RuntimeException e) {
            // un menu en échec ne doit jamais empêcher le démarrage du serveur
            context.getLogger().error("Menu SkyHand non chargé (config.yml illisible) : " + e);
            return;
        }
        if (!menu.enabled()) {
            context.getLogger().info("Menu SkyHand désactivé.");
            return;
        }

        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(), event -> {
            try {
                event.registry().register(MenuDialog.TYPED_KEY, builder -> MenuDialog.fill(builder, menu));
            } catch (RuntimeException e) {
                context.getLogger().error("Menu SkyHand non enregistré : " + e);
            }
        });

        // Les tags sont reconstruits à chaque rechargement des données (/reload) : le gestionnaire est rappelé et reste sans effet de bord.
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.preFlatten(RegistryKey.DIALOG), event -> {
            try {
                // entrée facultative : si le dialogue manquait, le tag resterait valide au lieu de faire échouer le chargement
                List<TagEntry<io.papermc.paper.dialog.Dialog>> entry = List.of(TagEntry.valueEntry(MenuDialog.TYPED_KEY, false));
                if (menu.pauseScreen()) {
                    event.registrar().addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, entry);
                }
                if (menu.quickActions()) {
                    event.registrar().addToTag(DialogTagKeys.QUICK_ACTIONS, entry);
                }
            } catch (RuntimeException e) {
                context.getLogger().error("Menu SkyHand non ajouté aux tags : " + e);
            }
        });
        context.getLogger().info("Menu SkyHand : " + menu.buttons().size() + " boutons, "
                + (menu.pauseScreen() ? "écran Échap" : "") + (menu.pauseScreen() && menu.quickActions() ? " et " : "")
                + (menu.quickActions() ? "actions rapides" : "") + ".");
    }
}
