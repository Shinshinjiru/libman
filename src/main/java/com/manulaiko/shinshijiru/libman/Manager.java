package com.manulaiko.shinshijiru.libman;

import com.dgtlrepublic.anitomyj.AnitomyJ;
import com.dgtlrepublic.anitomyj.Element;
import com.manulaiko.shinshijiru.libman.model.Library;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main class for managing libraries.
 * <p>
 * The `scan` method performs the initial scan of the library and generates the model graph.
 * You can pass a list of paths each representing a library.
 *
 * @author manulaiko <manulaiko@gmail.com>
 */
@Slf4j
@Component
public class Manager {
    public Library scan(Path path) {
        return scan(Collections.singletonList(path))
                .get(0);
    }

    public List<Library> scan(List<Path> paths) {
        log.debug("Scanning {} library paths...", paths.size());

        return paths.stream()
                .map(Path::toAbsolutePath)
                .map(this::parseLibrary)
                .toList();
    }

    @SneakyThrows
    private Library parseLibrary(Path path) {
        log.debug("Parsing library located at {}", path);

        var name = path.getFileName().toString();
        var shows = Files.list(path)
                .map(this::parseShow)
                .flatMap(Collection::stream)
                .toList();

        log.debug("Library parsed with {} shows in it", shows.size());

        return new Library(name, shows, path.toString());
    }

    @SneakyThrows
    private List<Library.Show> parseShow(Path path) {
        log.debug("Parsing show located at {}", path);

        var parsedName = AnitomyJ.parse(path.getFileName().toString());
        var title = "";
        var movie = false;
        var tags = new ArrayList<String>();

        for (var element : parsedName) {
            switch (element.getCategory()) {
                case kElementAnimeTitle -> title = element.getValue();
                case kElementFileExtension -> movie = true;
                default -> tags.add(element.getCategory().toString().substring(8) + "=" + element.getValue());
            }
        }

        if (movie) {
            log.debug("Show {} is a movie", title);

            return Collections.singletonList(new Library.Show(title, true, new ArrayList<>(), tags, path.toString()));
        }

        if (path.toFile().isFile()) {
            return Collections.emptyList();
        }

        log.debug("Parsing episodes for show {}", title);

        var files = new ArrayList<Path>();
        var folders = new ArrayList<Path>();

        Files.list(path)
                .forEach(p -> {
                    if (p.toFile().isFile()) {
                        files.add(p);
                    } else {
                        folders.add(p);
                    }
                });

        var seasons = parseSeasons(path);

        var show = new Library.Show(title, false, seasons, tags, path.toString());
        if (folders.isEmpty() || folders.size() == 1 && folders.stream().anyMatch(p -> p.getFileName().toString().matches("^[eE][xX][tT][rR][aA][sS]?$"))) {
            return Collections.singletonList(show);
        }

        log.debug("Found {} inner folders, assuming they are different show seasons if they're numeric", folders.size());

        var ret = new ArrayList<Library.Show>();
        ret.add(show);

        files.forEach(f -> {
            log.debug("Parsing movie located at {}", f);

            var mParsedName = AnitomyJ.parse(f.getFileName().toString());

            var mTitle = "";
            var mTags = new ArrayList<String>();

            for (var element : mParsedName) {
                if (element.getCategory() == Element.ElementCategory.kElementAnimeTitle) {
                    mTitle = element.getValue();
                } else {
                    mTags.add(element.getCategory().toString().substring(8) + "=" + element.getValue());
                }
            }

            if (mTags.isEmpty()) {
                mTags = tags;
            }

            ret.add(new Library.Show(mTitle, true, new ArrayList<>(), mTags, f.toString()));
        });

        return ret;
    }

    @SneakyThrows
    private List<Library.Show.Season> parseSeasons(Path path) {
        log.debug("Parsing seasons for show located at {}", path);

        var folders = Files.list(path)
                .filter(p -> p.toFile().isDirectory())
                .toList();

        var ret = new ArrayList<Library.Show.Season>();
        var seasons = new ArrayList<Path>(folders.size());

        // Add the seasons to parse based on the folders in this path.
        folders.forEach(f -> {
            var fN = f.getFileName().toString();

            // If the folder name is a number, assume it's the season number.
            try {
                Byte.parseByte(fN);
                seasons.add(f);
            } catch (Exception ex) {
                // If the parsed name contains a season number.
                var parsed = AnitomyJ.parse(fN);
                parsed.stream()
                        .filter(e -> e.getCategory() == Element.ElementCategory.kElementAnimeSeason)
                        .findAny()
                        .ifPresent((e) -> seasons.add(f));
            }

            // Not a numeric folder, maybe extras, openings-endings...
        });

        if (seasons.isEmpty()) {
            return Collections.singletonList(new Library.Show.Season("", (byte) 1, parseEpisodes(path), Collections.emptyList(), path.toString()));
        }

        log.debug("Found {} seasons", seasons.size());

        var i = new AtomicInteger();
        seasons.forEach(f -> {
            log.debug("Parsing season located at {}", f);

            var parsed = AnitomyJ.parse(f.getFileName().toString());
            var title = "";
            var season = (byte) 0;
            var tags = new ArrayList<String>();

            for (var e : parsed) {
                switch (e.getCategory()) {
                    case kElementAnimeSeason -> season = Byte.parseByte(e.getValue());
                    case kElementAnimeTitle -> title = e.getValue();
                    default -> tags.add(e.getCategory() + "=" + e.getValue());
                }
            }

            var currI = i.getAndIncrement();
            if (season == 0) {
                try {
                    season = Byte.parseByte(f.getFileName().toString());
                } catch (Exception e) {
                    season = (byte) currI;
                }
            }

            ret.add(new Library.Show.Season(title, season, parseEpisodes(f), tags, f.toString()));
        });

        return ret;
    }

    @SneakyThrows
    private List<Library.Show.Season.Episode> parseEpisodes(Path path) {
        log.debug("Parsing episodes for show located at {}", path);

        var ret = new ArrayList<Library.Show.Season.Episode>();

        var i = new AtomicInteger();
        Files.list(path)
                .filter(f -> f.toFile().isFile())
                .forEach(f -> {
                    var parsedName = AnitomyJ.parse(f.getFileName().toString());

                    var name = "";
                    var animeTitle = "";
                    var episodeTitle = "";
                    var movie = false;
                    var episode = "";
                    var tags = new ArrayList<String>();

                    for (var element : parsedName) {
                        switch (element.getCategory()) {
                            case kElementEpisodeTitle -> episodeTitle = element.getValue();
                            case kElementAnimeTitle -> animeTitle = element.getValue();
                            case kElementEpisodeNumber -> episode = element.getValue();
                            case kElementFileExtension -> movie = true;
                            case kElementFileName -> name = element.getValue();
                            default -> tags.add(element.getCategory().toString().substring(8) + "=" + element.getValue());
                        }
                    }

                    if (!movie) {
                        // The file is not a video file, skip.

                        return;
                    }

                    i.getAndIncrement();

                    if (episode.isEmpty()) {
                        try {
                            Short.parseShort(name);
                            episode = name;
                        } catch (Exception e) {
                            try {
                                Short.parseShort(animeTitle);
                                episode = animeTitle;
                            } catch (Exception e1) {
                                episode = i.get() + "";
                                episodeTitle = animeTitle;
                            }
                        }
                    }

                    ret.add(new Library.Show.Season.Episode(episode, episodeTitle, tags, f.toString()));
                });

        return ret;
    }
}
