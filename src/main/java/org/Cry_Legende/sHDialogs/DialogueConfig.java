package org.Cry_Legende.sHDialogs;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
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
 * <p>
 * Valeurs : {@code /shdialogs open <dialogue> <joueur> <valeurs...>} remplace {1}, {2}... (et {joueur}) dans le titre, le
 * texte, les libellés, les infobulles et les commandes ; la dernière valeur prend le reste de la ligne. {@code attendre-menu}
 * : si le joueur a un menu ouvert, le dialogue attend qu'il le ferme au lieu de le fermer.
 */
record DialogueConfig(String id, List<String> title, List<String> text, int columns, int buttonWidth, String closeLabel,
                      String permission, boolean waitMenu, List<MenuConfig.Button> buttons) {

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
            List<String> title = Texts.lineList(d, "titre", List.of());
            if (title.isEmpty()) {
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
                    d.getString("bouton-fermer", defaultCloseLabel), d.getString("permission", ""), d.getBoolean("attendre-menu", false),
                    List.copyOf(buttons)));
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
     * Textes affichés : titre, texte, libellés et infobulles.
     */
    private List<String> shownTexts() {
        List<String> all = new ArrayList<>(title);
        all.addAll(text);
        all.add(closeLabel);
        for (MenuConfig.Button button : buttons) {
            all.add(button.label());
            all.addAll(button.tooltip());
        }
        return all;
    }

    /**
     * Nombre de valeurs attendues après le joueur : le plus grand {n} du dialogue (textes et commandes), 0 sans valeur.
     */
    int valueCount() {
        List<String> all = shownTexts();
        for (MenuConfig.Button button : buttons) {
            for (MenuConfig.Action action : button.actions()) {
                all.add(action.text());
            }
        }
        return Texts.maxValueIndex(all);
    }

    /**
     * Un bouton lance une commande par la console : le dialogue ne s'ouvre que par la console ou le staff
     * (shdialogs.open.others), et chaque ouverture ne sert qu'à un clic.
     */
    boolean trusted() {
        return buttons.stream().anyMatch(MenuConfig.Button::hasConsole);
    }

    /**
     * Le titre, le texte, un libellé ou une infobulle contient {1}, {2}... ou {joueur} : le dialogue est construit à chaque
     * ouverture.
     */
    boolean showsValues() {
        return Texts.hasValues(shownTexts());
    }

    /**
     * Copie dont les textes affichés ont reçu les valeurs (les commandes sont remplies au clic).
     */
    DialogueConfig filled(Map<String, String> values) {
        List<MenuConfig.Button> filledButtons = new ArrayList<>();
        for (MenuConfig.Button b : buttons) {
            filledButtons.add(new MenuConfig.Button(b.id(), Texts.fillValues(b.label(), values), fill(b.tooltip(), values), b.actions(),
                    b.url(), b.permission(), b.close()));
        }
        return new DialogueConfig(id, fill(title, values), fill(text, values), columns, buttonWidth, Texts.fillValues(closeLabel, values),
                permission, waitMenu, List.copyOf(filledButtons));
    }

    private static List<String> fill(List<String> lines, Map<String, String> values) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(Texts.fillValues(line, values));
        }
        return List.copyOf(out);
    }

    /**
     * Préfixe des clés de clic des boutons de commande de ce dialogue (voir {@link MenuConfig.Button#clickKey(String)}).
     */
    String clickScope() {
        return "dialogue/" + id;
    }
}
