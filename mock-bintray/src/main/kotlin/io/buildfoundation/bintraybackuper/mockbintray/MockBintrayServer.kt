package io.buildfoundation.bintraybackuper.mockbintray

import io.reactivex.disposables.Disposable
import io.reactivex.disposables.Disposables
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.io.File

fun startMockBintrayServer(port: Int, dataDir: File): Disposable {
    val server = MockWebServer()

    server.dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val response = listOf(
                    getRepos(request, dataDir),
                    getPackages(request, dataDir),
                    getPackageFiles(request, dataDir),
                    downloadFile(request, dataDir)
            )
                    .filterNotNull()
                    .firstOrNull()

            return response ?: MockResponse().setResponseCode(400)
        }
    }

    server.start(port)

    return Disposables.fromAction { server.shutdown() }
}

private fun getRepos(request: RecordedRequest, dataDir: File): MockResponse? {
    if (!(request.requestUrl.pathSegments().let { it.size == 2 && it[0] == "repos" })) return null

    val subject = request.requestUrl.pathSegments()[1]

    return parseResponse(File(dataDir, "$subject/repos.json")).toMockResponse()
}

private fun getPackages(request: RecordedRequest, dataDir: File): MockResponse? {
    if (!(request.requestUrl.pathSegments().let { it.size == 4 && it[0] == "repos" && it[3] == "packages" })) return null

    val subject = request.requestUrl.pathSegments()[1]
    val repo = request.requestUrl.pathSegments()[2]

    return parseResponse(File(dataDir, "$subject/repos/$repo/packages.json")).toMockResponse()
}

private fun getPackageFiles(request: RecordedRequest, dataDir: File): MockResponse? {
    if (!(request.requestUrl.pathSegments().let { it.size == 5 && it[0] == "packages" && it[4] == "files" })) return null

    val subject = request.requestUrl.pathSegments()[1]
    val repo = request.requestUrl.pathSegments()[2]
    val pkg = request.requestUrl.pathSegments()[3]

    return parseResponse(File(dataDir, "packages/$subject/$repo/$pkg/files.json")).toMockResponse()
}

private fun downloadFile(request: RecordedRequest, dataDir: File): MockResponse? {
    if (!(request.requestUrl.pathSegments().let { it.size == 3 })) return null

    val subject = request.requestUrl.pathSegments()[0]
    val repo = request.requestUrl.pathSegments()[1]
    val filePath = request.requestUrl.pathSegments()[2]

    val file = File(dataDir, "downloads/$subject/$repo/$filePath")

    return if (file.exists()) {
        MockResponse()
                .setBody(Buffer().write(file.readBytes()))
    } else {
        MockResponse()
                .setResponseCode(404)
    }
}
