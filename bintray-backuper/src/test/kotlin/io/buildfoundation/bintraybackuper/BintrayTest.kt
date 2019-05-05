package io.buildfoundation.bintraybackuper

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit.SECONDS

class BintrayTest {

    @get:Rule
    val testDir = TemporaryFolder()

    private val testServer = MockWebServer()
    private val httpClient = OkHttpClient()
    private val jsonParser = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Before
    fun setup() {
        testServer.start()
    }

    @After
    fun teardown() {
        testServer.shutdown()
    }

    @Test
    fun getRepos_success() {
        testServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""
                [
                  {
                    "name": "repo1",
                    "owner": "subject1"
                  },
                  {
                    "name": "repo2",
                    "owner": "subject2"
                  },
                  {
                    "name": "repo3",
                    "owner": "subject3"
                  }
                ]
            """))

        getRepos(httpClient, jsonParser, testServer.url(""), "testorg")
            .test()
            .awaitDone(5, SECONDS)
            .assertNoErrors()
            .assertComplete()
            .assertValues(BintrayRepo("repo1"), BintrayRepo("repo2"), BintrayRepo("repo3"))

        assertThat(testServer.requestCount).isEqualTo(1)
        assertThat(testServer.takeRequest().path).isEqualTo("/repos/testorg")
    }

    @Test
    fun getPackages_single_page_success() {
        testServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""
                [
                  {
                    "name": "package1",
                    "linked": false
                  },
                  {
                    "name": "package2",
                    "linked": false
                  },
                  {
                    "name": "package3",
                    "linked": false
                  }
                ]
            """))

        getPackages(httpClient, jsonParser, testServer.url(""), "testorg", BintrayRepo("testrepo"), 0)
            .test()
            .awaitDone(5, SECONDS)
            .assertNoErrors()
            .assertComplete()
            .assertValues(BintrayPackage("package1"), BintrayPackage("package2"), BintrayPackage("package3"))

        assertThat(testServer.requestCount).isEqualTo(1)

        testServer.takeRequest().also { request ->
            assertThat(request.requestUrl.encodedPath()).isEqualTo("/repos/testorg/testrepo/packages")
            assertThat(request.requestUrl.queryParameter("start_pos")).isEqualTo("0")
        }
    }

    @Test
    fun getPackages_multi_page_success() {
        testServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("X-RangeLimit-Total", "9")
            .setHeader("X-RangeLimit-EndPos", "3")
            .setBody("""
                [
                  {
                    "name": "package1",
                    "linked": false
                  },
                  {
                    "name": "package2",
                    "linked": false
                  },
                  {
                    "name": "package3",
                    "linked": false
                  }
                ]
            """))

        testServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("X-RangeLimit-Total", "9")
            .setHeader("X-RangeLimit-EndPos", "6")
            .setBody("""
                [
                  {
                    "name": "package4",
                    "linked": false
                  },
                  {
                    "name": "package5",
                    "linked": false
                  },
                  {
                    "name": "package6",
                    "linked": false
                  }
                ]
            """))

        testServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("X-RangeLimit-Total", "9")
            .setHeader("X-RangeLimit-EndPos", "9")
            .setBody("""
                [
                  {
                    "name": "package7",
                    "linked": false
                  },
                  {
                    "name": "package8",
                    "linked": false
                  },
                  {
                    "name": "package9",
                    "linked": false
                  }
                ]
            """))

        getPackages(httpClient, jsonParser, testServer.url(""), "testorg", BintrayRepo("testrepo"), 0)
            .test()
            .awaitDone(5, SECONDS)
            .assertNoErrors()
            .assertComplete()
            .assertValues(
                BintrayPackage("package1"),
                BintrayPackage("package2"),
                BintrayPackage("package3"),
                BintrayPackage("package4"),
                BintrayPackage("package5"),
                BintrayPackage("package6"),
                BintrayPackage("package7"),
                BintrayPackage("package8"),
                BintrayPackage("package9")
            )

        assertThat(testServer.requestCount).isEqualTo(3)

        testServer.takeRequest().also { request ->
            assertThat(request.requestUrl.encodedPath()).isEqualTo("/repos/testorg/testrepo/packages")
            assertThat(request.requestUrl.queryParameter("start_pos")).isEqualTo("0")
        }

        testServer.takeRequest().also { request ->
            assertThat(request.requestUrl.encodedPath()).isEqualTo("/repos/testorg/testrepo/packages")
            assertThat(request.requestUrl.queryParameter("start_pos")).isEqualTo("3")
        }

        testServer.takeRequest().also { request ->
            assertThat(request.requestUrl.encodedPath()).isEqualTo("/repos/testorg/testrepo/packages")
            assertThat(request.requestUrl.queryParameter("start_pos")).isEqualTo("6")
        }
    }

    @Test
    fun getPackageFiles_success() {
        testServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""
               [
                  {
                    "name": "nutcracker-1.1-sources.jar",
                    "path": "org/jfrog/powerutils/nutcracker/1.1/nutcracker-1.1-sources.jar",
                    "package": "jfrog-power-utils",
                    "version": "1.1",
                    "repo": "jfrog-jars",
                    "owner": "jfrog",
                    "created": "ISO8601 (yyyy-MM-dd'T'HH:mm:ss.SSSZ)",
                    "size": 1234,
                    "sha1": "602e20176706d3cc7535f01ffdbe91b270ae5012"
                  },
                  {
                    "name": "nutcracker-1.1.jar",
                    "path": "org/jfrog/powerutils/nutcracker/1.1/nutcracker-1.1.jar",
                    "package": "jfrog-power-utils",
                    "version": "1.1",
                    "repo": "jfrog-jars",
                    "owner": "jfrog",
                    "created": "ISO8601 (yyyy-MM-dd'T'HH:mm:ss.SSSZ)",
                    "size": 1234,
                    "sha1": "602e20176706d3cc7535f01ffdbe91b270ae5013"
                  },
                  {
                    "name": "nutcracker-1.1.jar",
                    "path": "org/jfrog/powerutils/nutcracker/1.1/nutcracker-1.1-javadoc.jar",
                    "package": "jfrog-power-utils",
                    "version": "1.1",
                    "repo": "jfrog-jars",
                    "owner": "jfrog",
                    "created": "ISO8601 (yyyy-MM-dd'T'HH:mm:ss.SSSZ)",
                    "size": 1234,
                    "sha1": "602e20176706d3cc7535f01ffdbe91b270ae5014"
                  }
               ]
            """)
        )

        getPackageFiles(httpClient, jsonParser, testServer.url(""), "testorg", BintrayRepo("testrepo"), BintrayPackage("testpackage"))
            .test()
            .awaitDone(5, SECONDS)
            .assertNoErrors()
            .assertComplete()
            .assertValues(
                BintrayFile("org/jfrog/powerutils/nutcracker/1.1/nutcracker-1.1-sources.jar", "602e20176706d3cc7535f01ffdbe91b270ae5012"),
                BintrayFile("org/jfrog/powerutils/nutcracker/1.1/nutcracker-1.1.jar", "602e20176706d3cc7535f01ffdbe91b270ae5013"),
                BintrayFile("org/jfrog/powerutils/nutcracker/1.1/nutcracker-1.1-javadoc.jar", "602e20176706d3cc7535f01ffdbe91b270ae5014")
            )

        assertThat(testServer.requestCount).isEqualTo(1)

        testServer.takeRequest().also { request ->
            assertThat(request.requestUrl.encodedPath()).isEqualTo("/packages/testorg/testrepo/testpackage/files")
            assertThat(request.requestUrl.queryParameter("include_unpublished")).isEqualTo("1")
        }
    }

    @Test
    fun downloadFile_success() {
        val testContent = "test content"
        testServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody(testContent)
        )

        val testFile = testDir.newFile()

        downloadFile(httpClient, testServer.url(""), "testorg", BintrayRepo("testrepo"), BintrayFile("org/jfrog/powerutils/nutcracker/1.1/nutcracker-1.1-sources.jar", "602e20176706d3cc7535f01ffdbe91b270ae5012"), testFile, 16 * 1024)
            .test()
            .awaitDone(5, SECONDS)
            .assertNoErrors()
            .assertComplete()

        assertThat(testServer.requestCount).isEqualTo(1)

        testServer.takeRequest().also { request ->
            assertThat(request.requestUrl.encodedPath()).isEqualTo("/testorg/testrepo/org/jfrog/powerutils/nutcracker/1.1/nutcracker-1.1-sources.jar")
        }

        assertThat(testFile).hasBinaryContent(testContent.toByteArray())
    }
}
