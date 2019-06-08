package io.buildfoundation.bintraybackuper.mockbintray

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.SystemExitException
import java.io.File

internal data class Args(
        val port: Int,
        val dataDir: File
)

private class IntermediateArgs(parser: ArgParser) {
    val port by parser
            .storing("--port", help = "Port to run mock Bintray HTTP server on")

    val dataDir by parser
            .storing("--data-dir", help = "Path to directory with test data")
}

internal fun parseArgs(rawArgs: Array<String>): Args {
    val parser = ArgParser(rawArgs)

    try {
        val intermediateArgs = IntermediateArgs(parser)
        parser.force()

        return Args(
                port = intermediateArgs.port.toInt().also { if (it <= 0 || it >= 65534) throw IllegalArgumentException("Port value must be within (0, 65534)") },
                dataDir = File(intermediateArgs.dataDir).also { if (!it.exists() || !it.isDirectory) throw java.lang.IllegalArgumentException("Data dir must exist and be a directory") }
        )
    } catch (e: SystemExitException) {
        e.printAndExit()
    }
}
