/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.beatport.api

import android.content.Context
import android.content.SharedPreferences
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import com.tiefensuche.beatport.api.Constants.ACCESS_TOKEN
import com.tiefensuche.beatport.api.Constants.LOCATION
import com.tiefensuche.beatport.api.Constants.REFRESH_TOKEN
import com.tiefensuche.beatport.api.Constants.SUBSCRIPTION
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.extensions.WebRequests
import com.tiefensuche.soundcrowd.plugins.beatport.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.collections.HashMap

class BeatportApi(val context: Context, val prefs: SharedPreferences) {
    private val CLIENT_ID = context.getString(R.string.client_id)
    private val REDIRECT_URI = context.getString(R.string.redirect_uri)

    var accessToken: String? = prefs.getString(context.getString(R.string.access_token_key), null)
    var refreshToken: String? = prefs.getString(context.getString(R.string.refresh_token_key), null)
    var hasSubscription: Boolean =
        prefs.getBoolean(context.getString(R.string.has_subscription), false)

    val nextQueryUrls: HashMap<String, String> = HashMap()

    fun getAccessToken() {
        if (refreshToken != null) {
            try {
                val res = WebRequests.post(
                    Endpoints.TOKEN_BASE,
                    "grant_type=refresh_token&refresh_token=$refreshToken&client_id=$CLIENT_ID"
                )
                if (res.status == 200) {
                    val json = JSONObject(res.value)
                    accessToken = json.getString(ACCESS_TOKEN)
                    refreshToken = json.getString(REFRESH_TOKEN)
                    hasSubscription = hasSubscription()
                    prefs.edit()
                        .putString(context.getString(R.string.access_token_key), accessToken)
                        .putString(context.getString(R.string.refresh_token_key), refreshToken)
                        .putBoolean(context.getString(R.string.has_subscription), hasSubscription)
                        .apply()
                    return
                }
            } catch (e: WebRequests.HttpException) {
                // refresh token expired or invalid, continue with standard login
            }
        }
        val username = prefs.getString(context.getString(R.string.username_key), null)
        val password = prefs.getString(context.getString(R.string.password_key), null)
        if (username == null || password == null)
            throw InvalidCredentialsException("Username and/or password missing!")

        var con = WebRequests.createConnection(Endpoints.LOGIN)
        con.setRequestProperty("content-type", "application/json")
        con.requestMethod = "POST"
        con.doOutput = true
        con.outputStream.write("{\"username\":\"$username\",\"password\":\"$password\"}".toByteArray())
        if (con.responseCode != 200)
            throw InvalidCredentialsException("Could not login!")

        val cookie = con.getHeaderField("Set-Cookie")
        con = WebRequests.createConnection(Endpoints.AUTH.format(CLIENT_ID, REDIRECT_URI))
        con.setRequestProperty("Cookie", cookie)
        if (con.responseCode != 302)
            throw Exception("Could not get access token!")

        val code = con.getHeaderField(LOCATION).substringAfter("=").substringBefore("&")
        val json = JSONObject(
            WebRequests.post(
                Endpoints.TOKEN.format(code, CLIENT_ID, REDIRECT_URI),
                ""
            ).value
        )
        accessToken = json.getString(ACCESS_TOKEN)
        refreshToken = json.getString(REFRESH_TOKEN)
        hasSubscription = hasSubscription()

        prefs.edit()
            .putString(context.getString(R.string.access_token_key), accessToken)
            .putString(context.getString(R.string.refresh_token_key), refreshToken)
            .putBoolean(context.getString(R.string.has_subscription), hasSubscription)
            .apply()
    }

    fun getGenres(reset: Boolean): List<MediaMetadataCompat> {
        return parseGenresFromJSONArray(
            Requests.CollectionRequest(this, Endpoints.GENRES, reset).execute()
        )
    }

    fun getTracks(genre: String, reset: Boolean): List<MediaMetadataCompat> {
        return getTracks(Endpoints.TRACKS, genre, reset)
    }

    fun getTop100(genre: String, reset: Boolean): List<MediaMetadataCompat> {
        return getTracks(Endpoints.TOP100, genre, reset)
    }

    fun getTracks(
        type: Requests.CollectionEndpoint,
        arg: String,
        reset: Boolean
    ): List<MediaMetadataCompat> {
        return parseTracksFromJSONArray(
            Requests.CollectionRequest(
                this,
                type, reset, arg
            ).execute()
        )
    }

    fun query(query: String, reset: Boolean): List<MediaMetadataCompat> {
        val trackList = JSONArray()
        val tracks = Requests.CollectionRequest(
            this,
            Endpoints.QUERY_URL,
            reset,
            URLEncoder.encode(query, "UTF-8")
        ).execute()
        for (j in 0 until tracks.length()) {
            trackList.put(tracks.getJSONObject(j))
        }
        return parseTracksFromJSONArray(trackList)
    }

    fun getMyPlaylists(reset: Boolean): List<MediaMetadataCompat> {
        return getPlaylists(Endpoints.PLAYLISTS, "", reset)
    }

    fun getCuratedPlaylists(path: String, reset: Boolean): List<MediaMetadataCompat> {
        if (!path.contains('/'))
            return getPlaylists(Endpoints.CURATED, path, reset)

        return getPlaylist(
            path.substring(path.lastIndexOf('/') + 1),
            Endpoints.CURATED_TRACKS,
            reset
        )
    }

    fun getPlaylists(
        endpoint: Requests.CollectionEndpoint,
        path: String,
        reset: Boolean
    ): List<MediaMetadataCompat> {
        val playlists = Requests.CollectionRequest(this, endpoint, reset, path).execute()
        val result = mutableListOf<MediaMetadataCompat>()
        for (i in 0 until playlists.length()) {
            val playlist = playlists.getJSONObject(i)
            result.add(
                MediaMetadataCompat.Builder()
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                        playlist.getString(Constants.ID)
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_TITLE,
                        playlist.getString(Constants.NAME)
                    )
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "")
                    .putString(
                        MediaMetadataCompatExt.METADATA_KEY_TYPE,
                        MediaMetadataCompatExt.MediaType.STREAM.name
                    )
                    .build()
            )
        }
        return result
    }

    fun getPlaylist(
        id: String,
        endpoint: Requests.CollectionEndpoint,
        reset: Boolean
    ): List<MediaMetadataCompat> {
        val tracks = Requests.CollectionRequest(this, endpoint, reset, id).execute()

        val result = mutableListOf<MediaMetadataCompat>()
        for (i in 0 until tracks.length()) {
            val track = buildTrackFromJSON(tracks.getJSONObject(i).getJSONObject(Constants.TRACK))
            result.add(
                MediaMetadataCompat.Builder(track)
                    .putLong("PLAYLIST_ID", tracks.getJSONObject(i).getLong(Constants.ID))
                    .build()
            )
        }
        return result
    }

    private fun parseGenresFromJSONArray(genres: JSONArray): List<MediaMetadataCompat> {
        val result = mutableListOf<MediaMetadataCompat>()
        for (i in 0 until genres.length()) {
            result.add(buildGenreFromJSON(genres.getJSONObject(i)))
        }
        return result
    }

    private fun parseTracksFromJSONArray(tracks: JSONArray): List<MediaMetadataCompat> {
        val result = mutableListOf<MediaMetadataCompat>()
        for (i in 0 until tracks.length()) {
            result.add(buildTrackFromJSON(tracks.getJSONObject(i)))
        }
        return result
    }

    private fun buildTrackFromJSON(json: JSONObject): MediaMetadataCompat {
        return MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, "")
            .putString(
                MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                json.getLong(Constants.ID).toString()
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                json.getString(Constants.SAMPLE_URL)
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_TITLE,
                json.getJSONObject(Constants.RELEASE).getString(Constants.NAME)
            )
            .putString(
                MediaMetadataCompat.METADATA_KEY_ARTIST,
                json.getJSONArray(Constants.ARTISTS).getJSONObject(0).getString(Constants.NAME)
            )
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, json.getLong(Constants.LENGTH_MS))
            .putString(
                MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                json.getJSONObject(Constants.RELEASE).getJSONObject(Constants.IMAGE)
                    .getString(Constants.URI)
            )
            .putString(
                MediaMetadataCompatExt.METADATA_KEY_TYPE,
                MediaMetadataCompatExt.MediaType.MEDIA.name
            )
            .build()
    }

    private fun buildGenreFromJSON(json: JSONObject): MediaMetadataCompat {
        return MediaMetadataCompat.Builder()
            .putString(
                MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                json.getInt(Constants.ID).toString()
            )
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, json.getString(Constants.NAME))
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, "")
            .putString(MediaMetadataCompatExt.METADATA_KEY_URL, json.getString(Constants.URL))
            .putString(
                MediaMetadataCompatExt.METADATA_KEY_TYPE,
                MediaMetadataCompatExt.MediaType.STREAM.name
            )
            .build()
    }

    fun getStreamUrl(id: String): String {
        val res = Requests.ActionRequest(this, Endpoints.STREAM_URL, data = null, id).execute()
        if (res.status == 200) {
            return JSONObject(res.value).getString(LOCATION)
        }
        throw NotStreamableException("Can not get stream url")
    }

    private fun hasSubscription(): Boolean {
        val result = Requests.ActionRequest(this, Endpoints.INTROSPECT).execute()
        if (result.status == 200) {
            return !JSONObject(result.value).isNull(SUBSCRIPTION)
        }
        return false
    }

    // Exception types
    class InvalidCredentialsException(message: String) : Exception(message)
    class NotStreamableException(message: String) : Exception(message)
}