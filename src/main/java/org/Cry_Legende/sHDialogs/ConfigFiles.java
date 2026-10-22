package org.Cry_Legende.sHDialogs;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lecture de config.yml pour le démarrage (bootstrap), avant que le plugin lui-même n'existe : copie le fichier par défaut
 * s'il manque et complète les clés absentes avec celles du jar, comme le ferait JavaPlugin.getConfig().
 */
final class ConfigFiles {

    private ConfigFiles() {
    }

    static YamlConfiguration load(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve("config.yml");
        if (!Files.exists(file)) {
            try (InputStream in = ConfigFiles.class.getResourceAsStream("/config.yml")) {
                if (in == null) {
                    throw new IOException("config.yml absent du jar");
                }
                Files.copy(in, file);
            }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
        try (InputStream in = ConfigFiles.class.getResourceAsStream("/config.yml")) {
            if (in != null) {
                Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                config.setDefaults(YamlConfiguration.loadConfiguration(reader));
            }
        }
        return config;
    }
}
