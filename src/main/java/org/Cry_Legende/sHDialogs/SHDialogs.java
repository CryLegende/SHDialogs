package org.Cry_Legende.sHDialogs;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SHDialogs extends JavaPlugin implements Listener {

    static final String PERMISSION_ADMIN = "shdialogs.admin";
    static final String PERMISSION_MENU = "shdialogs.menu";
    private static final String CLICK_NAMESPACE = "shdialogs";
    private static final long MENU_CLICK_INTERVAL_MS = 300;

    private final Map<UUID, Long> lastMenuClick = new ConcurrentHashMap<>();
    private volatile Messages messages;
    private volatile MenuConfig menu;
    private String registeredMenuSignature = "";
    private boolean viaVersion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        viaVersion = getServer().getPluginManager().isPluginEnabled("ViaVersion");
        loadConfiguration();
        // ce que le bootstrap a enregistré au démarrage : base de comparaison pour /shdialogs reload
        registeredMenuSignature = menu.signature();
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
        diagnoseMenu();
        getLogger().info("SHDialogs prêt (menu " + (menu.enabled() ? menu.buttons().size() + " boutons" : "désactivé") + ").");
    }

    @Override
    public void onDisable() {
        lastMenuClick.clear();
    }

    /**
     * Relit config.yml. Le dialogue du menu, lui, reste celui du démarrage : seules les commandes, permissions et messages changent.
     */
    private void loadConfiguration() {
        reloadConfig();
        FileConfiguration config = getConfig();
        messages = new Messages(config);
        menu = MenuConfig.read(config, getLogger()::warning);
    }

    // ------------------------------------------------------------------ outils

    void sync(Runnable task) {
        if (getServer().isPrimaryThread()) {
            task.run();
        } else {
            getServer().getScheduler().runTask(this, task);
        }
    }

    /**
     * Les clients plus anciens que minProtocol (1.21.6 : 771) ne connaissent pas les dialogues. Sans ViaVersion, ou si
     * ViaVersion ne connaît pas le client, on le considère compatible : le serveur lui-même est en 1.21.11.
     */
    boolean supportsDialogs(Player player, int minProtocol) {
        if (!viaVersion) {
            return true;
        }
        int protocol = ViaSupport.protocol(player);
        return protocol <= 0 || protocol >= minProtocol;
    }

    /**
     * Vérifie au démarrage que le dialogue est bien dans le registre et dans les deux tags.
     */
    private void diagnoseMenu() {
        if (!menu.enabled()) {
            return;
        }
        try {
            Registry<Dialog> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG);
            if (registry.get(MenuDialog.KEY) == null) {
                getLogger().warning("Le dialogue " + MenuDialog.KEY.asString() + " n'est pas enregistré : voir les messages de démarrage du bootstrap.");
                return;
            }
            boolean pause = registry.hasTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS)
                    && registry.getTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS).contains(MenuDialog.TYPED_KEY);
            boolean quick = registry.hasTag(DialogTagKeys.QUICK_ACTIONS)
                    && registry.getTag(DialogTagKeys.QUICK_ACTIONS).contains(MenuDialog.TYPED_KEY);
            getLogger().info("Menu SkyHand enregistré (" + MenuDialog.KEY.asString() + ") : écran Échap "
                    + (pause ? "oui" : "non") + ", actions rapides " + (quick ? "oui" : "non") + ".");
        } catch (RuntimeException e) {
            getLogger().warning("Vérification du menu impossible : " + e);
        }
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMenuClick.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Réponses des boutons du menu (clé shdialogs:menu/...). Le client est une source non fiable : la clé est contrôlée ici,
     * puis la commande est retrouvée dans config.yml d'après l'identifiant du bouton.
     */
    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (!CLICK_NAMESPACE.equals(id.namespace())) {
            return;
        }
        try {
            if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
                return;
            }
            Player player = connection.getPlayer();
            String value = id.value();
            if (value.startsWith("menu/")) {
                String button = value.substring("menu/".length());
                sync(() -> onMenuClick(player, button));
            }
        } catch (RuntimeException e) {
            getLogger().warning("Clic de dialogue ignoré (" + id.asString() + ") : " + e);
        }
    }

    // ------------------------------------------------------------------ menu

    /**
     * Clic sur un bouton du menu SkyHand : la commande est retrouvée dans config.yml d'après l'identifiant du bouton,
     * jamais d'après ce que dit le client, et lancée par le joueur.
     */
    private void onMenuClick(Player player, String buttonId) {
        try {
            if (!player.isOnline()) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = lastMenuClick.put(player.getUniqueId(), now);
            if (last != null && now - last < MENU_CLICK_INTERVAL_MS) {
                return;
            }
            MenuConfig current = menu;
            MenuConfig.Button button = current.enabled() ? current.button(buttonId) : null;
            if (button == null || button.isUrl()) {
                messages.send(player, Messages.Type.ERREUR, "bouton-indisponible");
                return;
            }
            if (!button.permission().isEmpty() && !player.hasPermission(button.permission())) {
                messages.send(player, Messages.Type.ERREUR, "bouton-permission", "bouton", Texts.stripColors(button.label()));
                return;
            }
            if (CommandRunner.asPlayer(player, button.command()) == CommandRunner.Result.UNKNOWN) {
                messages.send(player, Messages.Type.ERREUR, "commande-echec", "commande", button.command());
            }
        } catch (RuntimeException e) {
            getLogger().warning("Bouton « " + buttonId + " » du menu impossible pour " + player.getName() + " : " + e);
            messages.send(player, Messages.Type.ERREUR, "erreur-interne");
        }
    }

    /**
     * Ouvre le dialogue du registre à un joueur (outil de test et de secours du staff).
     */
    private void showMenu(Player player) {
        Dialog dialog = menu.enabled() ? RegistryAccess.registryAccess().getRegistry(RegistryKey.DIALOG).get(MenuDialog.KEY) : null;
        if (dialog == null) {
            messages.send(player, Messages.Type.ERREUR, "menu-indisponible");
        } else if (!supportsDialogs(player, 771)) {
            messages.send(player, Messages.Type.ERREUR, "version-ancienne");
        } else {
            player.showDialog(dialog);
        }
    }

    // ------------------------------------------------------------------ commandes

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            commands.register("shdialogs", "Administration de SHDialogs", List.of(), new AdminCommand());
        });
    }

    /**
     * /shdialogs reload | menu
     */
    private final class AdminCommand implements BasicCommand {

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            try {
                String sub = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
                if (sub.equals("reload")) {
                    if (!sender.hasPermission(PERMISSION_ADMIN)) {
                        messages.send(sender, Messages.Type.ERREUR, "permission");
                        return;
                    }
                    loadConfiguration();
                    messages.send(sender, Messages.Type.SUCCES, "recharge");
                    if (!menu.signature().equals(registeredMenuSignature)) {
                        messages.send(sender, Messages.Type.INFO, "menu-redemarrage");
                    }
                } else if (sub.equals("menu")) {
                    if (!sender.hasPermission(PERMISSION_MENU)) {
                        messages.send(sender, Messages.Type.ERREUR, "permission");
                    } else if (sender instanceof Player player) {
                        showMenu(player);
                    } else {
                        messages.send(sender, Messages.Type.ERREUR, "joueur-seulement");
                    }
                } else {
                    messages.send(sender, Messages.Type.ERREUR, "usage");
                }
            } catch (RuntimeException e) {
                getLogger().warning("/shdialogs : " + e);
                messages.send(sender, Messages.Type.ERREUR, "erreur-interne");
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            if (args.length > 1) {
                return List.of();
            }
            String start = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> options = new ArrayList<>();
            CommandSender sender = source.getSender();
            if (sender.hasPermission(PERMISSION_ADMIN) && "reload".startsWith(start)) {
                options.add("reload");
            }
            if (sender.hasPermission(PERMISSION_MENU) && "menu".startsWith(start)) {
                options.add("menu");
            }
            return options;
        }
    }
}
