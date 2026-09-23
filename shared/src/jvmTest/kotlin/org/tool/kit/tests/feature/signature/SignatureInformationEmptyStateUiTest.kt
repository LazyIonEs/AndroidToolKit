package org.tool.kit.tests.feature.signature

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.tool.kit.feature.ui.FeaturePage
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Image
import org.junit.Test
import org.tool.kit.feature.signature.*
import org.tool.kit.theme.AppTheme
import java.io.File
import java.util.Locale
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class SignatureInformationEmptyStateUiTest {
    @Test fun chineseCardsAndSharedAnimationsInside800By600Window() = exercise(Locale.SIMPLIFIED_CHINESE)
    @Test fun englishCardsAndSharedAnimationsInside800By600Window() = exercise(Locale.US)

    private fun exercise(locale: Locale) {
        val originalLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            // Window chrome uses 28 dp vertically and the navigation rail uses 80 dp horizontally.
            runDesktopComposeUiTest(width = 720, height = 572) {
                var dark by mutableStateOf(false)
                var dragging by mutableStateOf(false)
                var state by mutableStateOf(SignatureInformationUiState())
                var picks = 0
                val intents = mutableListOf<SignatureInformationIntent>()
                val target = object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent) = false
                }
                val english = locale == Locale.US
                val heading = if (english) "Drop your file here" else "把文件放进来"
                val output = File(checkNotNull(System.getProperty("test.fixtureRoot")), "signature-empty-e").apply { mkdirs() }
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                        AppTheme(dark) {
                            Surface(Modifier.fillMaxSize().testTag("signature-test-page")) {
                                FeaturePage(busy = state.busy, useDarkTheme = dark) {
                                    SignatureInformationScreen(state, intents::add, { picks++ }, dragging, target)
                                }
                            }
                        }
                    }
                }

                fun capture(name: String) {
                    waitForIdle()
                    Image.makeFromBitmap(onNodeWithTag("signature-test-page").captureToImage().asSkiaBitmap()).use { image ->
                        image.encodeToData()!!.use { output.resolve("${if (english) "en" else "zh"}-$name.png").writeBytes(it.bytes) }
                    }
                }

                fun checkLayoutAndCapture(name: String) {
                    onNodeWithTag("signature-empty").performMouseInput { moveTo(Offset.Zero) }
                    waitForIdle()
                    val root = onNodeWithTag("signature-empty").fetchSemanticsNode().boundsInRoot
                    assertEquals(720f, root.width)
                    assertEquals(572f, root.height)
                    onNodeWithTag("signature-pick-file").assertIsDisplayed().assertIsEnabled()
                    onNodeWithContentDescription("Lottie animation").assertDoesNotExist()
                    val hint = onNodeWithTag("signature-keystore-hint").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    val lowerCard = onNodeWithTag("signature-verification-card").fetchSemanticsNode().boundsInRoot
                    assertTrue(hint.top >= lowerCard.bottom + 12f, "The footer must have its own vertical space")
                    assertTrue(hint.height >= 18f && hint.bottom <= root.bottom - 24f,
                        "The complete footer must remain above the bottom padding")
                    capture(name)
                    val textNodes = onAllNodes(hasText("", substring = true), useUnmergedTree = true)
                    textNodes.fetchSemanticsNodes().forEachIndexed { i, node ->
                        val bounds = node.boundsInRoot
                        assertTrue(root.contains(bounds.topLeft) && bounds.right <= root.right && bounds.bottom <= root.bottom,
                            "Text must fit in the 720 × 572 content area: $bounds")
                        textNodes[i].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { get ->
                            val layouts = mutableListOf<TextLayoutResult>()
                            assertTrue(get(layouts))
                            layouts.forEach { layout ->
                                // Desktop paragraphs can retain the full constraint width even when
                                // the Text node wraps its content. Check the actual drawn lines.
                                for (line in 0 until layout.lineCount) {
                                    assertFalse(layout.isLineEllipsized(line))
                                    assertTrue(layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f,
                                        "Line must fit horizontally: ${layout.layoutInput.text}")
                                    assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f,
                                        "Line must fit vertically: ${layout.layoutInput.text}")
                                }
                                assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1),
                                    "All text must be visible")
                            }
                        }
                    }

                }

                onNodeWithText(heading).assertIsDisplayed()
                onNodeWithTag("signature-pick-file").performClick()
                assertEquals(1, picks, "The import button must invoke the existing picker exactly once")
                assertTrue(intents.isEmpty(), "Opening the picker must not start verification")
                checkLayoutAndCapture("light")
                val cardBounds = onNodeWithTag("signature-import-card").fetchSemanticsNode().boundsInRoot
                val buttonBounds = onNodeWithTag("signature-pick-file").fetchSemanticsNode().boundsInRoot
                val fingerprint = onNodeWithTag("signature-fingerprint-card").fetchSemanticsNode().boundsInRoot
                val certificate = onNodeWithTag("signature-certificate-card").fetchSemanticsNode().boundsInRoot
                val verification = onNodeWithTag("signature-verification-card").fetchSemanticsNode().boundsInRoot
                assertTrue(cardBounds.right < fingerprint.left)
                assertTrue(fingerprint.bottom < certificate.top)
                assertTrue(cardBounds.bottom < verification.top)
                for (theme in listOf(false, true)) {
                    runOnIdle { dark = theme }
                    checkLayoutAndCapture(if (theme) "dark" else "light")
                    mainClock.autoAdvance = false
                    runOnIdle { dragging = true }
                    mainClock.advanceTimeBy(600)
                    onNodeWithTag("signature-drop-animation").assertIsDisplayed()
                    onAllNodesWithContentDescription("Lottie animation").assertCountEquals(1)
                    onNodeWithContentDescription("Lottie animation").assertIsDisplayed()
                    val overlay = onNodeWithTag("signature-drop-animation").fetchSemanticsNode().boundsInRoot
                    assertEquals(720f, overlay.width)
                    assertEquals(572f, overlay.height)
                    assertEquals(cardBounds, onNodeWithTag("signature-import-card").fetchSemanticsNode().boundsInRoot)
                    assertEquals(buttonBounds, onNodeWithTag("signature-pick-file").fetchSemanticsNode().boundsInRoot)
                    onNodeWithTag("signature-pick-file").performClick()
                    assertEquals(1, picks, "The picker must ignore click-through while dragging")
                    capture(if (theme) "drop-dark" else "drop-light")
                    runOnIdle { dragging = false }
                    mainClock.advanceTimeBy(600)
                    onNodeWithContentDescription("Lottie animation").assertDoesNotExist()
                    onNodeWithText(heading).assertIsDisplayed()

                    // Loading is supplied by the same FeaturePage used in the production route.
                    // A stale drag flag must not leave a second animation above the busy overlay.
                    runOnIdle { state = state.copy(phase = VerificationPhase.Loading); dragging = true }
                    mainClock.advanceTimeBy(600)
                    onNodeWithTag("signature-drop-animation").assertDoesNotExist()
                    onNodeWithTag("signature-empty").assertDoesNotExist()
                    onNodeWithTag("signature-pick-file").assertDoesNotExist()
                    onAllNodesWithContentDescription("Lottie animation").assertCountEquals(1)
                    onNodeWithContentDescription("Lottie animation").assertIsDisplayed()
                    capture(if (theme) "loading-dark" else "loading-light")
                    runOnIdle { state = SignatureInformationUiState(); dragging = false }
                    mainClock.advanceTimeBy(600)
                    onNodeWithContentDescription("Lottie animation").assertDoesNotExist()
                    onNodeWithText(heading).assertIsDisplayed()
                    mainClock.autoAdvance = true
                }
                onNodeWithTag("signature-pick-file").performClick()
                assertEquals(2, picks, "The picker must remain available after a failed first read returns to idle")
            }
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
