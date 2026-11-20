package org.Cry_Legende.sHDialogs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversion des textes de config.yml (codes &amp;) en composants, et remplacement des variables {nom}.
 */
final class Texts {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();
    private static final Pattern VARIABLE = Pattern.compile("\\{([a-z-]+)}");
    private static final Pattern COLOR_CODE = Pattern.compile("[&§](?:#[0-9a-fA-F]{6}|[0-9a-fk-orxA-FK-ORX])");

    private Texts() {
    }

    /**
     * Texte sans codes de couleur (&amp;a, §a, &amp;#rrggbb...).
     */
    static String stripColors(String text) {
        return text == null ? "" : COLOR_CODE.matcher(text).replaceAll("");
    }

    static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    /**
     * Valeur d'une clé écrite en une ligne ou en liste de lignes (le titre d'un dialogue, par exemple). Lignes vides retirées.
     */
    static List<String> lineList(ConfigurationSection section, String key, List<String> fallback) {
        List<String> lines = new ArrayList<>();
        if (section.isList(key)) {
            lines.addAll(section.getStringList(key));
        } else if (section.isString(key)) {
            lines.add(section.getString(key));
        } else {
            return fallback;
        }
        lines.removeIf(line -> line == null || line.isBlank());
        return List.copyOf(lines);
    }

    /**
     * Plusieurs lignes séparées par des retours à la ligne.
     */
    static Component lines(List<String> lines) {
        Component out = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            out = out.append(legacy(lines.get(i)));
            if (i < lines.size() - 1) {
                out = out.append(Component.newline());
            }
        }
        return out;
    }

    /**
     * Remplace les variables en un seul passage : une valeur qui contiendrait elle-même « {joueur} » n'est pas réinterprétée.
     */
    static String fill(String template, Map<String, String> values) {
        if (values.isEmpty() || template.indexOf('{') < 0) {
            return template;
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Valeurs d'un dialogue ouvert par /shdialogs open &lt;dialogue&gt; &lt;joueur&gt; &lt;valeurs...&gt; : {1}, {2}... et {joueur},
     * plus {saisie} (le texte tapé dans le champ, nettoyé par {@link #sanitizeInput}) au clic d'un bouton.
     */
    private static final Pattern VALUE = Pattern.compile("\\{([1-9][0-9]?|joueur|saisie)}");

    /**
     * Remplace {1}, {2}... et {joueur} en un seul passage (une valeur n'est pas réinterprétée). Une variable sans valeur
     * reste telle quelle.
     */
    static String fillValues(String template, Map<String, String> values) {
        if (template == null || values.isEmpty() || template.indexOf('{') < 0) {
            return template;
        }
        Matcher matcher = VALUE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Plus grand numéro de valeur ({1}, {2}...) utilisé dans ces textes, 0 s'il n'y en a pas.
     */
    static int maxValueIndex(Iterable<String> texts) {
        int max = 0;
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = VALUE.matcher(text);
            while (matcher.find()) {
                if (Character.isDigit(matcher.group(1).charAt(0))) {
                    max = Math.max(max, Integer.parseInt(matcher.group(1)));
                }
            }
        }
        return max;
    }

    /**
     * Vrai si l'un de ces textes contient {1}, {2}... ou {joueur}.
     */
    static boolean hasValues(Iterable<String> texts) {
        for (String text : texts) {
            if (text != null && VALUE.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /** Apostrophes typographiques ramenées à l'apostrophe droite. */
    private static final String APOSTROPHES = "’‘ʼ";
    /** Traits d'union typographiques ramenés au trait d'union. */
    private static final String HYPHENS = "‐‑";

    /**
     * Texte tapé par le joueur, nettoyé avant d'entrer dans une commande ou une condition : on ne garde que les lettres (accents
     * compris), les chiffres, les espaces, les apostrophes et les traits d'union. Les espaces multiples sont réduits à un, le
     * texte est coupé à {@code maxLength} caractères puis débarrassé des espaces de début et de fin. Rien d'autre ne passe.
     */
    static String sanitizeInput(String raw, int maxLength) {
        if (raw == null || raw.isEmpty() || maxLength < 1) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC);
        StringBuilder kept = new StringBuilder();
        boolean lastSpace = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetter(cp) || Character.isDigit(cp)) {
                kept.appendCodePoint(cp);
                lastSpace = false;
            } else if (cp == '\'' || cp == '-') {
                kept.append((char) cp);
                lastSpace = false;
            } else if (APOSTROPHES.indexOf(cp) >= 0) {
                kept.append('\'');
                lastSpace = false;
            } else if (HYPHENS.indexOf(cp) >= 0) {
                kept.append('-');
                lastSpace = false;
            } else if (Character.getType(cp) == Character.SPACE_SEPARATOR) {
                if (!lastSpace) {
                    kept.append(' ');
                    lastSpace = true;
                }
            }
        }
        String result = kept.toString();
        if (result.codePointCount(0, result.length()) > maxLength) {
            result = result.substring(0, result.offsetByCodePoints(0, maxLength));
        }
        return result.trim();
    }

    /**
     * Variables données par paires nom, valeur.
     */
    static Map<String, String> vars(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
