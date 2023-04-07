package com.tiefensuche.soundcrowd.plugins.beatport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.support.v4.media.MediaMetadataCompat
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import com.tiefensuche.beatport.api.BeatportApi
import com.tiefensuche.beatport.api.Endpoints
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.plugins.Callback
import com.tiefensuche.soundcrowd.plugins.IPlugin

/**
 * Beatport plugin for soundcrowd
 */
class Plugin(appContext: Context, context: Context) : IPlugin {
    companion object {
        const val name = "Beatport"
        const val CURATED = "Curated Playlists"
        const val GENRES = "Genres"
        const val TOP100 = "Top 100"
        const val PLAYLISTS = "Playlists"
    }

    private val icon: Bitmap =
        BitmapFactory.decodeResource(context.resources, R.drawable.plugin_icon)
    private var api: BeatportApi =
        BeatportApi(context, PreferenceManager.getDefaultSharedPreferences(appContext))
    private val editTextUsername = EditTextPreference(context)
    private val editTextPassword = EditTextPreference(context)

    init {
        editTextUsername.key = context.getString(R.string.username_key)
        editTextUsername.title = context.getString(R.string.username_title)
        editTextUsername.summary = context.getString(R.string.username_summary)
        editTextUsername.dialogTitle = context.getString(R.string.username_title)
        editTextUsername.dialogMessage = context.getString(R.string.username_dialog_message)

        editTextPassword.key = context.getString(R.string.password_key)
        editTextPassword.title = context.getString(R.string.password_title)
        editTextPassword.summary = context.getString(R.string.password_summary)
        editTextPassword.dialogTitle = context.getString(R.string.password_title)
        editTextPassword.dialogMessage = context.getString(R.string.password_dialog_message)
    }

    override fun name(): String = name
    override fun mediaCategories(): List<String> = listOf(CURATED, GENRES, TOP100, PLAYLISTS)
    override fun preferences() = listOf(editTextUsername, editTextPassword)

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        callback: Callback<List<MediaMetadataCompat>>,
        refresh: Boolean
    ) {
        when (mediaCategory) {
            CURATED, GENRES, TOP100 -> callback.onResult(api.getGenres(refresh))
            PLAYLISTS -> callback.onResult(api.getMyPlaylists(refresh))
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        callback: Callback<List<MediaMetadataCompat>>,
        refresh: Boolean
    ) {
        when (mediaCategory) {
            CURATED -> callback.onResult(api.getCuratedPlaylists(path, refresh))
            GENRES -> callback.onResult(api.getTracks(path, refresh))
            TOP100 -> callback.onResult(api.getTop100(path, refresh))
            PLAYLISTS -> callback.onResult(
                api.getPlaylist(
                    path,
                    Endpoints.PLAYLIST_TRACKS,
                    refresh
                )
            )
        }
    }

    @Throws(Exception::class)
    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        query: String,
        callback: Callback<List<MediaMetadataCompat>>,
        refresh: Boolean
    ) {
        callback.onResult(api.query(query, refresh))
    }

    override fun getMediaUrl(
        metadata: MediaMetadataCompat,
        callback: Callback<Pair<MediaMetadataCompat, MediaDataSource?>>
    ) {
        if (!api.hasSubscription) {
            callback.onResult(
                Pair(
                    MediaMetadataCompat.Builder(metadata)
                        .putString(
                            MediaMetadataCompatExt.METADATA_KEY_DOWNLOAD_URL,
                            metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
                        )
                        .build(), null
                )
            )
            return
        }
        val steamUrl =
            api.getStreamUrl(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
        callback.onResult(
            Pair(
                MediaMetadataCompat.Builder(metadata)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, steamUrl)
                    .putString(MediaMetadataCompatExt.METADATA_KEY_DOWNLOAD_URL, steamUrl)
                    .build(), null
            )
        )
    }

    override fun getIcon(): Bitmap = icon
}