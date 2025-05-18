package com.tiefensuche.soundcrowd.plugins.beatport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import com.tiefensuche.soundcrowd.plugins.IPlugin
import com.tiefensuche.beatport.api.BeatportApi
import com.tiefensuche.beatport.api.Endpoints
import com.tiefensuche.beatport.api.Genre
import com.tiefensuche.beatport.api.Playlist
import com.tiefensuche.beatport.api.Track
import androidx.core.net.toUri
import androidx.preference.Preference
import com.tiefensuche.soundcrowd.plugins.MediaItemUtils
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import androidx.core.content.edit

/**
 * Beatport plugin for soundcrowd
 */
class Plugin(val context: Context) : IPlugin {
    companion object {
        const val NAME = "Beatport"
        const val CURATED = "Curated Playlists"
        const val GENRES = "Genres"
        const val TOP100 = "Top 100"
        const val PLAYLISTS = "Playlists"
    }

    private val icon: Bitmap =
        BitmapFactory.decodeResource(context.resources, R.drawable.plugin_icon)
    private var sharedPref = PreferenceManager.getDefaultSharedPreferences(context)
    private var api: BeatportApi =
        BeatportApi(
            BeatportApi.Session(
                context.getString(R.string.beatport_client_id),
                context.getString(R.string.beatport_redirect_uri),
                ::sessionCallback
            )
        )
    private val editTextUsername = EditTextPreference(context)
    private val editTextPassword = EditTextPreference(context)

    init {
        editTextUsername.key = context.getString(R.string.username_key)
        editTextUsername.title = context.getString(R.string.username_title)
        editTextUsername.summary = context.getString(R.string.username_summary)
        editTextUsername.dialogTitle = context.getString(R.string.username_title)
        editTextUsername.dialogMessage = context.getString(R.string.username_dialog_message)
        editTextUsername.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            api.session.accessToken = null
            api.session.refreshToken = null
            api.session.username = newValue as String
            true
        }

        editTextPassword.key = context.getString(R.string.password_key)
        editTextPassword.title = context.getString(R.string.password_title)
        editTextPassword.summary = context.getString(R.string.password_summary)
        editTextPassword.dialogTitle = context.getString(R.string.password_title)
        editTextPassword.dialogMessage = context.getString(R.string.password_dialog_message)
        editTextPassword.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            api.session.accessToken = null
            api.session.refreshToken = null
            api.session.password = newValue as String
            true
        }

        api.session.username = sharedPref.getString(context.getString(R.string.username_key), null)
        api.session.password = sharedPref.getString(context.getString(R.string.password_key), null)
        api.session.accessToken = sharedPref.getString(context.getString(R.string.beatport_access_token_key), null)
        api.session.refreshToken = sharedPref.getString(context.getString(R.string.beatport_refresh_token_key), null)
    }

    override fun name(): String = NAME
    override fun mediaCategories(): List<String> = listOf(CURATED, GENRES, TOP100, PLAYLISTS)
    override fun preferences() = listOf(editTextUsername, editTextPassword)

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        refresh: Boolean
    ) : List<MediaItem> {
        return when (mediaCategory) {
            CURATED, GENRES, TOP100 -> genresToMediaItems(api.getGenres(refresh))
            PLAYLISTS -> playlistsToMediaItems(api.getMyPlaylists(refresh))
            else -> emptyList()
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        refresh: Boolean
    ) : List<MediaItem> {
        return when (mediaCategory) {
            CURATED ->
                if (!path.contains('/'))
                    playlistsToMediaItems(api.getCuratedPlaylists(path, refresh))
                else
                    tracksToMediaItems(
                        api.getCuratedPlaylist(
                            path.substring(path.lastIndexOf('/') + 1),
                            refresh
                        )
                    )
            GENRES -> tracksToMediaItems(api.getTracks(path, refresh))
            TOP100 -> tracksToMediaItems(api.getTop100(path, refresh))
            PLAYLISTS -> tracksToMediaItems(
                api.getPlaylist(
                    path,
                    Endpoints.PLAYLIST_TRACKS,
                    refresh
                )
            )
            else -> emptyList()
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        query: String,
        type: String,
        refresh: Boolean
    ) : List<MediaItem> {
        return tracksToMediaItems(api.query(query, refresh))
    }

    override fun getMediaUri(
        mediaItem: MediaItem,
    ) : Uri {
        if (!api.session.hasSubscription()) {
            return mediaItem.requestMetadata.mediaUri!!
        }
        return api.getStreamUrl(mediaItem.mediaId).toUri()
    }

    override fun getIcon(): Bitmap = icon

    private fun tracksToMediaItems(tracks: List<Track>): List<MediaItem> {
        return tracks.map {
            MediaItemUtils.createMediaItem(
                it.id.toString(),
                it.url.toUri(),
                it.title,
                it.duration,
                it.artist,
                artworkUri = it.artwork.toUri()
            )
        }
    }

    private fun genresToMediaItems(genres: List<Genre>): List<MediaItem> {
        return genres.map {
            MediaItemUtils.createBrowsableItem(
                it.id.toString(),
                it.name,
                MediaMetadataCompatExt.MediaType.COLLECTION
            )
        }
    }

    private fun playlistsToMediaItems(playlists: List<Playlist>): List<MediaItem> {
        return playlists.map {
            MediaItemUtils.createBrowsableItem(
                it.uuid.toString(),
                it.title,
                MediaMetadataCompatExt.MediaType.STREAM
            )
        }
    }

    private fun sessionCallback(session: BeatportApi.Session) {
        sharedPref.edit {
            putString(context.getString(R.string.beatport_access_token_key), session.accessToken)
            putString(context.getString(R.string.beatport_refresh_token_key), session.refreshToken)
        }
    }
}