package org.Cry_Legende.sHDialogs;

import net.kyori.adventure.key.Key;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Menu SkyHand tel que décrit par la section « menu » de config.yml.
 * <p>
 * Lu deux fois : au démarrage du serveur (pour construire le dialogue du registre, qui ne change plus ensuite) et à chaque
 * rechargement (pour retrouver la commande de chaque bouton au moment du clic).
 */
record MenuConfig(boolean enabled, boolean pauseScreen, boolean quickActions, String title, String buttonTitle,
                  List<String> text, int columns, int buttonWidth, String closeLabel, List<Button> buttons) {

    /**
     * Un bouton du menu : soit une commande joueur, soit une adresse web.
     */
    record Button(String id, String label, List<String> tooltip, String command, String url, String permission) {

        boolean isUrl() {
            return url != null;
        }

        /**
         * Clé envoyée par le client au clic (boutons de commande seulement).
         */
        Key clickKey() {
            return Key.key("shdialogs", "menu/" + id);
        }
    }

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,40}");
    static final int MAX_COMMAND_LENGTH = 200;

    static MenuConfig read(ConfigurationSection root, Consumer<String> warn) {
        ConfigurationSection menu = root.getConfigurationSection("menu");
        if (menu == null) {
            return disabled();
        }
        List<Button> buttons = new ArrayList<>();
        ConfigurationSection section = menu.getConfigurationSection("boutons");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection b = section.getConfigurationSection(id);
                if (b == null || !b.getBoolean("actif", true)) {
                    continue;
                }
                Button button = readButton(id, b, warn);
                if (button != null) {
                    buttons.add(button);
                }
            }
        }
        boolean enabled = menu.getBoolean("actif", true);
        if (enabled && buttons.isEmpty()) {
            warn.accept("Menu SkyHand ignoré : aucun bouton valide dans menu.boutons.");
            enabled = false;
        }
        return new MenuConfig(enabled, menu.getBoolean("ecran-echap", true), menu.getBoolean("actions-rapides", true),
                menu.getString("titre", "&7&l« &e&lSkyHand &8&l» &7Menu"), menu.getString("titre-bouton", "&eMenu SkyHand"),
                menu.getStringList("texte"), clamp(menu.getInt("colonnes", 2), 1, 4), clamp(menu.getInt("largeur-bouton", 150), 20, 1024),
                menu.getString("bouton-fermer", "&c⤾ &fFermer"), List.copyOf(buttons));
    }

    private static MenuConfig disabled() {
        return new MenuConfig(false, false, false, "", "", List.of(), 1, 150, "", List.of());
    }

    private static Button readButton(String id, ConfigurationSection b, Consumer<String> warn) {
        if (!ID.matcher(id).matches()) {
            warn.accept("Bouton « " + id + " » ignoré : l'identifiant doit contenir seulement a-z, 0-9, _ et - (40 caractères au plus).");
            return null;
        }
        String label = b.getString("libelle");
        if (label == null || label.isBlank()) {
            warn.accept("Bouton « " + id + " » ignoré : libelle manquant.");
            return null;
        }
        List<String> tooltip = b.getStringList("infobulle");
        String permission = b.getString("permission", "");
        String url = b.getString("url", "");
        if (!url.isBlank()) {
            String checked = checkUrl(url.trim());
            if (checked == null) {
                warn.accept("Bouton « " + id + " » ignoré : url invalide (http ou https attendu) : " + url);
                return null;
            }
            return new Button(id, label, tooltip, null, checked, permission == null ? "" : permission);
        }
        String command = cleanCommand(b.getString("commande"));
        if (command == null) {
            warn.accept("Bouton « " + id + " » ignoré : commande manquante ou invalide.");
            return null;
        }
        return new Button(id, label, tooltip, command, null, permission == null ? "" : permission);
    }

    /**
     * Commande sans le « / » initial, ou null si elle est vide, trop longue ou sur plusieurs lignes.
     */
    static String cleanCommand(String raw) {
        if (raw == null) {
            return null;
        }
        String command = raw.trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }
        if (command.isEmpty() || command.length() > MAX_COMMAND_LENGTH || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) {
            return null;
        }
        return command;
    }

    private static String checkUrl(String raw) {
        try {
            URI uri = new URI(raw);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
                return null;
            }
            return uri.toString();
        } catch (java.net.URISyntaxException e) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    Button button(String id) {
        for (Button button : buttons) {
            if (button.id().equals(id)) {
                return button;
            }
        }
        return null;
    }

    /**
     * Tout ce qui est figé dans le dialogue du registre. Si la signature change après un rechargement, l'aspect du menu
     * ne sera à jour qu'au prochain démarrage.
     */
    String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(enabled).append('|').append(pauseScreen).append('|').append(quickActions).append('|').append(title).append('|')
                .append(buttonTitle).append('|').append(text).append('|').append(columns).append('|').append(buttonWidth).append('|')
                .append(closeLabel);
        for (Button b : buttons) {
            sb.append('\n').append(b.id().toLowerCase(Locale.ROOT)).append('|').append(b.label()).append('|').append(b.tooltip())
                    .append('|').append(b.url());
        }
        return sb.toString();
    }
}
