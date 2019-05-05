package io.buildfoundation.bintraybackuper

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream

data class BintrayRepo(
    @Json(name = "name")
    val name: String
)

data class BintrayPackage(
    @Json(name = "name")
    val name: String
)

data class BintrayFile(
    @Json(name = "path")
    val path: String,

    @Json(name = "sha1")
    val sha1: String
)

/**
 * [API Documentation](https://bintray.com/docs/api/#_get_repositories)
 */
fun getRepos(httpClient: OkHttpClient, jsonParser: Moshi, apiEndpoint: HttpUrl, subject: String): Observable<BintrayRepo> = Single
    .fromCallable {
        httpClient
            .newCall(
                Request
                    .Builder()
                    .get()
                    .url(
                        apiEndpoint
                            .newBuilder()
                            .addPathSegments("repos/$subject")
                            .build()
                    )
                    .build()
            )
            .execute()
            .also { validateResponse(it, "Get repos for '$subject'") }
            .body()!!
            .string()
    }
    .map { json ->
        jsonParser
            .adapter<List<BintrayRepo>>(Types.newParameterizedType(List::class.java, BintrayRepo::class.java))
            .fromJson(json)
    }
    .flatMapObservable { Observable.fromIterable(it) }

/**
 * [API Documentation](https://bintray.com/docs/api/#_get_packages)
 */
fun getPackages(httpClient: OkHttpClient, jsonParser: Moshi, apiEndpoint: HttpUrl, subject: String, repo: BintrayRepo, startPosition: Int): Observable<BintrayPackage> = Single
    .fromCallable {
        httpClient
            .newCall(
                Request
                    .Builder()
                    .get()
                    .url(
                        apiEndpoint
                            .newBuilder()
                            .addPathSegments("repos/$subject/${repo.name}/packages")
                            .addQueryParameter("start_pos", "$startPosition")
                            .build()
                    )
                    .build()
            )
            .execute()
            .also { validateResponse(it, "Get packages for '$subject/${repo.name}'") }
    }
    .flatMapObservable { response ->
        val packages = jsonParser
            .adapter<List<BintrayPackage>>(Types.newParameterizedType(List::class.java, BintrayPackage::class.java))
            .fromJson(response.body()!!.string())

        val total = response.header("X-RangeLimit-Total", "0")!!.toInt()
        val end = response.header("X-RangeLimit-EndPos", "0")!!.toInt()

        if (total == end) {
            Observable.fromIterable(packages)
        } else {
            Observable
                .fromIterable(packages)
                .concatWith(getPackages(httpClient, jsonParser, apiEndpoint, subject, repo, end))
        }
    }
    .distinct()

/**
 * [API Documentation](https://bintray.com/docs/api/#_get_package_files)
 */
fun getPackageFiles(httpClient: OkHttpClient, jsonParser: Moshi, apiEndpoint: HttpUrl, subject: String, repo: BintrayRepo, pkg: BintrayPackage): Observable<BintrayFile> = Single
    .fromCallable {
        httpClient
            .newCall(
                Request
                    .Builder()
                    .get()
                    .url(
                        apiEndpoint
                            .newBuilder()
                            .addPathSegments("packages/$subject/${repo.name}/${pkg.name}/files")
                            .addQueryParameter("include_unpublished", "1")
                            .build()
                    )
                    .build()
            )
            .execute()
            .also { validateResponse(it, "Get package files for '$subject/${repo.name}/${pkg.name}'") }
            .body()!!
            .string()
    }
    .map { json ->
        jsonParser
            .adapter<List<BintrayFile>>(Types.newParameterizedType(List::class.java, BintrayFile::class.java))
            .fromJson(json)
    }
    .flatMapObservable { Observable.fromIterable(it) }

/**
 * [API Documentation](https://bintray.com/docs/api/#_download_content)
 */
fun downloadFile(httpClient: OkHttpClient, downloadsEndpoint: HttpUrl, subject: String, repo: BintrayRepo, file: BintrayFile, destinationFile: File, bufferSizeBytes: Int): Completable = Completable
    .fromAction {
        httpClient
            .newCall(
                Request
                    .Builder()
                    .get()
                    .url(
                        downloadsEndpoint
                            .newBuilder()
                            .addPathSegments("$subject/${repo.name}/${file.path}")
                            .build()
                    )
                    .build()
            )
            .execute()
            .also { validateResponse(it, "Download file '$subject/${repo.name}/${file.path}'") }
            .body()!!
            .byteStream()
            .use { contentStream -> FileOutputStream(destinationFile).use { contentStream.copyTo(it, bufferSizeBytes) } }
    }

private fun validateResponse(response: Response, message: String) {
    if (!response.isSuccessful) {
        throw IllegalStateException("$message request is not successful: $response ${response.body()?.string()}")
    }
}

private operator fun CompositeDisposable.plusAssign(disposable: Disposable): Unit = this.add(disposable).let { Unit }
