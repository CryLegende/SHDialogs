package org.Cry_Legende.sHDialogs;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Un dialogue de la section « dialogues » de config.yml ou d'un fichier du dossier dialogues, ouvert à la demande par
 * {@code /shdialogs open <dialogue>} (depuis un menu DeluxeMenus, une commande, un NPC...).
 * <p>
 * Contrairement au menu SkyHand, ces dialogues ne vivent pas dans le registre du serveur : ils sont construits à l'exécution
 * et {@code /shdialogs reload} les recharge, sans redémarrage.
 * <p>
 * Valeurs : {@code /shdialogs open <dialogue> <joueur> <valeurs...>} remplace {1}, {2}... (et {joueur}) dans le titre, le
 * texte, les libellés, les infobulles et les commandes ; la dernière valeur prend le reste de la ligne. {@code attendre-menu}
 * : si le joueur a un menu ouvert, le dialogue attend qu'il le ferme au lieu de le fermer.
 * <p>
 * Versions : chaque version a des conditions et remplace une partie du dialogue ; à l'ouverture, la première dont toutes les
 * conditions sont vraies est montrée. Chaque version est une copie complète du dialogue (sans versions à son tour), avec son
 * nom dans {@code version} ; le dialogue de base a la version vide.
 */
record DialogueConfig(String id, String version, List<String> title, List<String> text, int columns, int buttonWidth, String closeLabel,
                      String permission, boolean waitMenu, List<MenuConfig.Button> buttons, Field field, List<Variant> variants) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,40}");
    static final int DEFAULT_INPUT_LENGTH = 32;
    static final int MAX_INPUT_LENGTH = 256;

    /**
     * Le champ de saisie du dialogue : libellé, texte de départ et nombre de caractères au plus.
     */
    record Field(String label, String initial, int maxLength) {
    }

    /**
     * Une version : ses conditions et le dialogue complet qu'elle montre.
     */
    record Variant(String name, List<Condition> conditions, DialogueConfig dialogue) {
    }

    /**
     * Un endroit où lire des dialogues : config.yml ou un fichier du dossier dialogues.
     */
    record Source(String file, ConfigurationSection section) {
    }

    enum State {
        OK, DESACTIVE, ERREUR, DOUBLON
    }

    /**
     * Ce que /shdialogs list dit d'un dialogue : où il est défini, son état, la cause d'une erreur ou le fichier qui l'avait déjà.
     */
    record Entry(String id, String file, State state, String detail) {
    }

    /**
     * Tous les dialogues valides, et une entrée par identifiant lu (y compris désactivés, en erreur ou en doublon).
     */
    record Loaded(Map<String, DialogueConfig> dialogues, List<Entry> entries) {
    }

    /**
     * Tous les dialogues actifs et valides, dans l'ordre des sources (config.yml puis les fichiers). Un dialogue invalide est
     * ignoré avec un avertissement. Un identifiant déjà lu dans une source précédente est refusé : le premier gagne.
     */
    static Loaded readAll(List<Source> sources, String defaultCloseLabel, Consumer<String> warn) {
        Map<String, DialogueConfig> dialogues = new LinkedHashMap<>();
        List<Entry> entries = new ArrayList<>();
        Map<String, String> owners = new HashMap<>();
        for (Source source : sources) {
            for (String id : source.section().getKeys(false)) {
                ConfigurationSection d = source.section().getConfigurationSection(id);
                if (d == null) {
                    warn.accept("Dialogue « " + id + " » de " + source.file() + " ignoré : ce n'est pas une section.");
                    continue;
                }
                String first = owners.putIfAbsent(id, source.file());
                if (first != null) {
                    warn.accept("Dialogue « " + id + " » de " + source.file() + " ignoré : déjà défini dans " + first + ".");
                    entries.add(new Entry(id, source.file(), State.DOUBLON, first));
                    continue;
                }
                if (!d.getBoolean("actif", true)) {
                    entries.add(new Entry(id, source.file(), State.DESACTIVE, ""));
                    continue;
                }
                List<String> own = new ArrayList<>();
                Consumer<String> sink = message -> {
                    own.add(message);
                    warn.accept(message);
                };
                DialogueConfig dialogue = readOne(id, d, defaultCloseLabel, sink);
                if (dialogue == null) {
                    entries.add(new Entry(id, source.file(), State.ERREUR, own.isEmpty() ? "non construit" : own.get(0)));
                } else {
                    dialogues.put(id, dialogue);
                    entries.add(new Entry(id, source.file(), State.OK, ""));
                }
            }
        }
        return new Loaded(Collections.unmodifiableMap(dialogues), List.copyOf(entries));
    }

    private static DialogueConfig readOne(String id, ConfigurationSection d, String defaultCloseLabel, Consumer<String> warn) {
        if (!ID.matcher(id).matches()) {
            warn.accept("Dialogue « " + id + " » ignoré : l'identifiant doit contenir seulement a-z, 0-9, _ et - (40 caractères au plus).");
            return null;
        }
        Consumer<String> inside = message -> warn.accept("Dialogue « " + id + " » : " + message);
        List<String> title = Texts.lineList(d, "titre", List.of());
        if (title.isEmpty()) {
            warn.accept("Dialogue « " + id + " » ignoré : titre manquant."
                    + (id.equals("dialogues") ? " Dans un fichier du dossier dialogues, les dialogues s'écrivent à la racine, sans section dialogues." : ""));
            return null;
        }
        List<MenuConfig.Button> buttons = MenuConfig.readButtons(d.getConfigurationSection("boutons"), inside, true);
        if (buttons.isEmpty()) {
            warn.accept("Dialogue « " + id + " » ignoré : aucun bouton valide dans boutons.");
            return null;
        }
        Field field = null;
        if (d.isConfigurationSection("champ")) {
            field = readField(d.getConfigurationSection("champ"));
        } else if (d.isSet("champ") && !Boolean.FALSE.equals(d.get("champ"))) {
            warn.accept("Dialogue « " + id + " » ignoré : champ doit être une section (libelle, defaut, longueur-max).");
            return null;
        }
        DialogueConfig base = new DialogueConfig(id, "", title, d.getStringList("texte"),
                MenuConfig.clamp(d.getInt("colonnes", 1), 1, 4), MenuConfig.clamp(d.getInt("largeur-bouton", 200), 20, 1024),
                d.getString("bouton-fermer", defaultCloseLabel), d.getString("permission", ""), d.getBoolean("attendre-menu", false),
                List.copyOf(buttons), field, List.of());
        ConfigurationSection versions = d.getConfigurationSection("versions");
        if (versions == null) {
            return base;
        }
        List<Variant> variants = new ArrayList<>();
        for (String name : versions.getKeys(false)) {
            ConfigurationSection v = versions.getConfigurationSection(name);
            if (v == null) {
                continue;
            }
            Variant variant = readVariant(base, name, v, message -> inside.accept("version « " + name + " » ignorée : " + message));
            if (variant != null) {
                variants.add(variant);
            }
        }
        return variants.isEmpty() ? base : base.withVariants(List.copyOf(variants));
    }

    /**
     * Le champ de saisie décrit par la section champ (libelle, defaut, longueur-max).
     */
    private static Field readField(ConfigurationSection c) {
        return new Field(c.getString("libelle", ""), c.getString("defaut", ""),
                MenuConfig.clamp(c.getInt("longueur-max", DEFAULT_INPUT_LENGTH), 1, MAX_INPUT_LENGTH));
    }

    /**
     * Une version : ses conditions (si, obligatoires) et ce qu'elle remplace du dialogue de base (titre, texte, boutons,
     * bouton-fermer, colonnes, largeur-bouton, champ). Le reste vient du dialogue de base. Null si elle est invalide.
     */
    private static Variant readVariant(DialogueConfig base, String name, ConfigurationSection v, Consumer<String> warn) {
        if (!ID.matcher(name).matches()) {
            warn.accept("le nom doit contenir seulement a-z, 0-9, _ et - (40 caractères au plus).");
            return null;
        }
        List<Condition> conditions = Condition.readAll(v, "si", warn);
        if (conditions == null) {
            return null;
        }
        if (conditions.isEmpty()) {
            warn.accept("si manquant.");
            return null;
        }
        List<String> title = base.title();
        if (v.isSet("titre")) {
            title = Texts.lineList(v, "titre", base.title());
            if (title.isEmpty()) {
                warn.accept("titre vide.");
                return null;
            }
        }
        List<MenuConfig.Button> buttons = base.buttons();
        if (v.isSet("boutons")) {
            buttons = MenuConfig.readButtons(v.getConfigurationSection("boutons"), warn, true);
            if (buttons.isEmpty()) {
                warn.accept("aucun bouton valide dans boutons.");
                return null;
            }
        }
        Field field = base.field();
        if (v.isSet("champ")) {
            if (v.isConfigurationSection("champ")) {
                field = readField(v.getConfigurationSection("champ"));
            } else if (Boolean.FALSE.equals(v.get("champ"))) {
                field = null;
            } else {
                warn.accept("champ doit être une section, ou false pour retirer le champ.");
                return null;
            }
        }
        DialogueConfig dialogue = new DialogueConfig(base.id(), name, title,
                v.isSet("texte") ? v.getStringList("texte") : base.text(),
                v.isSet("colonnes") ? MenuConfig.clamp(v.getInt("colonnes"), 1, 4) : base.columns(),
                v.isSet("largeur-bouton") ? MenuConfig.clamp(v.getInt("largeur-bouton"), 20, 1024) : base.buttonWidth(),
                v.isSet("bouton-fermer") ? v.getString("bouton-fermer", "") : base.closeLabel(),
                base.permission(), base.waitMenu(), List.copyOf(buttons), field, List.of());
        return new Variant(name, conditions, dialogue);
    }

    DialogueConfig withVariants(List<Variant> list) {
        return new DialogueConfig(id, version, title, text, columns, buttonWidth, closeLabel, permission, waitMenu, buttons, field, list);
    }

    /**
     * La version à montrer : la première dont toutes les conditions sont vraies, sinon le dialogue de base (sans ses versions).
     */
    DialogueConfig select(UnaryOperator<String> resolve) {
        for (Variant variant : variants) {
            if (Condition.all(variant.conditions(), resolve)) {
                return variant.dialogue();
            }
        }
        return variants.isEmpty() ? this : withVariants(List.of());
    }

    /**
     * Le dialogue montré sous ce nom de version (vide : le dialogue de base), ou null si cette version n'existe plus.
     */
    DialogueConfig variant(String name) {
        if (name.isEmpty()) {
            return variants.isEmpty() ? this : withVariants(List.of());
        }
        for (Variant variant : variants) {
            if (variant.name().equals(name)) {
                return variant.dialogue();
            }
        }
        return null;
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
     * Textes affichés : titre, texte, libellés, infobulles et champ.
     */
    private List<String> shownTexts() {
        List<String> all = new ArrayList<>(title);
        all.addAll(text);
        all.add(closeLabel);
        for (MenuConfig.Button button : buttons) {
            all.add(button.label());
            all.addAll(button.tooltip());
        }
        if (field != null) {
            all.add(field.label());
            all.add(field.initial());
        }
        return all;
    }

    /**
     * Nombre de valeurs attendues après le joueur : le plus grand {n} du dialogue (textes et commandes, versions comprises),
     * 0 sans valeur.
     */
    int valueCount() {
        List<String> all = shownTexts();
        for (MenuConfig.Button button : buttons) {
            for (MenuConfig.Action action : button.actions()) {
                all.add(action.text());
            }
            if (button.requirement() != null) {
                for (MenuConfig.Action action : button.requirement().refusal()) {
                    all.add(action.text());
                }
                for (Condition condition : button.requirement().conditions()) {
                    all.add(condition.left());
                    all.add(condition.right());
                }
            }
        }
        for (Variant variant : variants) {
            for (Condition condition : variant.conditions()) {
                all.add(condition.left());
                all.add(condition.right());
            }
        }
        int max = Texts.maxValueIndex(all);
        for (Variant variant : variants) {
            max = Math.max(max, variant.dialogue().valueCount());
        }
        return max;
    }

    /**
     * Un bouton lance une commande par la console (ou une ligne de refus par la console), dans le dialogue ou dans l'une de ses
     * versions : le dialogue ne s'ouvre que par la console ou le staff (shdialogs.open.others), et chaque ouverture ne sert qu'à
     * un clic.
     */
    boolean trusted() {
        if (buttons.stream().anyMatch(MenuConfig.Button::hasConsole)) {
            return true;
        }
        return variants.stream().anyMatch(variant -> variant.dialogue().trusted());
    }

    /**
     * Le dialogue change selon le joueur ou l'ouverture : il est construit à chaque ouverture. C'est le cas s'il a des versions,
     * si un texte affiché contient {1}, {2}... ou {joueur}, ou un placeholder PlaceholderAPI (%...%).
     */
    boolean dynamic() {
        List<String> shown = shownTexts();
        return !variants.isEmpty() || Texts.hasValues(shown) || shown.stream().anyMatch(line -> line != null && line.indexOf('%') >= 0);
    }

    /**
     * Copie dont les textes affichés ont reçu les valeurs et les placeholders (les commandes sont remplies au clic). Sans versions :
     * c'est le dialogue déjà choisi.
     */
    DialogueConfig rendered(UnaryOperator<String> transform) {
        List<MenuConfig.Button> renderedButtons = new ArrayList<>();
        for (MenuConfig.Button b : buttons) {
            renderedButtons.add(new MenuConfig.Button(b.id(), transform.apply(b.label()), apply(b.tooltip(), transform), b.actions(),
                    b.url(), b.permission(), b.close(), b.requirement()));
        }
        Field renderedField = field == null ? null
                : new Field(transform.apply(field.label()), transform.apply(field.initial()), field.maxLength());
        return new DialogueConfig(id, version, apply(title, transform), apply(text, transform), columns, buttonWidth,
                transform.apply(closeLabel), permission, waitMenu, List.copyOf(renderedButtons), renderedField, List.of());
    }

    private static List<String> apply(List<String> lines, UnaryOperator<String> transform) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(transform.apply(line));
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
