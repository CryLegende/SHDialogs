package org.Cry_Legende.sHDialogs;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

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
