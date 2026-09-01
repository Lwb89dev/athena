package com.athena.reader.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** User-facing copy that a ViewModel can hold without being a composable. */
sealed interface UiString {
    data class Literal(val value: String) : UiString
    data class Res(val id: StringResource, val args: List<Any> = emptyList()) : UiString
}

@Composable
fun UiString.text(): String = when (this) {
    is UiString.Literal -> value
    is UiString.Res -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
}
