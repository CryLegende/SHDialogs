package org.Cry_Legende.sHDialogs;

import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /signaler : ouverture du formulaire, puis validation de la réponse et transmission à TigerReports.
 * <p>
 * Le client est une source non fiable : n'importe qui peut forger la réponse d'un bouton avec la clé, les champs et le jeton
 * qu'il veut. Une réponse n'est donc prise en compte que si le joueur a un formulaire en attente, que le jeton reçu est celui de
 * ce formulaire et qu'il n'a pas expiré ; le jeton est consommé d'un seul coup (usage unique). Ensuite chaque champ est revérifié
 * côté serveur, sans jamais se fier aux limites annoncées au client.
 */
final class ReportService {

    static final String PERMISSION = "shdialogs.signaler";
    private static final long REFUSAL_MESSAGE_INTERVAL_MS = 5000;

    /**
     * Formulaire ouvert chez un joueur et pas encore répondu.
     */
    private record Pending(String token, long createdAt) {
    }

    /**
     * Résultat de la validation d'une réponse : soit une erreur à afficher dans le formulaire, soit les valeurs à transmettre.
     */
    private record Checked(String error, String target, ReportSettings.Motif motif, String details, String reason) {
        static Checked failure(String error) {
            return new Checked(error, null, null, null, null);
        }
    }

    private final SHDialogs plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRefusal = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private volatile ReportSettings settings;
    private volatile Messages messages;

    ReportService(SHDialogs plugin) {
        this.plugin = plugin;
    }

    void configure(ReportSettings settings, Messages messages) {
        this.settings = settings;
        this.messages = messages;
    }

    boolean enabled() {
        return settings != null && settings.enabled();
    }

    /**
     * Libère ce qui concerne un joueur parti (le délai anti-spam, lui, reste jusqu'à son terme).
     */
    void forget(UUID player) {
        pending.remove(player);
        lastRefusal.remove(player);
    }

    void clear() {
        pending.clear();
        lastSent.clear();
        lastRefusal.clear();
    }

    // ------------------------------------------------------------------ ouverture

    /**
     * /signaler [pseudo] : ouvre le formulaire, ou explique pourquoi il ne s'ouvre pas.
     */
    void open(Player player, String presetTarget) {
        try {
            ReportSettings s = settings;
            if (!s.enabled()) {
                messages.send(player, Messages.Type.ERREUR, "signalement-desactive");
                return;
            }
            if (!plugin.supportsDialogs(player, s.minProtocol())) {
                player.sendMessage(messages.get(Messages.Type.ERREUR, "version-ancienne")
                        .clickEvent(ClickEvent.suggestCommand("/report ")));
                return;
            }
            if (!CommandRunner.exists(s.command())) {
                messages.send(player, Messages.Type.ERREUR, "signalement-indisponible");
                return;
            }
            long wait = remainingSeconds(player, s);
            if (wait > 0) {
                messages.send(player, Messages.Type.ERREUR, "cooldown", "secondes", String.valueOf(wait));
                return;
            }
            String preset = presetTarget == null ? "" : presetTarget.trim();
            // un pseudo passé en argument est seulement prérempli : il est revérifié à l'envoi comme les autres
            if (preset.length() > s.nameMax()) {
                preset = "";
            }
            show(player, new ReportDialog.Form(preset, null, ""), null);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("/signaler impossible pour " + player.getName() + " : " + e);
            messages.send(player, Messages.Type.ERREUR, "erreur-interne");
        }
    }

    /**
     * Affiche le formulaire avec un jeton neuf (l'ancien, s'il y en avait un, cesse de valoir).
     */
    private void show(Player player, ReportDialog.Form form, String error) {
        UUID id = player.getUniqueId();
        if (pending.size() > 500) {
            long limit = System.currentTimeMillis() - settings.expirationSeconds() * 1000L;
            pending.values().removeIf(p -> p.createdAt() < limit);
        }
        Pending created = new Pending(newToken(), System.currentTimeMillis());
        pending.put(id, created);
        try {
            player.showDialog(ReportDialog.build(settings, form, error, created.token()));
        } catch (RuntimeException e) {
            pending.remove(id, created);
            plugin.getLogger().warning("Formulaire de signalement impossible pour " + player.getName() + " : " + e);
            player.closeDialog();
            messages.send(player, Messages.Type.ERREUR, "ouverture-impossible");
        }
    }

    private String newToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    // ------------------------------------------------------------------ réponses

    /**
     * Bouton « Envoyer ». Appelé depuis l'événement de clic, peut-être hors du fil principal : seule la consommation du jeton
     * et la lecture des champs se font ici, le reste passe par le fil principal.
     */
    void onSend(Player player, DialogResponseView view) {
        if (!takePending(player, view)) {
            plugin.sync(() -> refuse(player));
            return;
        }
        ReportDialog.Form form = new ReportDialog.Form(text(view, ReportDialog.FIELD_TARGET), text(view, ReportDialog.FIELD_MOTIF),
                text(view, ReportDialog.FIELD_DETAILS));
        plugin.sync(() -> process(player, form));
    }

    /**
     * Bouton « Annuler ».
     */
    void onCancel(Player player, DialogResponseView view) {
        boolean valid = takePending(player, view);
        plugin.sync(() -> {
            // dans tous les cas on referme : l'écran du client attend une réponse du serveur
            player.closeDialog();
            if (!valid) {
                refuse(player);
            }
        });
    }

    /**
     * Consomme le formulaire en attente si le jeton reçu est le bon et n'a pas expiré. Atomique : deux réponses simultanées avec
     * le même jeton (double clic, rejeu) n'en font passer qu'une.
     */
    private boolean takePending(Player player, DialogResponseView view) {
        UUID id = player.getUniqueId();
        Pending current = pending.get(id);
        String token = text(view, ReportDialog.FIELD_TOKEN);
        if (current == null || token == null) {
            return false;
        }
        boolean same = MessageDigest.isEqual(current.token().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        if (!same) {
            return false;
        }
        if (System.currentTimeMillis() - current.createdAt() > settings.expirationSeconds() * 1000L) {
            pending.remove(id, current);
            return false;
        }
        return pending.remove(id, current);
    }

    /**
     * Valeur d'un champ, ou null si absente ou de mauvais type (une réponse forgée peut contenir n'importe quoi).
     */
    private String text(DialogResponseView view, String key) {
        if (view == null) {
            return null;
        }
        try {
            return view.getText(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Réponse sans formulaire en attente (ou périmé, ou au mauvais jeton) : on referme l'écran et on le dit, au plus une fois
     * toutes les quelques secondes pour qu'un client qui boucle ne remplisse pas le chat.
     */
    private void refuse(Player player) {
        if (!player.isOnline()) {
            return;
        }
        player.closeDialog();
        long now = System.currentTimeMillis();
        Long last = lastRefusal.get(player.getUniqueId());
        if (last == null || now - last >= REFUSAL_MESSAGE_INTERVAL_MS) {
            lastRefusal.put(player.getUniqueId(), now);
            messages.send(player, Messages.Type.ERREUR, "formulaire-expire");
        }
    }

    // ------------------------------------------------------------------ traitement

    private void process(Player player, ReportDialog.Form form) {
        try {
            if (!player.isOnline()) {
                return;
            }
            ReportSettings s = settings;
            if (!s.enabled()) {
                end(player, "signalement-desactive");
                return;
            }
            if (!player.hasPermission(PERMISSION)) {
                end(player, "permission");
                return;
            }
            long wait = remainingSeconds(player, s);
            if (wait > 0) {
                end(player, "cooldown", "secondes", String.valueOf(wait));
                return;
            }
            Checked checked = validate(player, s, form);
            if (checked.error() != null) {
                // formulaire de nouveau affiché avec l'erreur : ce que le joueur a saisi est conservé
                show(player, form, checked.error());
                return;
            }
            String command = Texts.fill(s.command(), Texts.vars("joueur", checked.target(), "raison", checked.reason(),
                    "motif", checked.motif().reason(), "details", checked.details()));
            CommandRunner.Result result = CommandRunner.asPlayer(player, command);
            if (result == CommandRunner.Result.UNKNOWN) {
                end(player, "signalement-indisponible");
                return;
            }
            if (result == CommandRunner.Result.EXECUTED) {
                lastSent.put(player.getUniqueId(), System.currentTimeMillis());
                plugin.getLogger().info("Signalement par formulaire : " + player.getName() + " -> " + checked.target() + " [" + checked.reason() + "]");
            }
            // TigerReports répond lui-même au joueur (signalement enregistré, ou raison du refus)
            player.closeDialog();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Signalement impossible pour " + player.getName() + " : " + e);
            end(player, "erreur-interne");
        }
    }

    /**
     * Referme le formulaire et explique pourquoi on s'arrête là.
     */
    private void end(Player player, String key, String... pairs) {
        player.closeDialog();
        messages.send(player, Messages.Type.ERREUR, key, pairs);
    }

    private Checked validate(Player player, ReportSettings s, ReportDialog.Form form) {
        String target = form.target() == null ? "" : form.target().trim();
        if (target.isEmpty()) {
            return Checked.failure(s.error("pseudo-vide"));
        }
        if (target.length() > s.nameMax() || !s.namePattern().matcher(target).matches()) {
            return Checked.failure(s.error("pseudo-invalide"));
        }
        if (target.equalsIgnoreCase(player.getName())) {
            return Checked.failure(s.error("soi-meme"));
        }
        String known = resolve(target);
        if (known == null && s.refuseUnknown()) {
            return Checked.failure(s.error("pseudo-inconnu", "joueur", target));
        }
        if (known != null) {
            if (known.equalsIgnoreCase(player.getName())) {
                return Checked.failure(s.error("soi-meme"));
            }
            target = known;
        }
        ReportSettings.Motif motif = s.motif(form.motif());
        if (motif == null) {
            return Checked.failure(s.error("motif-invalide"));
        }
        String rawDetails = form.details() == null ? "" : form.details();
        if (rawDetails.length() > s.detailsMax()) {
            return Checked.failure(s.error("details-trop-longs", "max", String.valueOf(s.detailsMax())));
        }
        String details = cleanDetails(rawDetails);
        String reason = details.isEmpty() ? motif.reason()
                : Texts.fill(s.reasonFormat(), Texts.vars("motif", motif.reason(), "details", details));
        if (reason.length() > s.reasonMax()) {
            return Checked.failure(s.error("raison-trop-longue"));
        }
        return new Checked(null, target, motif, details, reason);
    }

    /**
     * Pseudo exact d'un joueur connecté ou déjà venu sur ce serveur, sinon null. Rien ne part sur le réseau :
     * getOfflinePlayerIfCached ne consulte que le cache local.
     */
    private String resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null && offline.hasPlayedBefore() && offline.getName() != null) {
            return offline.getName();
        }
        return null;
    }

    /**
     * Les détails finissent dans une commande et dans l'interface de TigerReports : une seule ligne, sans code de couleur ni
     * caractère de contrôle.
     */
    static String cleanDetails(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isISOControl(c) ? ' ' : c);
        }
        return Texts.stripColors(sb.toString()).replace("§", "").replaceAll("\\s+", " ").trim();
    }

    // ------------------------------------------------------------------ délai anti-spam

    private long remainingSeconds(Player player, ReportSettings s) {
        Long last = lastSent.get(player.getUniqueId());
        if (last == null || s.cooldownSeconds() <= 0) {
            return 0;
        }
        long remainingMs = s.cooldownSeconds() * 1000L - (System.currentTimeMillis() - last);
        if (remainingMs <= 0) {
            lastSent.remove(player.getUniqueId(), last);
            return 0;
        }
        return (remainingMs + 999) / 1000;
    }
}
