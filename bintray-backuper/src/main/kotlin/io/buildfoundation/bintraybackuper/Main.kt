@file:JvmName("Main")

package io.buildfoundation.bintraybackuper

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.buildfoundation.bintraybackuper.DownloadSource.Downloaded
import io.buildfoundation.bintraybackuper.DownloadSource.LocalCacheHit
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.exceptions.UndeliverableException
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import org.apache.commons.io.FileUtils.byteCountToDisplaySize
import java.io.File
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

fun main(rawArgs: Array<String>) {
    val startTimeNanos = System.nanoTime()
    val args = parseArgs(rawArgs)

    registerRxJavaHooks()

    val httpClient = createHttpClient(
        connectionTimeout = args.httpConnectionTimeout,
        writeTimeout = args.httpWriteTimeout,
        readTimeout = args.httpReadTimeout,
        callTimeout = args.httpCallTimeout,
        credentials = args.basicAuthCredentials
    )

    val jsonParser = createJsonParser()

    val downloadScheduler = Schedulers.from(Executors.newFixedThreadPool(args.httpThreads))
    val checksumScheduler = Schedulers.from(Executors.newFixedThreadPool(args.checksumThreads))

    val discoveredFiles = Collections.newSetFromMap(ConcurrentHashMap<BintrayFile, Boolean>())
    val resolvedFiles = Collections.newSetFromMap(ConcurrentHashMap<BintrayFile, Boolean>())

    val results = getRepos(httpClient, jsonParser, args.apiEndpoint, args.subject)
        .doOnNext { repo -> println("Discovered repo: '${args.subject}/${repo.name}'") }
        .flatMap { repo ->
            getPackages(httpClient, jsonParser, args.apiEndpoint, args.subject, repo, startPosition = 0)
                .subscribeOn(downloadScheduler)
                .map { pkg -> repo to pkg }
        }
        .doOnNext { (repo, pkg) -> println("Discovered package: '${args.subject}/${repo.name}/${pkg.name}'") }
        .flatMap { (repo, pkg) ->
            getPackageFiles(httpClient, jsonParser, args.apiEndpoint, args.subject, repo, pkg)
                .subscribeOn(downloadScheduler)
                .map { file -> Triple(repo, pkg, file) }
        }
        .doOnNext { (repo, pkg, file) ->
            discoveredFiles.add(file)
            println("Discovered file: '${args.subject}/${repo.name}/${pkg.name}/${file.path}'")
        }
        .flatMapSingle { (repo, pkg, file) ->
            Single
                .fromCallable { File(args.downloadDir, "${args.subject}/${repo.name}/${pkg.name}/${file.path}") }
                .flatMap { destinationFile ->
                    val downloadAndVerifyChecksum = downloadFile(httpClient, args.downloadsEndpoint, args.subject, repo, file, destinationFile, args.networkBufferBytes)
                        .subscribeOn(downloadScheduler)
                        .observeOn(checksumScheduler)
                        .andThen(verifySha1Checksum(destinationFile, args.checksumBufferBytes, file.sha1))
                        .doOnError { error -> System.err.println("Warning: problem downloading '${args.subject}/${repo.name}/${pkg.name}/${file.path}': $error, retrying...") }
                        .retry(args.downloadRetries)
                        .andThen(Single.just(DownloadResult(file, destinationFile, Downloaded)))

                    if (destinationFile.exists()) {
                        verifySha1Checksum(destinationFile, args.checksumBufferBytes, file.sha1)
                            .subscribeOn(checksumScheduler)
                            .andThen(Single.just(DownloadResult(file, destinationFile, LocalCacheHit)))
                            .onErrorResumeNext { error: Throwable ->
                                when (error) {
                                    is ChecksumMismatchException -> {
                                        System.err.println("Warning: '$destinationFile': $error, deleting the file and trying again...")

                                        Completable
                                            .fromAction { destinationFile.delete() }
                                            .andThen(downloadAndVerifyChecksum)
                                    }
                                    else -> throw error
                                }
                            }
                    } else {
                        Completable
                            .fromAction { destinationFile.parentFile.mkdirs() }
                            .andThen(downloadAndVerifyChecksum)
                    }
                }
        }
        .doOnNext { downloadResult ->
            resolvedFiles.add(downloadResult.file)
            println("File '${downloadResult.destinationFile}': ${downloadResult.source}, ${byteCountToDisplaySize(downloadResult.destinationFile.length())}")
        }
        .scan(Results(filesCount = 0, totalBytes = 0)) { results, downloadResult -> Results(results.filesCount + 1, downloadResult.destinationFile.length()) }
        .lastOrError()
        .doOnError { error ->
            System.err.println("Fatal error: $error, took ${took(startTimeNanos)}.")
            error.printStackTrace()
            System.exit(1)
        }
        .blockingGet()

    println("Done: ${results.filesCount} files, ${byteCountToDisplaySize(results.totalBytes)}, took ${took(startTimeNanos)}")
    System.exit(0)
}

private fun registerRxJavaHooks() {
    RxJavaPlugins.setErrorHandler { error ->
        if (error is UndeliverableException) {
            System.err.println("Undeliverable RxJava error has occurred: $error")
            error.printStackTrace()
            System.exit(1)
        }
    }
}

private fun createHttpClient(connectionTimeout: Duration, writeTimeout: Duration, readTimeout: Duration, callTimeout: Duration, credentials: String?): OkHttpClient {
    val authenticatedHosts = setOf("api.bintray.com", "dl.bintray.com")

    return OkHttpClient
        .Builder()
        .let {
            if (credentials == null) {
                it
            } else {
                it.addInterceptor { chain ->
                    if (chain.request().url().host() in authenticatedHosts) {
                        chain.proceed(
                            chain
                                .request()
                                .newBuilder()
                                .addHeader("Authorization", credentials)
                                .build()
                        )
                    } else {
                        chain.proceed(chain.request())
                    }
                }
            }
        }
        .connectTimeout(connectionTimeout)
        .writeTimeout(writeTimeout)
        .readTimeout(readTimeout)
        .callTimeout(callTimeout)
        .build()
}

private fun createJsonParser(): Moshi = Moshi
    .Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

private enum class DownloadSource {
    Downloaded,
    LocalCacheHit,
}

private data class DownloadResult(
    val file: BintrayFile,
    val destinationFile: File,
    val source: DownloadSource
)

private data class Results(
    val filesCount: Int,
    val totalBytes: Long
)

private fun took(startTimeNanos: Long): String {
    val duration = Duration.ofNanos(System.nanoTime() - startTimeNanos)

    val hours = duration.toHours()
    val minutes = duration.minusHours(hours).toMinutes()
    val seconds = duration.minusHours(hours).minusMinutes(minutes).seconds
    val millis = duration.minusHours(hours).minusMinutes(minutes).minusSeconds(seconds).toMillis()

    return "$hours:$minutes:$seconds.$millis (hours:minutes:seconds.millis)"
}


