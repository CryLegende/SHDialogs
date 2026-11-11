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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class SHDialogs extends JavaPlugin implements Listener {

    static final String PERMISSION_RELOAD = "shdialogs.admin.reload";
    static final String PERMISSION_LIST = "shdialogs.admin.list";
    static final String PERMISSION_MENU = "shdialogs.menu";
    static final String PERMISSION_OPEN = "shdialogs.open";
    static final String PERMISSION_OPEN_OTHERS = "shdialogs.open.others";
    private static final String CLICK_NAMESPACE = "shdialogs";
    private static final long MENU_CLICK_INTERVAL_MS = 300;

    private final Map<UUID, Long> lastMenuClick = new ConcurrentHashMap<>();
    private volatile Messages messages;
    private volatile MenuConfig menu;
    private volatile Map<String, DialogueConfig> dialogues = Map.of();
    private volatile Map<String, Dialog> dialogs = Map.of();
    private volatile List<DialogueRow> rows = List.of();
    private volatile ConfigurationSection words;
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
        getLogger().info("SHDialogs prêt (menu " + (menu.enabled() ? menu.buttons().size() + " boutons" : "désactivé") + ", "
                + dialogues.size() + " dialogue(s) : " + String.join(", ", dialogues.keySet()) + ").");
    }

    @Override
    public void onDisable() {
        lastMenuClick.clear();
    }

    /**
     * Démarrage : relit config.yml et messages.yml sans rien exiger (un fichier illisible laisse les valeurs du jar).
     * Le dialogue du menu, lui, reste celui du démarrage : seules les commandes, permissions et messages changent ensuite.
     */
    private void loadConfiguration() {
        reloadConfig();
        YamlConfiguration messageFile;
        try {
            messageFile = MessagesFile.read(this);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("messages.yml illisible, textes du jar utilisés : " + describe(e));
            messageFile = MessagesFile.jarOnly(this);
        }
        apply(getConfig(), messageFile);
    }

    /**
     * /shdialogs reload : lit config.yml et messages.yml en mode strict avant de rien remplacer. Renvoie null si tout est
     * lu, sinon « fichier : erreur » et l'ancienne configuration reste en mémoire.
     */
    private String reloadAll() {
        FileConfiguration candidate;
        try {
            candidate = ConfigFiles.loadStrict(getDataFolder().toPath());
        } catch (IOException | InvalidConfigurationException e) {
            return "config.yml : " + describe(e);
        }
        YamlConfiguration messageFile;
        try {
            messageFile = MessagesFile.read(this);
        } catch (IOException | InvalidConfigurationException e) {
            return MessagesFile.NAME + " : " + describe(e);
        }
        apply(candidate, messageFile);
        return null;
    }

    private static String describe(Exception e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        text = text.replaceAll("\\s*\\R\\s*", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }

    /**
     * Construit messages, menu, dialogues et dialogues Paper depuis la configuration lue, puis les remplace d'un bloc.
     */
    private void apply(FileConfiguration config, ConfigurationSection messageFile) {
        List<String> problems = new ArrayList<>();
        Consumer<String> warn = message -> {
            getLogger().warning(message);
            problems.add(message);
        };
        Messages newMessages = new Messages(messageFile);
        MenuConfig newMenu = MenuConfig.read(config, warn);
        Map<String, DialogueConfig> newDialogues = DialogueConfig.readAll(config, config.getString("menu.bouton-fermer", "&c⤾ &fFermer"), warn);
        Map<String, Dialog> newDialogs = buildDialogs(newDialogues, warn);
        List<DialogueRow> newRows = describeDialogues(config, newDialogues, newDialogs, problems);
        words = messageFile;
        messages = newMessages;
        menu = newMenu;
        dialogues = newDialogues;
        dialogs = newDialogs;
        rows = newRows;
    }

    /**
     * Une ligne par dialogue de la section « dialogues » : chargé, désactivé ou en erreur (avec sa cause).
     */
    private List<DialogueRow> describeDialogues(FileConfiguration config, Map<String, DialogueConfig> parsed, Map<String, Dialog> built,
                                                List<String> problems) {
        List<DialogueRow> result = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("dialogues");
        if (section == null) {
            return result;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection d = section.getConfigurationSection(id);
            if (d == null) {
                continue;
            }
            if (!d.getBoolean("actif", true)) {
                result.add(new DialogueRow(id, DialogueRow.State.DESACTIVE, "", 0, "", ""));
            } else if (built.containsKey(id) && parsed.containsKey(id)) {
                DialogueConfig dialogue = parsed.get(id);
                result.add(new DialogueRow(id, DialogueRow.State.OK, Texts.stripColors(dialogue.title()), dialogue.buttons().size(),
                        dialogue.permission(), ""));
            } else {
                String marker = "« " + id + " »";
                String cause = problems.stream().filter(problem -> problem.contains(marker)).findFirst().orElse("non construit");
                result.add(new DialogueRow(id, DialogueRow.State.ERREUR, "", 0, "", cause));
            }
        }
        return result;
    }

    /**
     * Ligne de /shdialogs list.
     */
    private record DialogueRow(String id, State state, String title, int buttons, String permission, String cause) {
        enum State {
            OK, DESACTIVE, ERREUR
        }
    }

    /**
     * Texte de la section « mots » de messages.yml (oui, non, aucune...), sans préfixe.
     */
    private String word(String key) {
        return words == null ? key : words.getString("mots." + key, key);
    }

    private void reloadCommand(CommandSender sender) {
        String error = reloadAll();
        if (error != null) {
            String[] parts = error.split(" : ", 2);
            messages.send(sender, Messages.Type.ERREUR, "reload-erreur", "fichier", parts[0], "erreur", parts.length > 1 ? parts[1] : "");
            return;
        }
        messages.send(sender, Messages.Type.SUCCES, "recharge");
        long loaded = rows.stream().filter(row -> row.state() == DialogueRow.State.OK).count();
        long failed = rows.stream().filter(row -> row.state() == DialogueRow.State.ERREUR).count();
        messages.send(sender, failed > 0 ? Messages.Type.AVERTISSEMENT : Messages.Type.INFO, "reload-bilan",
                "charges", String.valueOf(loaded), "erreurs", String.valueOf(failed));
        if (!menu.signature().equals(registeredMenuSignature)) {
            messages.send(sender, Messages.Type.INFO, "menu-redemarrage");
        }
    }

    /**
     * /shdialogs list : le menu et chaque dialogue de la section « dialogues » avec son état.
     */
    private void listCommand(CommandSender sender) {
        MenuConfig current = menu;
        messages.send(sender, Messages.Type.INFO, "list-titre");
        if (current.enabled()) {
            messages.send(sender, Messages.Type.INFO, "list-menu", "boutons", String.valueOf(current.buttons().size()),
                    "echap", word(current.pauseScreen() ? "oui" : "non"), "rapides", word(current.quickActions() ? "oui" : "non"));
        } else {
            messages.send(sender, Messages.Type.INFO, "list-menu-desactive");
        }
        List<DialogueRow> current2 = rows;
        if (current2.isEmpty()) {
            messages.send(sender, Messages.Type.INFO, "list-vide");
            return;
        }
        for (DialogueRow row : current2) {
            switch (row.state()) {
                case OK -> messages.send(sender, Messages.Type.INFO, "list-dialogue", "dialogue", row.id(), "titre", row.title(),
                        "boutons", String.valueOf(row.buttons()), "permission", row.permission().isEmpty() ? word("aucune") : row.permission());
                case DESACTIVE -> messages.send(sender, Messages.Type.INFO, "list-desactive", "dialogue", row.id());
                case ERREUR -> messages.send(sender, Messages.Type.ERREUR, "list-erreur", "dialogue", row.id(), "cause", row.cause());
            }
        }
    }

    /**
     * Construit les dialogues Paper de la section « dialogues ». Un dialogue qui échoue est signalé dans la console et ne
     * s'ouvre pas (le joueur reçoit un message d'erreur).
     */
    private Map<String, Dialog> buildDialogs(Map<String, DialogueConfig> configs, Consumer<String> warn) {
        Map<String, Dialog> built = new HashMap<>();
        for (DialogueConfig dialogue : configs.values()) {
            try {
                built.put(dialogue.id(), DialogueDialog.create(dialogue));
            } catch (RuntimeException e) {
                warn.accept("Dialogue « " + dialogue.id() + " » non construit : " + e);
            }
        }
        return Map.copyOf(built);
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
            } else if (value.startsWith("dialogue/")) {
                String[] parts = value.substring("dialogue/".length()).split("/", 2);
                if (parts.length == 2) {
                    sync(() -> onDialogueClick(player, parts[0], parts[1]));
                }
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
        MenuConfig current = menu;
        runButton(player, buttonId, current.enabled() ? current.button(buttonId) : null);
    }

    /**
     * Clic sur un bouton de commande d'un dialogue de la section « dialogues » : mêmes contrôles que le menu, avec en plus la
     * permission du dialogue.
     */
    private void onDialogueClick(Player player, String dialogueId, String buttonId) {
        DialogueConfig dialogue = dialogues.get(dialogueId);
        if (dialogue != null && !dialogue.permission().isEmpty() && !player.hasPermission(dialogue.permission())) {
            messages.send(player, Messages.Type.ERREUR, "dialogue-permission");
            return;
        }
        runButton(player, buttonId, dialogue == null ? null : dialogue.button(buttonId));
    }

    private void runButton(Player player, String buttonId, MenuConfig.Button button) {
        try {
            if (!player.isOnline()) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = lastMenuClick.put(player.getUniqueId(), now);
            if (last != null && now - last < MENU_CLICK_INTERVAL_MS) {
                return;
            }
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

    // ------------------------------------------------------------------ dialogues

    /**
     * /shdialogs open &lt;dialogue&gt; [joueur] : ouvre un dialogue de la section « dialogues » à soi-même (permission
     * shdialogs.open, donnée à tous) ou à un autre joueur (shdialogs.open.others, pour la console et le staff).
     */
    private void openCommand(CommandSender sender, String id, String targetName) {
        if (targetName != null && sender instanceof Player self && self.getName().equalsIgnoreCase(targetName)) {
            targetName = null;
        }
        Player target;
        if (targetName == null) {
            if (!(sender instanceof Player self)) {
                messages.send(sender, Messages.Type.ERREUR, "console-joueur");
                return;
            }
            if (!sender.hasPermission(PERMISSION_OPEN)) {
                messages.send(sender, Messages.Type.ERREUR, "permission");
                return;
            }
            target = self;
        } else {
            if (!sender.hasPermission(PERMISSION_OPEN_OTHERS)) {
                messages.send(sender, Messages.Type.ERREUR, "permission");
                return;
            }
            target = getServer().getPlayerExact(targetName);
            if (target == null) {
                messages.send(sender, Messages.Type.ERREUR, "joueur-introuvable", "joueur", targetName);
                return;
            }
        }
        DialogueConfig dialogue = dialogues.get(id.toLowerCase(Locale.ROOT));
        if (dialogue == null) {
            messages.send(sender, Messages.Type.ERREUR, "dialogue-inconnu", "dialogue", id);
            return;
        }
        if (!dialogue.permission().isEmpty() && !target.hasPermission(dialogue.permission())) {
            messages.send(sender, Messages.Type.ERREUR, "dialogue-permission");
            return;
        }
        if (!supportsDialogs(target, 771)) {
            // pas de repli : le menu DeluxeMenus garde son texte classique ; l'avertissement va à celui qui a lancé la commande
            // (le joueur lui-même, le staff ou la console), jamais à la cible
            if (target == sender) {
                messages.send(sender, Messages.Type.AVERTISSEMENT, "version-ancienne");
            } else {
                messages.send(sender, Messages.Type.AVERTISSEMENT, "version-ancienne-autre", "joueur", target.getName());
            }
            return;
        }
        Dialog dialog = dialogs.get(dialogue.id());
        if (dialog == null) {
            messages.send(sender, Messages.Type.ERREUR, "erreur-interne");
            return;
        }
        open(sender, target, dialog);
        if (target != sender && sender instanceof Player) {
            messages.send(sender, Messages.Type.SUCCES, "ouvert", "dialogue", dialogue.id(), "joueur", target.getName());
        }
    }

    private void open(CommandSender sender, Player target, Dialog dialog) {
        // au tick suivant : la commande est souvent lancée depuis l'événement de clic d'un menu DeluxeMenus, qu'on ne ferme pas ici
        getServer().getScheduler().runTask(this, () -> {
            if (!target.isOnline()) {
                return;
            }
            try {
                // Les menus n'ont plus de [close] (il laissait un instant sans écran entre le menu et le dialogue) : le menu encore
                // ouvert est fermé ici, juste avant le dialogue. Les deux paquets partent dans le même tick, le client les traite
                // dans la même image : pas de clignotement, et plus de menu fantôme ouvert côté serveur.
                if (target.getOpenInventory().getType() != InventoryType.CRAFTING) {
                    target.closeInventory();
                }
                target.showDialog(dialog);
            } catch (RuntimeException e) {
                getLogger().warning("Dialogue non affiché à " + target.getName() + " : " + e);
                messages.send(sender, Messages.Type.ERREUR, "erreur-interne");
            }
        });
    }

    // ------------------------------------------------------------------ commandes

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            commands.register("shdialogs", "Administration de SHDialogs", List.of(), new AdminCommand());
        });
    }

    /**
     * /shdialogs reload | list | menu | open &lt;dialogue&gt; [joueur]
     */
    private final class AdminCommand implements BasicCommand {

        /** Sous-commandes que l'expéditeur a le droit d'utiliser. */
        private List<String> allowed(CommandSender sender) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission(PERMISSION_RELOAD)) {
                subs.add("reload");
            }
            if (sender.hasPermission(PERMISSION_LIST)) {
                subs.add("list");
            }
            if (sender.hasPermission(PERMISSION_MENU)) {
                subs.add("menu");
            }
            if (sender.hasPermission(PERMISSION_OPEN) || sender.hasPermission(PERMISSION_OPEN_OTHERS)) {
                subs.add("open");
            }
            return subs;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            try {
                String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "";
                if (sub.equals("reload") && args.length == 1) {
                    if (!sender.hasPermission(PERMISSION_RELOAD)) {
                        messages.send(sender, Messages.Type.ERREUR, "permission");
                        return;
                    }
                    reloadCommand(sender);
                } else if (sub.equals("list") && args.length == 1) {
                    if (!sender.hasPermission(PERMISSION_LIST)) {
                        messages.send(sender, Messages.Type.ERREUR, "permission");
                        return;
                    }
                    listCommand(sender);
                } else if (sub.equals("menu") && args.length == 1) {
                    if (!sender.hasPermission(PERMISSION_MENU)) {
                        messages.send(sender, Messages.Type.ERREUR, "permission");
                    } else if (sender instanceof Player player) {
                        showMenu(player);
                    } else {
                        messages.send(sender, Messages.Type.ERREUR, "joueur-seulement");
                    }
                } else if (sub.equals("open") && (args.length == 2 || args.length == 3)) {
                    openCommand(sender, args[1], args.length == 3 ? args[2] : null);
                } else if (sub.equals("open")) {
                    messages.send(sender, Messages.Type.ERREUR, "open-usage");
                } else {
                    List<String> subs = allowed(sender);
                    if (subs.isEmpty()) {
                        messages.send(sender, Messages.Type.ERREUR, "permission");
                    } else {
                        messages.send(sender, Messages.Type.ERREUR, "usage-liste", "sous-commandes", String.join("|", subs));
                    }
                }
            } catch (RuntimeException e) {
                getLogger().warning("/shdialogs : " + e);
                messages.send(sender, Messages.Type.ERREUR, "erreur-interne");
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            List<String> options = new ArrayList<>();
            if (args.length >= 2 && args[0].equalsIgnoreCase("open")) {
                String start = args[args.length - 1].toLowerCase(Locale.ROOT);
                if (args.length == 2 && sender.hasPermission(PERMISSION_OPEN)) {
                    for (String id : dialogues.keySet()) {
                        if (id.startsWith(start)) {
                            options.add(id);
                        }
                    }
                } else if (args.length == 3 && sender.hasPermission(PERMISSION_OPEN_OTHERS)) {
                    for (Player online : getServer().getOnlinePlayers()) {
                        if (online.getName().toLowerCase(Locale.ROOT).startsWith(start)) {
                            options.add(online.getName());
                        }
                    }
                }
                return options;
            }
            if (args.length > 1) {
                return List.of();
            }
            String start = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            for (String sub : allowed(sender)) {
                if (sub.startsWith(start)) {
                    options.add(sub);
                }
            }
            return options;
        }
    }
}
