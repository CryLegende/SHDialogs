package org.Cry_Legende.sHDialogs;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Réglages de /signaler (section « signalement » de config.yml), relus à chaque rechargement.
 */
record ReportSettings(boolean enabled, String command, int minProtocol, int cooldownSeconds, int expirationSeconds,
                      Pattern namePattern, int nameMax, boolean refuseUnknown, int detailsMax, int detailsLines,
                      int detailsHeight, String reasonFormat, int reasonMax, int width, String title, List<String> text,
                      String targetLabel, String motifLabel, String detailsLabel, String sendLabel, List<String> sendTooltip,
                      String cancelLabel, List<String> cancelTooltip, Map<String, String> errors, List<Motif> motifs) {

    /**
     * Motif proposé au joueur. id : valeur renvoyée par le client, reason : texte exact du motif dans TigerReports.
     */
    record Motif(String id, String label, String reason) {
    }

    private static final Pattern MOTIF_ID = Pattern.compile("[a-z0-9_-]{1,40}");

    static ReportSettings read(ConfigurationSection root, Consumer<String> warn) {
        ConfigurationSection s = root.getConfigurationSection("signalement");
        if (s == null) {
            s = root.createSection("signalement");
        }
        boolean enabled = s.getBoolean("actif", true);

        String command = MenuConfig.cleanCommand(s.getString("commande", "report {joueur} {raison}"));
        if (command == null) {
            warn.accept("signalement.commande invalide : /signaler désactivé.");
            command = "report {joueur} {raison}";
            enabled = false;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(s.getString("pseudo-regex", "^[A-Za-z0-9_]{1,16}$"));
        } catch (PatternSyntaxException e) {
            warn.accept("signalement.pseudo-regex invalide, valeur par défaut utilisée : " + e.getDescription());
            pattern = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
        }

        List<Motif> motifs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map<?, ?> raw : s.getMapList("motifs")) {
            String id = raw.get("id") == null ? "" : String.valueOf(raw.get("id"));
            String label = raw.get("libelle") == null ? null : String.valueOf(raw.get("libelle"));
            String reason = raw.get("raison") == null ? null : String.valueOf(raw.get("raison")).trim();
            if (!MOTIF_ID.matcher(id).matches() || label == null || reason == null || reason.isEmpty() || !seen.add(id)) {
                warn.accept("Motif ignoré (id a-z0-9_- unique, libelle et raison requis) : " + raw);
                continue;
            }
            motifs.add(new Motif(id, label, reason));
        }
        if (enabled && motifs.isEmpty()) {
            warn.accept("signalement.motifs est vide : /signaler désactivé.");
            enabled = false;
        }

        Map<String, String> errors = new LinkedHashMap<>();
        ConfigurationSection errorSection = s.getConfigurationSection("erreurs");
        if (errorSection != null) {
            for (String key : errorSection.getKeys(false)) {
                errors.put(key, errorSection.getString(key, ""));
            }
        }

        return new ReportSettings(enabled, command, s.getInt("protocole-minimum", 771),
                Math.max(0, s.getInt("cooldown-secondes", 30)), Math.max(10, s.getInt("expiration-secondes", 600)),
                pattern, clamp(s.getInt("pseudo-longueur-max", 16), 1, 64), s.getBoolean("refuser-joueur-inconnu", true),
                clamp(s.getInt("details-longueur-max", 150), 1, 1000), clamp(s.getInt("details-lignes", 4), 1, 10),
                clamp(s.getInt("details-hauteur", 80), 32, 300), s.getString("format-raison", "{motif} - {details}"),
                clamp(s.getInt("raison-longueur-max", 200), 10, 1000), clamp(s.getInt("largeur", 260), 100, 1024),
                s.getString("titre", "&c&lSignaler un joueur"), s.getStringList("texte"),
                s.getString("champ-joueur", "&7Pseudo du joueur"), s.getString("champ-motif", "&7Motif"),
                s.getString("champ-details", "&7Détails (facultatif)"), s.getString("bouton-envoyer", "&a&lEnvoyer"),
                s.getStringList("infobulle-envoyer"), s.getString("bouton-annuler", "&7Annuler"),
                s.getStringList("infobulle-annuler"), Map.copyOf(errors), List.copyOf(motifs));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    Motif motif(String id) {
        if (id == null) {
            return null;
        }
        for (Motif motif : motifs) {
            if (motif.id().equals(id)) {
                return motif;
            }
        }
        return null;
    }

    String error(String key, String... pairs) {
        String text = errors.getOrDefault(key, "&cErreur : " + key);
        return Texts.fill(text, Texts.vars(pairs));
    }
}
