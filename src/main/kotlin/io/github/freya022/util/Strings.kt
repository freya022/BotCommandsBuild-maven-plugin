package io.github.freya022.util

fun String.tryAppendDot(): String = this.substringBeforeLast('.') + "."