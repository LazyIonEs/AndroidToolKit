package org.tool.kit.data.generator

import java.io.File

/** Carries statistics to the batch coordinator without publishing an extra report file. */
data class JunkGeneratedArchive(val file: File, val report: JunkGenerationReport? = null)
