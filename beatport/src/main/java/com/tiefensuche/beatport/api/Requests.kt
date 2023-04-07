/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.tiefensuche.beatport.api

import com.tiefensuche.beatport.api.Constants.JSON_NULL
import com.tiefensuche.beatport.api.Constants.RESULTS
import com.tiefensuche.beatport.api.Constants.TRACKS
import com.tiefensuche.soundcrowd.extensions.WebRequests
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader

class Requests {
    enum class Method(val value: String) {
        GET("GET"), POST("POST"), PUT("PUT"), DELETE("DELETE")
    }

    open class Endpoint(route: String, val method: Method) {
        open val url = "${Endpoints.BASE_URL}$route"
    }

    class CollectionEndpoint(route: String, size: Int = 50) : Endpoint(route, Method.GET) {
        override val url = append(super.url, "per_page", "$size")
        private fun append(url: String, key: String, value: String?): String {
            return url + when {
                url.contains('?') -> '&'
                else -> '?'
            } + "$key=$value"
        }
    }

    class CollectionRequest(
        session: BeatportApi,
        endpoint: CollectionEndpoint,
        private val reset: Boolean,
        vararg args: Any?
    ) : Request<JSONArray>(session, endpoint, *args) {
        override fun execute(): JSONArray {
            var currentUrl = url

            if (!reset && url in session.nextQueryUrls) {
                session.nextQueryUrls[url]?.let {
                    if (it == JSON_NULL) {
                        return JSONArray()
                    }
                    currentUrl = it
                } ?: return JSONArray()
            }

            val response = request(currentUrl)
            val json = JSONObject(response.value)
            if (!json.isNull(Constants.NEXT)) {
                session.nextQueryUrls[url] = json.getString(Constants.NEXT)
                if (!session.nextQueryUrls[url]!!.startsWith("https://"))
                    session.nextQueryUrls[url] = "https://" + session.nextQueryUrls[url]
            } else {
                session.nextQueryUrls[url] = JSON_NULL
            }

            if (json.has(RESULTS)) {
                return json.getJSONArray(RESULTS)
            }
            if (json.has(TRACKS)) {
                return json.getJSONArray(TRACKS)
            }
            return JSONArray()
        }
    }

    class ActionRequest(
        session: BeatportApi,
        endpoint: Endpoint,
        val data: String? = null,
        vararg args: Any?
    ) : Request<WebRequests.Response>(session, endpoint, *args) {
        override fun execute(): WebRequests.Response {
            return request(url, data)
        }
    }

    abstract class Request<T>(val session: BeatportApi, val endpoint: Endpoint, vararg args: Any?) {
        val url = endpoint.url.format(*args)
        fun request(url: String, data: String? = null): WebRequests.Response {
            if (session.accessToken == null) {
                session.getAccessToken()
            }

            val con = WebRequests.createConnection(url)
            con.requestMethod = endpoint.method.value
            con.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            if (data != null) {
                con.setRequestProperty("content-type", "application/json")
                con.doOutput = true
                con.outputStream.write(data.toByteArray())
            }
            if (con.responseCode < 400) {
                return WebRequests.Response(
                    con.responseCode,
                    con.inputStream.bufferedReader().use(BufferedReader::readText)
                )
            } else if (con.responseCode == 401) {
                session.getAccessToken()
                return request(url, data)
            }
            throw WebRequests.HttpException(
                con.responseCode,
                con.errorStream.bufferedReader().use(BufferedReader::readText)
            )
        }

        abstract fun execute(): T
    }
}