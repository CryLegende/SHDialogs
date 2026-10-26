package org.Cry_Legende.sHDialogs;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/**
 * Messages du chat. Le préfixe est celui de config.yml, avec la couleur du type du message (erreur, succès, info, avertissement).
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

    Messages(ConfigurationSection config) {
        this.prefix = config.getString("prefixe", "&7&l« &{couleur}&lSH &8&l» &7");
        this.colors.put(Type.ERREUR, config.getString("couleurs.erreur", "c"));
        this.colors.put(Type.SUCCES, config.getString("couleurs.succes", "a"));
        this.colors.put(Type.INFO, config.getString("couleurs.info", "e"));
        this.colors.put(Type.AVERTISSEMENT, config.getString("couleurs.avertissement", "6"));
        this.messages = config.getConfigurationSection("messages");
    }

    Component get(Type type, String key, String... pairs) {
        String text = messages == null ? null : messages.getString(type.path + "." + key);
        if (text == null) {
            text = "&cMessage manquant : messages." + type.path + "." + key;
        }
        String head = prefix.replace("{couleur}", colors.get(type));
        return Texts.legacy(head + Texts.fill(text, Texts.vars(pairs)));
    }

    void send(CommandSender to, Type type, String key, String... pairs) {
        to.sendMessage(get(type, key, pairs));
    }
}
