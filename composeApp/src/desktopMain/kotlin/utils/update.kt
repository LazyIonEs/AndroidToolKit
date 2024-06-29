package utils

import androidx.compose.runtime.MutableState

inline fun <T> MutableState<T>.update(
    transform: (T) -> T
): T = run {
    transform(this.value).also { this.value = it }
}