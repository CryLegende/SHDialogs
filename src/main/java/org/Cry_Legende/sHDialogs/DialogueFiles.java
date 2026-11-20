package org.Cry_Legende.sHDialogs;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Fichiers du dossier plugins/SHDialogs/dialogues : chaque fichier .yml (sous-dossiers compris) décrit des dialogues, dont les
 * identifiants sont les clés de premier niveau, avec le même format que la section « dialogues » de config.yml.
 * <p>
 * Lus par ordre alphabétique de chemin, après config.yml. Un fichier illisible n'est pas pris en compte : l'appelant reçoit le
 * nom du fichier et l'erreur.
 */
final class DialogueFiles {

    static final String FOLDER = "dialogues";

    private DialogueFiles() {
    }

    /**
     * Les dialogues lus, dans l'ordre, et les fichiers qui n'ont pas pu l'être (« chemin : erreur »).
     */
    record Loaded(List<DialogueConfig.Source> sources, List<String> errors) {
    }

    /**
     * Lit tous les fichiers .yml du dossier. Le dossier est créé s'il manque.
     */
    static Loaded read(Path dataDirectory) {
        Path folder = dataDirectory.resolve(FOLDER);
        List<DialogueConfig.Source> sources = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            errors.add(FOLDER + " : dossier impossible à créer (" + e.getMessage() + ")");
            return new Loaded(List.of(), errors);
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(folder)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted((a, b) -> name(folder, a).compareTo(name(folder, b)))
                    .toList();
        } catch (IOException e) {
            errors.add(FOLDER + " : dossier illisible (" + e.getMessage() + ")");
            return new Loaded(List.of(), errors);
        }
        for (Path file : files) {
            String name = FOLDER + "/" + name(folder, file);
            YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file.toFile());
            } catch (IOException | InvalidConfigurationException | RuntimeException e) {
                errors.add(name + " : " + describe(e));
                continue;
            }
            sources.add(new DialogueConfig.Source(name, yaml));
        }
        return new Loaded(List.copyOf(sources), List.copyOf(errors));
    }

    /** Chemin relatif au dossier, avec des « / ». */
    private static String name(Path folder, Path file) {
        return folder.relativize(file).toString().replace('\\', '/');
    }

    private static String describe(Exception e) {
        String text = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        text = text.replaceAll("\\s*\\R\\s*", " ").trim();
        return text.length() > 240 ? text.substring(0, 240) + "..." : text;
    }
}
