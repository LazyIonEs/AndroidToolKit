package org.tool.kit.feature.app

/** Platform completion is acknowledged before posting the success notification. */
fun interface ClipboardWriter { suspend fun write(value: String) }
