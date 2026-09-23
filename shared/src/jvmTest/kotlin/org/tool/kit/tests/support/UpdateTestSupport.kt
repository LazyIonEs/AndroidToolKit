package org.tool.kit.tests.support

import org.tool.kit.domain.repository.UpdateAsset
import org.tool.kit.domain.repository.UpdateRelease

internal val release = UpdateRelease("99.0", "https://example.invalid/release", "2026-01-01", "fixture release notes",
    listOf(UpdateAsset("fixture-arm64.dmg", "https://example.invalid/first"), UpdateAsset("fixture-arm64.zip", "https://example.invalid/second")))
