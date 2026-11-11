package org.Cry_Legende.sHDialogs;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Lecture de messages.yml. Un texte absent du fichier du serveur prend la valeur du jar. Quand messages.yml n'existe pas
 * encore, il est créé à partir des textes que config.yml contenait avant (sections prefixe, couleurs et messages) : les
 * textes du serveur sont conservés, config.yml n'est plus lu pour cela ensuite.
 */
final class MessagesFile {

    static final String NAME = "messages.yml";

    private MessagesFile() {
    }

    /**
     * Lit messages.yml en mode strict : une erreur de syntaxe est levée au lieu d'être avalée.
     */
    static YamlConfiguration read(JavaPlugin plugin) throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), NAME);
        boolean created = !file.exists();
        if (created) {
            plugin.saveResource(NAME, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file);
        if (created) {
            migrate(plugin, loaded, file);
        }
        YamlConfiguration defaults = jarDefaults(plugin);
        if (defaults != null) {
            loaded.setDefaults(defaults);
        }
        return loaded;
    }

    /**
     * Textes du jar seuls : sert quand messages.yml est illisible au démarrage.
     */
    static YamlConfiguration jarOnly(JavaPlugin plugin) {
        YamlConfiguration fallback = new YamlConfiguration();
        YamlConfiguration defaults = jarDefaults(plugin);
        if (defaults != null) {
            fallback.setDefaults(defaults);
        }
        return fallback;
    }

    private static YamlConfiguration jarDefaults(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource(NAME)) {
            return in == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "messages.yml du jar illisible : " + e.getMessage());
            return null;
        }
    }

    /**
     * Reprend dans messages.yml les textes de l'ancienne section messages de config.yml, avec son préfixe et ses couleurs.
     */
    private static void migrate(JavaPlugin plugin, YamlConfiguration target, File file) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return;
        }
        YamlConfiguration old = YamlConfiguration.loadConfiguration(configFile);
        int copied = 0;
        if (old.isString("prefixe")) {
            target.set("prefixe", old.getString("prefixe"));
            copied++;
        }
        var colors = old.getConfigurationSection("couleurs");
        if (colors != null) {
            for (String key : colors.getKeys(false)) {
                target.set("couleurs." + key, colors.getString(key));
                copied++;
            }
        }
        var messages = old.getConfigurationSection("messages");
        if (messages != null) {
            for (String type : messages.getKeys(false)) {
                var group = messages.getConfigurationSection(type);
                if (group == null) {
                    continue;
                }
                for (String key : group.getKeys(false)) {
                    if (group.isString(key)) {
                        target.set(type + "." + key, group.getString(key));
                        copied++;
                    }
                }
            }
        }
        if (copied == 0) {
            return;
        }
        try {
            target.save(file);
            plugin.getLogger().info("messages.yml créé à partir des textes de config.yml (" + copied + " valeurs reprises).");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "messages.yml non écrit, textes de config.yml utilisés pour cette session : " + e.getMessage());
        }
    }
}
