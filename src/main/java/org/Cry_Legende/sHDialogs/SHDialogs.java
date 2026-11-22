package org.Cry_Legende.sHDialogs;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class SHDialogs extends JavaPlugin implements Listener {

    static final String PERMISSION_RELOAD = "shdialogs.admin.reload";
    static final String PERMISSION_LIST = "shdialogs.admin.list";
    static final String PERMISSION_MENU = "shdialogs.menu";
    static final String PERMISSION_OPEN = "shdialogs.open";
    static final String PERMISSION_OPEN_OTHERS = "shdialogs.open.others";
    private static final String CLICK_NAMESPACE = "shdialogs";
    private static final long MENU_CLICK_INTERVAL_MS = 300;
    /** attendre-menu : un dialogue qui attend depuis plus longtemps n'est plus montré à la fermeture du menu. */
    private static final long WAIT_MENU_MS = 120_000;

    /**
     * Dialogue de la section « dialogues » affiché à un joueur : la version montrée (vide pour le dialogue de base) et ses
     * valeurs ({1}, {2}... et {joueur}). Le clic retrouve le bouton dans cette version, jamais d'après ce que dit le client.
     */
    private record Opening(String dialogueId, String version, Map<String, String> values) {
    }

    /**
     * Ce qu'une ouverture doit montrer : le dialogue et ses valeurs. La version et les textes sont choisis au moment d'afficher.
     */
    private record Request(DialogueConfig dialogue, Map<String, String> values) {
    }

    /**
     * Dialogue « attendre-menu » en attente de la fermeture du menu ouvert.
     */
    private record Waiting(Request request, long at) {
    }

    private final Map<UUID, Long> lastMenuClick = new ConcurrentHashMap<>();
    /** Dernier dialogue de la section « dialogues » affiché à chaque joueur : ses valeurs servent au clic. */
    private final Map<UUID, Opening> openings = new ConcurrentHashMap<>();
    private final Map<UUID, Waiting> waiting = new ConcurrentHashMap<>();
    private volatile Messages messages;
    private volatile MenuConfig menu;
    private volatile Map<String, DialogueConfig> dialogues = Map.of();
    private volatile Map<String, Dialog> dialogs = Map.of();
    private volatile List<DialogueRow> rows = List.of();
    /** Fichiers du dossier dialogues qui n'ont pas pu être lus au démarrage (« chemin : erreur »). */
    private volatile List<String> unreadableFiles = List.of();
    private volatile ConfigurationSection words;
    private String registeredMenuSignature = "";
    private boolean viaVersion;
    private boolean papi;
    private DialogBlindness blindness;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        viaVersion = getServer().getPluginManager().isPluginEnabled("ViaVersion");
        papi = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        blindness = new DialogBlindness(this, true);
        loadConfiguration();
        // ce que le bootstrap a enregistré au démarrage : base de comparaison pour /shdialogs reload
        registeredMenuSignature = menu.signature();
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
        diagnoseMenu();
        getLogger().info("SHDialogs prêt (placeholders " + (papi ? "PlaceholderAPI" : "sans PlaceholderAPI") + ", menu " + (menu.enabled() ? menu.buttons().size() + " boutons" : "désactivé") + ", "
                + dialogues.size() + " dialogue(s) : " + String.join(", ", dialogues.keySet()) + ").");
    }

    @Override
    public void onDisable() {
        lastMenuClick.clear();
        openings.clear();
        waiting.clear();
        if (blindness != null) {
            blindness.shutdown();
        }
    }

    /**
     * Démarrage : relit config.yml, messages.yml et le dossier dialogues sans rien exiger (un fichier illisible laisse les valeurs
     * du jar, un fichier de dialogues illisible est signalé et ignoré).
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
        DialogueFiles.Loaded files = DialogueFiles.read(getDataFolder().toPath());
        for (String error : files.errors()) {
            getLogger().severe("Dialogues illisibles, fichier ignoré : " + error);
        }
        unreadableFiles = files.errors();
        apply(getConfig(), messageFile, files.sources());
    }

    /**
     * /shdialogs reload : lit config.yml, messages.yml et les fichiers du dossier dialogues en mode strict avant de rien
     * remplacer. Renvoie null si tout est lu, sinon « fichier : erreur » et l'ancienne configuration reste en mémoire.
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
        DialogueFiles.Loaded files = DialogueFiles.read(getDataFolder().toPath());
        if (!files.errors().isEmpty()) {
            return files.errors().get(0);
        }
        unreadableFiles = List.of();
        apply(candidate, messageFile, files.sources());
        return null;
    }

    private static String describe(Exception e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        text = text.replaceAll("\\s*\\R\\s*", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }

    /**
     * Construit messages, menu, dialogues et dialogues Paper depuis la configuration lue (config.yml puis les fichiers du dossier
     * dialogues), puis les remplace d'un bloc.
     */
    private void apply(FileConfiguration config, ConfigurationSection messageFile, List<DialogueConfig.Source> files) {
        Consumer<String> warn = message -> getLogger().warning(message);
        Messages newMessages = new Messages(messageFile);
        MenuConfig newMenu = MenuConfig.read(config, warn);
        List<DialogueConfig.Source> sources = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("dialogues");
        if (section != null) {
            sources.add(new DialogueConfig.Source("config.yml", section));
        }
        sources.addAll(files);
        DialogueConfig.Loaded loaded = DialogueConfig.readAll(sources, config.getString("menu.bouton-fermer", "&c⤾ &fFermer"), warn);
        Map<String, String> failures = new HashMap<>();
        Map<String, Dialog> newDialogs = buildDialogs(loaded.dialogues(), failures, warn);
        List<DialogueRow> newRows = describeDialogues(loaded, failures);
        words = messageFile;
        messages = newMessages;
        menu = newMenu;
        dialogues = loaded.dialogues();
        dialogs = newDialogs;
        rows = newRows;
        blindness.setEnabled(config.getBoolean("aveugler-pendant-dialogues", true));
    }

    /**
     * Une ligne par dialogue lu : chargé, désactivé, en doublon ou en erreur (avec sa cause).
     */
    private List<DialogueRow> describeDialogues(DialogueConfig.Loaded loaded, Map<String, String> failures) {
        List<DialogueRow> result = new ArrayList<>();
        for (DialogueConfig.Entry entry : loaded.entries()) {
            DialogueConfig dialogue = loaded.dialogues().get(entry.id());
            switch (entry.state()) {
                case OK -> {
                    String failure = failures.get(entry.id());
                    if (failure != null) {
                        result.add(new DialogueRow(entry.id(), DialogueRow.State.ERREUR, "", 0, "", failure, entry.file()));
                    } else {
                        result.add(new DialogueRow(entry.id(), DialogueRow.State.OK, Texts.stripColors(String.join(" ", dialogue.title())),
                                dialogue.buttons().size(), dialogue.permission(), "", entry.file()));
                    }
                }
                case DESACTIVE -> result.add(new DialogueRow(entry.id(), DialogueRow.State.DESACTIVE, "", 0, "", "", entry.file()));
                case ERREUR -> result.add(new DialogueRow(entry.id(), DialogueRow.State.ERREUR, "", 0, "", entry.detail(), entry.file()));
                case DOUBLON -> result.add(new DialogueRow(entry.id(), DialogueRow.State.DOUBLON, "", 0, "", entry.detail(), entry.file()));
            }
        }
        return result;
    }

    /**
     * Ligne de /shdialogs list : {@code cause} est la cause d'une erreur, ou le fichier qui avait déjà ce dialogue pour un doublon.
     */
    private record DialogueRow(String id, State state, String title, int buttons, String permission, String cause, String file) {
        enum State {
            OK, DESACTIVE, ERREUR, DOUBLON
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
        for (DialogueRow row : rows) {
            if (row.state() == DialogueRow.State.DOUBLON) {
                messages.send(sender, Messages.Type.AVERTISSEMENT, "reload-doublon", "dialogue", row.id(), "fichier", row.file(),
                        "premier", row.cause());
            }
        }
        if (!menu.signature().equals(registeredMenuSignature)) {
            messages.send(sender, Messages.Type.INFO, "menu-redemarrage");
        }
    }

    /**
     * /shdialogs list : le menu et chaque dialogue lu (config.yml et dossier dialogues) avec son état.
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
        for (String error : unreadableFiles) {
            String[] parts = error.split(" : ", 2);
            messages.send(sender, Messages.Type.ERREUR, "list-fichier-illisible", "fichier", parts[0], "cause", parts.length > 1 ? parts[1] : "");
        }
        List<DialogueRow> current2 = rows;
        if (current2.isEmpty()) {
            messages.send(sender, Messages.Type.INFO, "list-vide");
            return;
        }
        for (DialogueRow row : current2) {
            switch (row.state()) {
                case OK -> messages.send(sender, Messages.Type.INFO, "list-dialogue", "dialogue", row.id(), "titre", row.title(),
                        "boutons", String.valueOf(row.buttons()), "permission", row.permission().isEmpty() ? word("aucune") : row.permission(),
                        "fichier", row.file());
                case DESACTIVE -> messages.send(sender, Messages.Type.INFO, "list-desactive", "dialogue", row.id());
                case ERREUR -> messages.send(sender, Messages.Type.ERREUR, "list-erreur", "dialogue", row.id(), "cause", row.cause());
                case DOUBLON -> messages.send(sender, Messages.Type.AVERTISSEMENT, "list-doublon", "dialogue", row.id(), "fichier", row.file(),
                        "premier", row.cause());
            }
        }
    }

    /**
     * Construit les dialogues Paper (le dialogue de base et chacune de ses versions). Un dialogue qui échoue est signalé dans la
     * console, noté dans {@code failures} et ne s'ouvre pas (le joueur reçoit un message d'erreur).
     */
    private Map<String, Dialog> buildDialogs(Map<String, DialogueConfig> configs, Map<String, String> failures, Consumer<String> warn) {
        Map<String, Dialog> built = new HashMap<>();
        for (DialogueConfig dialogue : configs.values()) {
            try {
                built.put(dialogue.id(), DialogueDialog.create(dialogue.variant("")));
                for (DialogueConfig.Variant variant : dialogue.variants()) {
                    DialogueDialog.create(variant.dialogue());
                }
            } catch (RuntimeException e) {
                String message = "Dialogue « " + dialogue.id() + " » non construit : " + e;
                warn.accept(message);
                failures.put(dialogue.id(), message);
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
     * Lance la tâche sur le scheduler du joueur, au tick qui suit après {@code delay} ticks (0 : dès que possible). Les mondes
     * tournent en parallèle : ce qui lit ou change l'inventaire, les effets ou l'écran d'un joueur s'exécute sur son propre thread.
     * Sans effet si le joueur a quitté ou si le plugin s'arrête.
     */
    /** Dernière ouverture demandée par joueur (voir open) : une plus ancienne encore en attente ne s'affiche pas. */
    private final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();
    private final AtomicLong openTickets = new AtomicLong();

    private void onPlayer(Player player, long delay, Runnable task) {
        try {
            if (delay <= 0) {
                player.getScheduler().run(this, scheduled -> task.run(), null);
            } else {
                player.getScheduler().runDelayed(this, scheduled -> task.run(), null, delay);
            }
        } catch (IllegalPluginAccessException e) {
            // plugin en cours d'arrêt
        }
    }

    /**
     * Le texte avec ses placeholders PlaceholderAPI résolus pour ce joueur (inchangé sans PlaceholderAPI). Les codes § d'un
     * résultat deviennent des &amp; : les textes du plugin n'utilisent que ceux-ci.
     */
    private String placeholders(Player player, String text) {
        if (!papi || text == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            return PapiSupport.set(player, text).replace('§', '&');
        } catch (RuntimeException | LinkageError e) {
            getLogger().warning("Placeholder non résolu pour " + player.getName() + " : " + e);
            return text;
        }
    }

    /**
     * Les valeurs du dialogue ({1}, {2}, {joueur}, {saisie}) puis les placeholders : ce qui sert aux conditions.
     */
    private UnaryOperator<String> resolver(Player player, Map<String, String> values) {
        return text -> placeholders(player, Texts.fillValues(text, values));
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

    /** Dernier « mets ton jeu à jour » par joueur : un clic de PNJ peut demander deux dialogues de suite. */
    private final Map<UUID, Long> lastOldClientNotice = new ConcurrentHashMap<>();
    private static final long OLD_CLIENT_NOTICE_MS = 3000;

    /**
     * Le dialogue ne peut pas s'afficher (client d'avant la 1.21.6) : le joueur est invité à mettre son jeu à jour, au plus
     * une fois toutes les OLD_CLIENT_NOTICE_MS, qu'il ait ouvert le dialogue lui-même ou qu'un PNJ, un menu ou la console
     * l'ait ouvert pour lui.
     */
    private void warnOldClient(Player target) {
        long now = System.currentTimeMillis();
        Long last = lastOldClientNotice.get(target.getUniqueId());
        if (last != null && now - last < OLD_CLIENT_NOTICE_MS) {
            return;
        }
        lastOldClientNotice.put(target.getUniqueId(), now);
        messages.send(target, Messages.Type.AVERTISSEMENT, "version-ancienne");
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
        UUID uuid = event.getPlayer().getUniqueId();
        lastMenuClick.remove(uuid);
        lastOldClientNotice.remove(uuid);
        openings.remove(uuid);
        waiting.remove(uuid);
    }

    /**
     * attendre-menu : le menu se ferme, le dialogue en attente s'affiche. Au tick suivant, pour laisser un autre menu s'ouvrir
     * à la place (navigation entre menus) : on attend alors sa fermeture à lui.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (waiting.isEmpty() || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Waiting pending = waiting.get(uuid);
        if (pending == null) {
            return;
        }
        if (event.getReason() == InventoryCloseEvent.Reason.DISCONNECT) {
            waiting.remove(uuid, pending);
            return;
        }
        onPlayer(player, 1, () -> {
            if (waiting.get(uuid) != pending || !player.isOnline()) {
                return;
            }
            if (System.currentTimeMillis() - pending.at() > WAIT_MENU_MS) {
                waiting.remove(uuid, pending);
                return;
            }
            if (player.getOpenInventory().getType() != InventoryType.CRAFTING) {
                return;
            }
            waiting.remove(uuid, pending);
            try {
                show(player, pending.request());
            } catch (RuntimeException e) {
                getLogger().warning("Dialogue « " + pending.request().dialogue().id() + " » non affiché à " + player.getName() + " : " + e);
            }
        });
    }

    /**
     * Bouton « fermer », bouton du bas ou Échap avec bouton du bas : le dialogue affiché ne sert plus.
     */
    @EventHandler
    public void onDialogClosed(PlayerCustomClickEvent event) {
        if (DialogBlindness.CLOSE_KEY.equals(event.getIdentifier()) && event.getCommonConnection() instanceof PlayerGameConnection connection) {
            openings.remove(connection.getPlayer().getUniqueId());
        }
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
                    // le texte du champ est lu ici, avec l'événement ; il est nettoyé au clic, d'après le champ du dialogue montré
                    String input = typedText(event);
                    onPlayer(player, 0, () -> onDialogueClick(player, parts[0], parts[1], input));
                }
            }
        } catch (RuntimeException e) {
            getLogger().warning("Clic de dialogue ignoré (" + id.asString() + ") : " + e);
        }
    }

    /**
     * Le texte que le client dit avoir tapé dans le champ du dialogue, tel quel : une source non fiable, nettoyée avant tout usage.
     */
    private static String typedText(PlayerCustomClickEvent event) {
        try {
            DialogResponseView view = event.getDialogResponseView();
            return view == null ? null : view.getText(DialogueDialog.INPUT_KEY);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ menu

    /**
     * Clic sur un bouton du menu SkyHand : la commande est retrouvée dans config.yml d'après l'identifiant du bouton,
     * jamais d'après ce que dit le client, et lancée par le joueur.
     */
    private void onMenuClick(Player player, String buttonId) {
        if (throttled(player)) {
            return;
        }
        MenuConfig current = menu;
        runButton(player, buttonId, current.enabled() ? current.button(buttonId) : null, Map.of("joueur", player.getName()));
    }

    /**
     * Clic sur un bouton de commande d'un dialogue de la section « dialogues » : mêmes contrôles que le menu, avec en plus la
     * permission du dialogue. Le bouton est cherché dans la version que le serveur a montrée à ce joueur, et {saisie} est le
     * texte du champ, nettoyé d'après le champ de cette version. S'exécute sur le scheduler du joueur.
     */
    private void onDialogueClick(Player player, String dialogueId, String buttonId, String typed) {
        if (throttled(player)) {
            return;
        }
        DialogueConfig dialogue = dialogues.get(dialogueId);
        if (dialogue != null && !dialogue.permission().isEmpty() && !player.hasPermission(dialogue.permission())) {
            messages.send(player, Messages.Type.ERREUR, "dialogue-permission");
            return;
        }
        Map<String, String> values = Map.of("joueur", player.getName());
        DialogueConfig shown = dialogue;
        boolean once = dialogue != null && (dialogue.trusted() || dialogue.valueCount() > 0);
        if (dialogue != null && (once || !dialogue.variants().isEmpty())) {
            // valeurs, version ou commandes de la console : le bouton ne vaut que pour ce dialogue ouvert à ce joueur par le
            // serveur. Avec des valeurs ou des commandes de la console, une seule fois (le client peut renvoyer la clé du clic
            // quand il veut)
            Opening opening = openings.get(player.getUniqueId());
            if (opening == null || !opening.dialogueId().equals(dialogueId) || (once && !openings.remove(player.getUniqueId(), opening))) {
                messages.send(player, Messages.Type.ERREUR, "bouton-indisponible");
                return;
            }
            values = opening.values();
            shown = dialogue.variant(opening.version());
        }
        Map<String, String> all = new HashMap<>(values);
        all.put("saisie", shown == null || shown.field() == null ? "" : Texts.sanitizeInput(typed, shown.field().maxLength()));
        runButton(player, buttonId, shown == null ? null : shown.button(buttonId), Map.copyOf(all));
    }

    /**
     * Deux clics à moins de MENU_CLICK_INTERVAL_MS : le second est ignoré.
     */
    private boolean throttled(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastMenuClick.put(player.getUniqueId(), now);
        return last != null && now - last < MENU_CLICK_INTERVAL_MS;
    }

    /**
     * Lance les actions du bouton dans l'ordre : commandes du joueur, de la console ([console]) et messages ([message]), avec
     * les valeurs du dialogue ({1}, {2}, {joueur} et {saisie}). Un bouton qui a une exigence ne les lance que si elle est remplie
     * (les objets sont alors retirés) ; sinon il lance ses lignes de refus.
     */
    private void runButton(Player player, String buttonId, MenuConfig.Button button, Map<String, String> values) {
        try {
            if (!player.isOnline()) {
                return;
            }
            if (button == null || button.isUrl() || button.close()) {
                messages.send(player, Messages.Type.ERREUR, "bouton-indisponible");
                return;
            }
            if (!button.permission().isEmpty() && !player.hasPermission(button.permission())) {
                messages.send(player, Messages.Type.ERREUR, "bouton-permission", "bouton", Texts.stripColors(button.label()));
                return;
            }
            List<MenuConfig.Action> actions = button.actions();
            MenuConfig.Requirement requirement = button.requirement();
            if (requirement != null) {
                // conditions, contrôle et retrait des objets : tout dans ce même tick, sur le scheduler du joueur
                boolean met = Condition.all(requirement.conditions(), resolver(player, values))
                        && (requirement.items().isEmpty() || ItemRequirement.take(player, requirement.items()));
                if (!met) {
                    actions = requirement.refusal();
                    if (actions.isEmpty()) {
                        messages.send(player, Messages.Type.ERREUR, "exigence-refusee");
                    }
                } else if (!requirement.items().isEmpty()) {
                    getLogger().info("Bouton « " + buttonId + " » de " + player.getName() + " : objets retirés ("
                            + requirement.items().stream().map(ItemRequirement.Item::text).collect(java.util.stream.Collectors.joining(", ")) + ").");
                }
            }
            for (MenuConfig.Action action : actions) {
                String text = Texts.fillValues(action.text(), values);
                switch (action.kind()) {
                    case PLAYER -> {
                        if (CommandRunner.asPlayer(player, text) == CommandRunner.Result.UNKNOWN) {
                            messages.send(player, Messages.Type.ERREUR, "commande-echec", "commande", text);
                        }
                    }
                    case CONSOLE -> {
                        getLogger().info("Bouton « " + buttonId + " » de " + player.getName() + " : [console] " + text);
                        if (!getServer().dispatchCommand(getServer().getConsoleSender(), text)) {
                            getLogger().warning("Commande inconnue du bouton « " + buttonId + " » : " + text);
                            messages.send(player, Messages.Type.ERREUR, "erreur-interne");
                        }
                    }
                    case MESSAGE -> player.sendMessage(Texts.legacy(text));
                }
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
            show(player, dialog, null);
        }
    }

    // ------------------------------------------------------------------ dialogues

    /**
     * /shdialogs open &lt;dialogue&gt; [joueur] [valeurs...] : ouvre un dialogue de la section « dialogues » à soi-même
     * (permission shdialogs.open, donnée à tous) ou à un autre joueur (shdialogs.open.others, pour la console et le staff).
     * Les valeurs remplacent {1}, {2}... du dialogue, la dernière prend le reste de la ligne. Elles passent dans les commandes
     * des boutons : comme un dialogue qui a un bouton [console], elles sont réservées à la console et au staff.
     */
    /** Délai le plus long de /shdialogs openlater : 10 secondes. */
    private static final int MAX_OPEN_DELAY = 200;

    /** Ticks de /shdialogs openlater (1 à MAX_OPEN_DELAY), 0 si le texte n'en est pas. */
    private static int ticks(String text) {
        try {
            int ticks = Integer.parseInt(text.trim());
            return ticks >= 1 && ticks <= MAX_OPEN_DELAY ? ticks : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void openCommand(CommandSender sender, String id, String targetName, String[] rawValues) {
        openCommand(sender, id, targetName, rawValues, 1);
    }

    /**
     * Ouvre un dialogue après delay ticks (1 pour /shdialogs open : au tick suivant).
     */
    private void openCommand(CommandSender sender, String id, String targetName, String[] rawValues, int delay) {
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
        int expected = dialogue.valueCount();
        if ((rawValues.length > 0 || expected > 0 || dialogue.trusted()) && !sender.hasPermission(PERMISSION_OPEN_OTHERS)) {
            messages.send(sender, Messages.Type.ERREUR, "permission");
            return;
        }
        if (rawValues.length < expected) {
            messages.send(sender, Messages.Type.ERREUR, "valeurs-manquantes", "dialogue", dialogue.id(), "attendues", String.valueOf(expected));
            return;
        }
        if (expected == 0 && rawValues.length > 0) {
            messages.send(sender, Messages.Type.ERREUR, "valeurs-inattendues", "dialogue", dialogue.id());
            return;
        }
        Map<String, String> values = new HashMap<>();
        values.put("joueur", target.getName());
        for (int i = 0; i < expected; i++) {
            values.put(String.valueOf(i + 1), i == expected - 1 ? String.join(" ", Arrays.copyOfRange(rawValues, i, rawValues.length)) : rawValues[i]);
        }
        if (!dialogue.permission().isEmpty() && !target.hasPermission(dialogue.permission())) {
            messages.send(sender, Messages.Type.ERREUR, "dialogue-permission");
            return;
        }
        if (!supportsDialogs(target, 771)) {
            // pas de repli en texte (les menus DeluxeMenus gardent leur texte classique) : le joueur est invité à mettre son
            // jeu à jour ; un membre du staff qui l'ouvre pour lui le sait aussi (la console, elle, n'en a rien à faire :
            // un clic de PNJ par un vieux client n'est pas une erreur)
            warnOldClient(target);
            if (target != sender && sender instanceof Player) {
                messages.send(sender, Messages.Type.AVERTISSEMENT, "version-ancienne-autre", "joueur", target.getName());
            }
            return;
        }
        if (!dialogs.containsKey(dialogue.id())) {
            messages.send(sender, Messages.Type.ERREUR, "erreur-interne");
            return;
        }
        open(sender, target, new Request(dialogue, Map.copyOf(values)), delay);
        if (target != sender && sender instanceof Player) {
            messages.send(sender, Messages.Type.SUCCES, "ouvert", "dialogue", dialogue.id(), "joueur", target.getName());
        }
    }

    private void open(CommandSender sender, Player target, Request request) {
        open(sender, target, request, 1);
    }

    /**
     * Ouvre le dialogue après delay ticks. Seule la dernière ouverture demandée pour un joueur s'affiche : un dialogue
     * attendu (openlater, le temps de l'animation d'un PNJ par exemple) ne s'ouvre pas par-dessus un autre demandé après lui,
     * et deux clics rapides n'en ouvrent qu'un.
     */
    private void open(CommandSender sender, Player target, Request request, int delay) {
        long ticket = openTickets.incrementAndGet();
        lastOpen.put(target.getUniqueId(), ticket);
        // au tick suivant au plus tôt : la commande est souvent lancée depuis l'événement de clic d'un menu DeluxeMenus,
        // qu'on ne ferme pas ici
        onPlayer(target, Math.max(1, delay), () -> {
            if (!target.isOnline() || !Long.valueOf(ticket).equals(lastOpen.get(target.getUniqueId()))) {
                return;
            }
            lastOpen.remove(target.getUniqueId(), ticket);
            try {
                if (target.getOpenInventory().getType() != InventoryType.CRAFTING) {
                    if (request.dialogue().waitMenu()) {
                        // attendre-menu : le joueur regarde encore un menu (le lot d'une box...), le dialogue s'affichera à sa fermeture
                        waiting.put(target.getUniqueId(), new Waiting(request, System.currentTimeMillis()));
                        return;
                    }
                    // Les menus n'ont plus de [close] (il laissait un instant sans écran entre le menu et le dialogue) : le menu
                    // encore ouvert est fermé ici, juste avant le dialogue. Les deux paquets partent dans le même tick, le client
                    // les traite dans la même image : pas de clignotement, et plus de menu fantôme ouvert côté serveur.
                    target.closeInventory();
                }
                show(target, request);
            } catch (RuntimeException e) {
                getLogger().warning("Dialogue non affiché à " + target.getName() + " : " + e);
                messages.send(sender, Messages.Type.ERREUR, "erreur-interne");
            }
        });
    }

    /**
     * Affiche un dialogue de la section « dialogues » à ce joueur. La version est choisie ici, à l'ouverture, d'après ses
     * conditions (valeurs du dialogue puis placeholders, pour ce joueur), et les textes reçoivent leurs valeurs et placeholders.
     * Le serveur retient la version montrée : au clic, le bouton est cherché dans celle-là.
     */
    private void show(Player player, Request request) {
        DialogueConfig base = request.dialogue();
        UnaryOperator<String> resolve = resolver(player, request.values());
        DialogueConfig chosen = base.select(resolve);
        Dialog dialog;
        if (base.dynamic()) {
            dialog = DialogueDialog.create(chosen.rendered(resolve));
        } else {
            dialog = dialogs.get(base.id());
            if (dialog == null) {
                throw new IllegalStateException("dialogue non construit");
            }
        }
        show(player, dialog, new Opening(base.id(), chosen.version(), request.values()));
    }

    /**
     * Affiche un dialogue et retient ses valeurs pour le clic (null : un dialogue sans valeurs, le menu par exemple). Un
     * dialogue en attente de la fermeture d'un menu est oublié : celui-ci le remplace.
     */
    private void show(Player player, Dialog dialog, Opening opening) {
        UUID uuid = player.getUniqueId();
        waiting.remove(uuid);
        if (opening == null) {
            openings.remove(uuid);
        } else {
            openings.put(uuid, opening);
        }
        blindness.show(player, dialog);
    }

    // ------------------------------------------------------------------ commandes

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            commands.register("shdialogs", "Administration de SHDialogs", List.of(), new AdminCommand());
        });
    }

    /**
     * /shdialogs reload | list | menu | open &lt;dialogue&gt; [joueur] [valeurs...]
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
            if (sender.hasPermission(PERMISSION_OPEN_OTHERS)) {
                subs.add("openlater");
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
                } else if (sub.equals("open") && args.length >= 2) {
                    openCommand(sender, args[1], args.length >= 3 ? args[2] : null,
                            args.length > 3 ? Arrays.copyOfRange(args, 3, args.length) : new String[0]);
                } else if (sub.equals("open")) {
                    messages.send(sender, Messages.Type.ERREUR, "open-usage");
                } else if (sub.equals("openlater") && args.length >= 4 && ticks(args[1]) > 0) {
                    if (!sender.hasPermission(PERMISSION_OPEN_OTHERS)) {
                        messages.send(sender, Messages.Type.ERREUR, "permission");
                        return;
                    }
                    openCommand(sender, args[2], args[3], args.length > 4 ? Arrays.copyOfRange(args, 4, args.length) : new String[0],
                            ticks(args[1]));
                } else if (sub.equals("openlater")) {
                    messages.send(sender, Messages.Type.ERREUR, "openlater-usage");
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
            if (args.length >= 2 && args[0].equalsIgnoreCase("openlater")) {
                // openlater <ticks> <dialogue> <joueur> : mêmes propositions que open, un rang plus loin
                if (args.length == 2) {
                    return List.of("20", "40");
                }
                args = Arrays.copyOfRange(args, 1, args.length);
                args[0] = "open";
            }
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
