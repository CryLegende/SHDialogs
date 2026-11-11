package org.Cry_Legende.sHDialogs;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/**
 * Messages du chat (messages.yml). Le préfixe est celui du fichier, avec la couleur du type du message (erreur, succès, info, avertissement).
 */
final class Messages {

    enum Type {
        ERREUR("erreur"),
        SUCCES("succes"),
        INFO("info"),
        AVERTISSEMENT("avertissement");

        private final String path;

        Type(String path) {
            this.path = path;
        }
    }

    private final String prefix;
    private final Map<Type, String> colors = new EnumMap<>(Type.class);
    private final ConfigurationSection messages;

    /**
     * @param root racine de messages.yml (préfixe, couleurs et un groupe par type de message)
     */
    Messages(ConfigurationSection root) {
        this.prefix = root.getString("prefixe", "&7&l« &{couleur}&lSH &8&l» &7");
        this.colors.put(Type.ERREUR, root.getString("couleurs.erreur", "c"));
        this.colors.put(Type.SUCCES, root.getString("couleurs.succes", "a"));
        this.colors.put(Type.INFO, root.getString("couleurs.info", "e"));
        this.colors.put(Type.AVERTISSEMENT, root.getString("couleurs.avertissement", "6"));
        this.messages = root;
    }

    Component get(Type type, String key, String... pairs) {
        String text = messages.getString(type.path + "." + key);
        if (text == null) {
            text = "&cMessage manquant : " + type.path + "." + key;
        }
        String head = prefix.replace("{couleur}", colors.get(type));
        return Texts.legacy(head + Texts.fill(text, Texts.vars(pairs)));
    }

    void send(CommandSender to, Type type, String key, String... pairs) {
        to.sendMessage(get(type, key, pairs));
    }
}
