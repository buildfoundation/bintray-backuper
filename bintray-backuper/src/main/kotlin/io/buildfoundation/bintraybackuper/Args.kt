package io.buildfoundation.bintraybackuper

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import com.xenomachina.argparser.default
import okhttp3.Credentials
import okhttp3.HttpUrl
import java.io.File
import java.time.Duration

data class Args(
    val subject: String,
    val downloadDir: File,
    val basicAuthCredentials: String?,
    val httpConnectionTimeout: Duration,
    val httpWriteTimeout: Duration,
    val httpReadTimeout: Duration,
    val httpCallTimeout: Duration,
    val networkBufferBytes: Int,
    val httpThreads: Int,
    val checksumThreads: Int,
    val checksumBufferBytes: Int,
    val downloadRetries: Long,
    val apiEndpoint: HttpUrl,
    val downloadsEndpoint: HttpUrl
)

private class IntermediateArgs(parser: ArgParser) {
    val subject by parser
        .storing("--subject", help = "Bintray \"subject\", either org or user name that hosts files.")

    val downloadDir by parser
        .storing("--download-dir", help = "Directory to download files to. Files will be stored in following layout: 'download-dir/subject/repo/path-to-file'. Directory layout will be created if it doesn't exist. Checksum of existing files will be verified against metadata downloaded from Bintray.")

    val httpConnectionTimeoutSeconds by parser
        .storing("--http-connection-timeout", help = "HTTP connection timeout (seconds)")
        .default("30")

    val httpWriteTimeout by parser
        .storing("--http-write-timeout", help = "HTTP write timeout for individual socket write (seconds)")
        .default("60")

    val httpReadTimeout by parser
        .storing("--http-read-timeout", help = "HTTP read timeout for individual socket read (seconds)")
        .default("60")

    val httpCallTimeout by parser
        .storing("--http-call-timeout", help = "HTTP call timeout (seconds)")
        .default("300")

    val networkBufferBytes by parser
        .storing("--network-buffer-bytes", help = "Network stream buffer (bytes)")
        .default("${16 * 1024}")

    val httpThreads by parser
        .storing("--http-threads", help = "Number of threads for HTTP requests")
        .default("6")

    val checksumThreads by parser
        .storing("--checksum-threads", help = "Number of threads for checksum verification, default is number of cores * 6 (disk bound)")
        .default("${Runtime.getRuntime().availableProcessors() * 6}")

    val checksumBufferBytes by parser
        .storing("--checksum-buffer-bytes", help = "Checksum disk stream buffer (bytes)")
        .default("${16 * 1024}")

    val downloadRetries by parser
        .storing("--download-retries", help = "Number of retries to attempt for each download")
        .default("3")

    val apiEndpoint by parser
        .storing("--api-endpoint", help = "Bintray-compatible API endpoint to use")
        .default("https://api.bintray.com/")
        .addValidator {
            try {
                HttpUrl.get(value)
            } catch (e: Exception) {
                throw SystemExitException("Invalid api endpoint url, error = '$e', value = '$value'", returnCode = 1)
            }
        }

    val downloadsEndpoint by parser
        .storing("--downloads-endpoint", help = "Bintray-compatible downloads endpoint to use")
        .default("https://dl.bintray.com/")
        .addValidator {
            try {
                HttpUrl.get(value)
            } catch (e: Exception) {
                throw SystemExitException("Invalid api endpoint url, error = '$e', value = '$value'", returnCode = 1)
            }
        }
}

fun parseArgs(rawArgs: Array<String>): Args {
    val parser = ArgParser(rawArgs)

    try {
        val intermediateArgs = IntermediateArgs(parser)
        parser.force()

        return Args(
            subject = intermediateArgs.subject,
            basicAuthCredentials = (System.getenv("BINTRAY_BACKUPER_API_CREDENTIALS") ?: "").let {
                if (it == "") {
                    null
                } else {
                    val split = it.split(":")

                    if (split.size != 2) {
                        throw SystemExitException("Credentials must be in 'user:apikey' format.", returnCode = 1)
                    }

                    val user = split[0]
                    val apiKey = split[1]

                    Credentials.basic(user, apiKey)
                }
            },
            httpConnectionTimeout = Duration.ofSeconds(intermediateArgs.httpConnectionTimeoutSeconds.toLong()),
            httpWriteTimeout = Duration.ofSeconds(intermediateArgs.httpWriteTimeout.toLong()),
            httpReadTimeout = Duration.ofSeconds(intermediateArgs.httpReadTimeout.toLong()),
            httpCallTimeout = Duration.ofSeconds(intermediateArgs.httpCallTimeout.toLong()),
            networkBufferBytes = intermediateArgs.networkBufferBytes.toInt(),
            httpThreads = intermediateArgs.httpThreads.toInt(),
            checksumThreads = intermediateArgs.checksumThreads.toInt(),
            checksumBufferBytes = intermediateArgs.checksumBufferBytes.toInt(),
            downloadDir = File(intermediateArgs.downloadDir),
            downloadRetries = intermediateArgs.downloadRetries.toLong(),
            apiEndpoint = intermediateArgs.apiEndpoint.let { HttpUrl.get(it) },
            downloadsEndpoint = intermediateArgs.downloadsEndpoint.let { HttpUrl.get(it) }
        )
    } catch (e: SystemExitException) {
        e.printAndExit()
    }
}
