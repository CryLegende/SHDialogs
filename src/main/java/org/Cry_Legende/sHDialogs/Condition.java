package org.Cry_Legende.sHDialogs;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Une condition « A op B » d'un dialogue (versions, exigences d'un bouton). Les deux côtés passent par la fonction de
 * résolution donnée au test (valeurs du dialogue, puis placeholders PlaceholderAPI).
 * <p>
 * Si A et B se lisent comme des nombres (espaces et séparateurs de milliers ignorés, virgule ou point décimal), la
 * comparaison est numérique. Sinon, c'est une comparaison de texte sans les codes couleur, sans tenir compte de la casse ni des
 * accents ; les opérateurs d'ordre sont alors faux. {@code contient} et {@code !contient} comparent toujours du texte.
 */
record Condition(String left, Op op, String right) {

    enum Op {
        EQ("=="), NE("!="), GE(">="), LE("<="), GT(">"), LT("<"), CONTAINS("contient"), NOT_CONTAINS("!contient");

        private final String token;

        Op(String token) {
            this.token = token;
        }
    }

    /** Opérateurs en signes, du plus long au plus court. */
    private static final Op[] SYMBOLS = {Op.EQ, Op.NE, Op.GE, Op.LE, Op.GT, Op.LT};
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NUMBER = Pattern.compile("[+-]?\\d+(?:\\.\\d+)?");
    /** Entier groupé par milliers : 1.000.000 ou 1,234,567. */
    private static final String THOUSANDS = "[+-]?\\d{1,3}(?:[.,]\\d{3})+";

    /**
     * La condition écrite « A op B », ou null si aucun opérateur n'y figure. Le premier opérateur rencontré sépare les deux
     * côtés ; « contient » et « !contient » doivent être entourés d'espaces.
     */
    static Condition parse(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        for (int i = 0; i < text.length(); i++) {
            for (Op op : SYMBOLS) {
                if (text.startsWith(op.token, i)) {
                    return new Condition(text.substring(0, i).trim(), op, text.substring(i + op.token.length()).trim());
                }
            }
            if (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
                for (Op op : new Op[]{Op.NOT_CONTAINS, Op.CONTAINS}) {
                    int end = i + op.token.length();
                    if (text.startsWith(op.token, i) && (end == text.length() || Character.isWhitespace(text.charAt(end)))) {
                        return new Condition(text.substring(0, i).trim(), op, text.substring(end).trim());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Les conditions d'une clé écrite en une ligne ou en liste (vide si la clé manque). Null si l'une est invalide, après
     * l'avoir signalée.
     */
    static List<Condition> readAll(ConfigurationSection section, String key, Consumer<String> warn) {
        List<Condition> conditions = new ArrayList<>();
        for (String line : Texts.lineList(section, key, List.of())) {
            Condition condition = parse(line);
            if (condition == null) {
                warn.accept("condition invalide dans " + key + " (« A == B », avec ==, !=, >=, <=, >, <, contient ou !contient) : " + line);
                return null;
            }
            conditions.add(condition);
        }
        return List.copyOf(conditions);
    }

    /**
     * Toutes les conditions sont vraies (une liste vide l'est).
     */
    static boolean all(List<Condition> conditions, UnaryOperator<String> resolve) {
        for (Condition condition : conditions) {
            if (!condition.test(resolve)) {
                return false;
            }
        }
        return true;
    }

    boolean test(UnaryOperator<String> resolve) {
        String a = resolve.apply(left);
        String b = resolve.apply(right);
        BigDecimal numberA = number(a);
        BigDecimal numberB = number(b);
        boolean numeric = numberA != null && numberB != null;
        String textA = normalize(a);
        String textB = normalize(b);
        return switch (op) {
            case EQ -> numeric ? numberA.compareTo(numberB) == 0 : textA.equals(textB);
            case NE -> numeric ? numberA.compareTo(numberB) != 0 : !textA.equals(textB);
            case GE -> numeric && numberA.compareTo(numberB) >= 0;
            case LE -> numeric && numberA.compareTo(numberB) <= 0;
            case GT -> numeric && numberA.compareTo(numberB) > 0;
            case LT -> numeric && numberA.compareTo(numberB) < 0;
            case CONTAINS -> textA.contains(textB);
            case NOT_CONTAINS -> !textA.contains(textB);
        };
    }

    /**
     * Texte comparable : sans codes couleur, sans accents, en minuscules.
     */
    static String normalize(String text) {
        String plain = Normalizer.normalize(Texts.stripColors(text == null ? "" : text), Normalizer.Form.NFD);
        return MARKS.matcher(plain).replaceAll("").toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Le nombre que ce texte exprime, ou null. Les espaces (insécables compris) sont ignorés ; un séparateur seul est décimal
     * (« 1,5 », « 2.25 »), plusieurs séparateurs identiques ou un groupe de trois chiffres après un autre séparateur sont des
     * milliers (« 1.000.000 », « 1,234.5 »).
     */
    static BigDecimal number(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder compact = new StringBuilder();
        for (char c : Texts.stripColors(text).toCharArray()) {
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) {
                compact.append(c);
            }
        }
        String s = compact.toString();
        if (s.isEmpty() || s.chars().noneMatch(Character::isDigit)) {
            return null;
        }
        int dots = (int) s.chars().filter(c -> c == '.').count();
        int commas = (int) s.chars().filter(c -> c == ',').count();
        if (dots > 0 && commas > 0) {
            // le dernier séparateur est la virgule décimale, les autres groupent les milliers
            int last = Math.max(s.lastIndexOf('.'), s.lastIndexOf(','));
            String whole = s.substring(0, last);
            String fraction = s.substring(last + 1);
            if (!whole.matches(THOUSANDS) || !fraction.matches("\\d+")) {
                return null;
            }
            s = whole.replace(".", "").replace(",", "") + "." + fraction;
        } else if (dots + commas > 1) {
            if (!s.matches(THOUSANDS)) {
                return null;
            }
            s = s.replace(".", "").replace(",", "");
        } else if (commas == 1) {
            s = s.replace(',', '.');
        }
        if (!NUMBER.matcher(s).matches()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
