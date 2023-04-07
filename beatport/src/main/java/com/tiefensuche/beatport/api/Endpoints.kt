/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.beatport.api

object Endpoints {
    const val BASE_URL = "https://api.beatport.com/v4"
    const val LOGIN = "$BASE_URL/auth/login/"
    const val AUTH = "$BASE_URL/auth/o/authorize/?response_type=code&client_id=%s&redirect_uri=%s"
    const val TOKEN_BASE = "$BASE_URL/auth/o/token/"
    const val TOKEN = "$TOKEN_BASE?code=%s&grant_type=authorization_code&client_id=%s&redirect_uri=%s"

    val GENRES = Requests.CollectionEndpoint("/catalog/genres/")
    val TRACKS = Requests.CollectionEndpoint("/catalog/genres/%s/tracks/?preorder=false")
    val TOP100 = Requests.CollectionEndpoint("/catalog/genres/%s/top/100/?preorder=false")
    val CURATED = Requests.CollectionEndpoint("/curation/playlists/?genre_id=%s")
    val CURATED_TRACKS = Requests.CollectionEndpoint("/curation/playlists/%s/tracks/")
    val PLAYLISTS = Requests.CollectionEndpoint("/my/playlists/")
    val PLAYLIST_TRACKS = Requests.CollectionEndpoint("/my/playlists/%s/tracks/")
    val LIKE_TRACK_URL = Requests.Endpoint("/my/playlists/%s/tracks/bulk/", Requests.Method.POST)
    val UNLIKE_TRACK_URL = Requests.Endpoint("/my/playlists/%s/tracks/%s/", Requests.Method.DELETE)
    val QUERY_URL = Requests.CollectionEndpoint("/catalog/search/?type=tracks&q=%s")
    val STREAM_URL = Requests.Endpoint("/catalog/tracks/%s/download/", Requests.Method.GET)
    val INTROSPECT = Requests.Endpoint("/auth/o/introspect/", Requests.Method.GET)
}