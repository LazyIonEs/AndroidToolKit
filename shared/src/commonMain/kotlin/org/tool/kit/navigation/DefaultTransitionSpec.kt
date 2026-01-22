package org.tool.kit.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation3.scene.Scene

/**
 * @author      : Eddy
 * @description : 描述
 * @createDate  : 2026/1/22 14:05
 */
private const val DEFAULT_TRANSITION_DURATION_MILLISECOND = 600

fun <T : Any> defaultTransitionSpec():
        AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    ContentTransform(
        fadeIn(animationSpec = tween(DEFAULT_TRANSITION_DURATION_MILLISECOND)),
        fadeOut(animationSpec = tween(DEFAULT_TRANSITION_DURATION_MILLISECOND)),
    )
}