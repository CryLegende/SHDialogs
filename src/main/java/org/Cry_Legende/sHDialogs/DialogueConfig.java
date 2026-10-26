package org.Cry_Legende.sHDialogs;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Un dialogue de la section « dialogues » de config.yml, ouvert à la demande par {@code /shdialogs open <dialogue>}
 * (depuis un menu DeluxeMenus, une commande, un NPC...).
 * <p>
 * Contrairement au menu SkyHand, ces dialogues ne vivent pas dans le registre du serveur : ils sont construits à l'exécution
 * et {@code /shdialogs reload} les recharge, sans redémarrage.
 */
record DialogueConfig(String id, String title, List<String> text, int columns, int buttonWidth, String closeLabel,
                      String permission, List<MenuConfig.Button> buttons) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,40}");

    /**
     * Tous les dialogues actifs et valides, dans l'ordre du fichier. Un dialogue invalide est ignoré avec un avertissement.
     */
    static Map<String, DialogueConfig> readAll(ConfigurationSection root, String defaultCloseLabel, Consumer<String> warn) {
        ConfigurationSection section = root.getConfigurationSection("dialogues");
        if (section == null) {
            return Map.of();
        }
        Map<String, DialogueConfig> dialogues = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection d = section.getConfigurationSection(id);
            if (d == null || !d.getBoolean("actif", true)) {
                continue;
            }
            if (!ID.matcher(id).matches()) {
                warn.accept("Dialogue « " + id + " » ignoré : l'identifiant doit contenir seulement a-z, 0-9, _ et - (40 caractères au plus).");
                continue;
            }
            String title = d.getString("titre");
            if (title == null || title.isBlank()) {
                warn.accept("Dialogue « " + id + " » ignoré : titre manquant.");
                continue;
            }
            List<MenuConfig.Button> buttons = MenuConfig.readButtons(d.getConfigurationSection("boutons"),
                    message -> warn.accept("Dialogue « " + id + " » : " + message));
            if (buttons.isEmpty()) {
                warn.accept("Dialogue « " + id + " » ignoré : aucun bouton valide dans boutons.");
                continue;
            }
            dialogues.put(id, new DialogueConfig(id, title, d.getStringList("texte"),
                    MenuConfig.clamp(d.getInt("colonnes", 1), 1, 4), MenuConfig.clamp(d.getInt("largeur-bouton", 200), 20, 1024),
                    d.getString("bouton-fermer", defaultCloseLabel), d.getString("permission", ""), List.copyOf(buttons)));
        }
        return Collections.unmodifiableMap(dialogues);
    }

    MenuConfig.Button button(String buttonId) {
        for (MenuConfig.Button button : buttons) {
            if (button.id().equals(buttonId)) {
                return button;
            }
        }
        return null;
    }

    /**
     * Préfixe des clés de clic des boutons de commande de ce dialogue (voir {@link MenuConfig.Button#clickKey(String)}).
     */
    String clickScope() {
        return "dialogue/" + id;
    }
}
