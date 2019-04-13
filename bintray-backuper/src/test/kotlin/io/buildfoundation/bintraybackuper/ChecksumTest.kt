package io.buildfoundation.bintraybackuper

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit.SECONDS

class ChecksumTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var testFile: File

    @Before
    fun setup() {
        testFile = tempDir.newFile("test.file")
        testFile.writeText("test content")
    }

    @Test
    fun sha1Matches() {
        verifySha1Checksum(testFile, bufferSizeBytes = 16 * 1024, expectedSha1 = "1eebdf4fdc9fc7bf283031b93f9aef3338de9052")
            .test()
            .awaitDone(5, SECONDS)
            .assertNoErrors()
            .assertComplete()
    }

    @Test
    fun sha1DoesntMatch() {
        verifySha1Checksum(testFile, bufferSizeBytes = 16 * 1024, expectedSha1 = "wrongsha")
            .test()
            .awaitDone(5, SECONDS)
            .assertError(ChecksumMismatchException::class.java)
            .assertErrorMessage("SHA1 mismatch on file ${testFile.absolutePath}, expected 'wrongsha' but was '1eebdf4fdc9fc7bf283031b93f9aef3338de9052'")
    }
}
