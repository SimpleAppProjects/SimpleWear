package com.thewizrd.simplewear.utils

import androidx.annotation.StringRes

interface ErrorMessage {
    data class Resource(
        @StringRes val stringId: Int
    ) : ErrorMessage

    data class String(
        val message: kotlin.String
    ) : ErrorMessage
}