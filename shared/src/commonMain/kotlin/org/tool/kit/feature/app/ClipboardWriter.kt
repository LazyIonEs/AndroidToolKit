package org.tool.kit.feature.app

/** Platform completion is acknowledged before posting the existing success notification. */
fun interface ClipboardWriter { suspend fun write(value: String) }
