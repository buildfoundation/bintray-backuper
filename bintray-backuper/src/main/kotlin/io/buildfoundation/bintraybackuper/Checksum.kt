package io.buildfoundation.bintraybackuper

import io.reactivex.Completable
import java.io.File
import java.io.FileInputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*

// We verify sha1 because Bintray provides sha1.
fun verifySha1Checksum(file: File, bufferSizeBytes: Int, expectedSha1: String): Completable = Completable
    .fromAction {
        val digest = MessageDigest.getInstance("SHA1")

        val sha1ByteArray = FileInputStream(file)
            .buffered(bufferSizeBytes)
            .use { fileStream ->
                DigestInputStream(fileStream, digest).use { digestStream ->
                    val buffer = ByteArray(bufferSizeBytes)

                    while (digestStream.read(buffer) != -1) {
                        // DigestInputStream is accumulative, we just need to pipe data through it and finalize digest.
                    }
                    digest.digest()
                }
            }

        val actualSha1 = Formatter().use {
            sha1ByteArray.forEach { byte ->
                it.format("%02x", byte)
            }
            it.toString()
        }

        if (actualSha1 != expectedSha1) {
            throw ChecksumMismatchException("SHA1 mismatch on file $file, expected '$expectedSha1' but was '$actualSha1'")
        }
    }

class ChecksumMismatchException(message: String) : RuntimeException(message)
