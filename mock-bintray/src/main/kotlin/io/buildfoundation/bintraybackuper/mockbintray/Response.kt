package io.buildfoundation.bintraybackuper.mockbintray

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Headers
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import java.io.File

private val jsonParser = Moshi
        .Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

internal data class Response(val code: Int, val headers: Headers, val body: Buffer)

internal fun Response.toMockResponse(): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeaders(headers)
        .setBody(body)

internal fun parseResponse(file: File): Response {
    if (!file.exists()) return Response(code = 404, headers = Headers.of(), body = Buffer())

    val responseJson = jsonParser
            .adapter(ResponseJson::class.java)
            .fromJson(file.readText())!!

    return Response(
            code = responseJson.code,
            headers = Headers.of(responseJson
                    .headers
                    .map { header ->
                        header
                                .split(":")
                                .also { split -> if (split.size != 2) throw IllegalStateException("One of headers in file ${file.absolutePath} doesn't contain ':'") }
                                .let { it[0] to it[1] }
                    }
                    .toMap()
            ),
            body = Buffer().writeUtf8(responseJson.body)
    )
}

private data class ResponseJson(
        @Json(name = "code")
        val code: Int,

        @Json(name = "headers")
        val headers: List<String>,

        @Json(name = "body") val body: String
)
