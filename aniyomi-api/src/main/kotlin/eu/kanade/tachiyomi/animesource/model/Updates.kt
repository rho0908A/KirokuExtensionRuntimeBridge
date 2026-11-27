package eu.kanade.tachiyomi.animesource.model

/** Result of an episode update for one anime (extensions-lib 17). */
class SAnimeEpisodeUpdate(val anime: SAnime, val episodes: List<SEpisode>)

/** Result of a season update for one anime (extensions-lib 17). */
class SAnimeSeasonUpdate(val anime: SAnime, val seasons: List<SAnime>)
