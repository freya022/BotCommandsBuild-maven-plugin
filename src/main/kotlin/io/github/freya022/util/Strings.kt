package io.github.freya022.util

fun String.tryAppendDot(): String = when {
    this.endsWith('.') -> this
    else -> "$this."
}