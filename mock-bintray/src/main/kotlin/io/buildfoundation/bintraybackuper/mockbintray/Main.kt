@file:JvmName("Main")

package io.buildfoundation.bintraybackuper.mockbintray

import kotlin.concurrent.thread

fun main(rawArgs: Array<String>) {
    val args = parseArgs(rawArgs)
    val disposable = startMockBintrayServer(args.port, args.dataDir)

    Runtime
            .getRuntime()
            .addShutdownHook(thread(start = false) {
                disposable.dispose()
            })
}
