package com.manulaiko.shinshijiru.libman;

import com.dgtlrepublic.anitomyj.AnitomyJ;
import com.dgtlrepublic.anitomyj.Element;
import com.manulaiko.shinshijiru.libman.model.Library;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main class for managing libraries.
 *
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
        var season = (byte)0;
        var tags = new ArrayList<String>();

        for (var element : parsedName) {
            switch (element.getCategory()) {
                case kElementAnimeTitle -> title = element.getValue();
                case kElementAnimeSeason -> season = Byte.parseByte(element.getValue());
                case kElementFileExtension -> movie = true;
                default -> tags.add(element.getCategory().toString().substring(8) + "=" + element.getValue());
            }
        }

        if (movie) {
            log.debug("Show {} is a movie", title);

            return Collections.singletonList(new Library.Show(title, true, season, new ArrayList<>(), tags, path.toString()));
        }


        log.debug("Parsing episodes for show {}", title);

        var files = new ArrayList<Path>();
        var folders = new ArrayList<Path>();

        try {
            Files.list(path)
                    .forEach(p -> {
                        if (p.toFile().isFile()) {
                            files.add(p);
                        } else {
                            folders.add(p);
                        }
                    });
        } catch (NotDirectoryException e) {
            return Collections.emptyList();
        }

        if (folders.size() > 0) {
            log.debug("Found {} inner folders, assuming they are different show seasons if they're numeric", folders.size());

            var seasons = new ArrayList<Path>(folders.size());
            folders.forEach(f -> {
                try {
                    Byte.parseByte(f.getFileName().toString());
                    seasons.add(f);
                } catch (Exception e) {
                    // Not a numeric folder, maybe extras, openings-endings...
                }
            });

            if (seasons.size() > 0 ) {
                log.debug("Found {} seasons, assuming the rest of the files are movies", seasons.size());

                var finalTitle = title;
                var ret = new ArrayList<Library.Show>(folders.size());

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

                    ret.add(new Library.Show(mTitle, true, (byte)0, new ArrayList<>(), mTags, f.toString()));
                });

                seasons.forEach(f -> {
                    log.debug("Parsing season located at {}", f);

                    var sSeason = Byte.parseByte(f.getFileName().toString());

                    ret.add(new Library.Show(finalTitle, false, sSeason, parseEpisodes(f), tags, f.toString()));
                });

                return ret;
            }
        }

        return Collections.singletonList(new Library.Show(title, false, season, parseEpisodes(path), tags, path.toString()));
    }

    @SneakyThrows
    private List<Library.Show.Episode> parseEpisodes(Path path) {
        log.debug("Parsing episodes for show located at {}", path);

        var ret = new ArrayList<Library.Show.Episode>();

        var i = new AtomicInteger();
        Files.list(path)
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
                            case kElementFileName ->  name = element.getValue();
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

                    ret.add(new Library.Show.Episode(episode, episodeTitle, tags, f.toString()));
                });

        return ret;
    }
}
