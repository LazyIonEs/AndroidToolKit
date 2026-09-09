package org.tool.kit.vm

/** The legacy VM is the sole input owner until the SettingsViewModel cutover. */
data class SettingsInputDraft(val outputPath: String, val signerSuffix: String)
