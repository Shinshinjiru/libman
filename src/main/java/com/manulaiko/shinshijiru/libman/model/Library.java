package com.manulaiko.shinshijiru.libman.model;

import java.util.List;

/**
 * Represents a single library path.
 *
 * A library consists of:
 *  * A name to identify the library (For example, list name)
 *  * A list of shows present on this library.
 *  * A file path.
 *
 * @author manulaiko <manulaiko@gmail.com>
 */
public record Library(String name, List<Show> shows, String path) {
    /**
     * Represents a single show.
     *
     * A show consists of:
     *  * A title for the show.
     *  * A flag to identify if the show is a series or a movie.
     *  * A list of seasons.
     *  * A list of tags (Like fansub, resolution, audio...).
     *  * A file path.
     *
     * @author manulaiko
     */
    public record Show(String title, boolean movie, List<Season> seasons, List<String> tags, String path) {
        /**
         * Represents a season of a show.
         *
         * A season consists of:
         *  * A season title.
         *  * A season number.
         *  * A list of episodes.
         *  * A list of tags (Like fansub, resolution, audio...).
         *  * A file path.
         *
         * @author manulaiko
         */
        public record Season(String title, byte season, List<Episode> episodes, List<String> tags, String path) {
            /**
             * Represents a single episode.
             *
             * An episode consists of:
             *  * An episode number (it's a string because of specials and recaps sometimes named as .5).
             *  * An episode title.
             *  * A list of tags (Like fansub, resolution, audio...)
             *  * A file name.
             *
             * @author manulaiko
             */
            public record Episode(String number, String title, List<String> tags, String path) {
            }
        }
    }
}
