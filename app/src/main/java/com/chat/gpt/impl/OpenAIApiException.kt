package com.chat.gpt.impl


internal enum class ApiError {
    UNKNOWN,
    UNAUTHORIZED
}

internal class OpenAIApiException(
    val apiError: ApiError,
    message: String?
): RuntimeException(message)