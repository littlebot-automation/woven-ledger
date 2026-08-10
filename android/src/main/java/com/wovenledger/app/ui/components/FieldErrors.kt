package com.wovenledger.app.ui.components

import com.google.gson.Gson
import com.wovenledger.app.data.api.ApiErrors
import retrofit2.HttpException

/**
 * The server answers a rejected write with 422 and {"errors": {field: message}},
 * keyed by its own field names. Every form reads it the same way, so the parsing
 * lives once here rather than in each ViewModel.
 *
 * An empty map means the failure was not a per-field one — no connection, a 500,
 * a 404 — and the caller should show [failureMessage] instead.
 */
fun fieldErrorsOf(cause: Throwable, gson: Gson): Map<String, String> {
    if (cause !is HttpException || cause.code() != 422) {
        return emptyMap()
    }

    val body = cause.response()?.errorBody()?.string().orEmpty()

    return runCatching { gson.fromJson(body, ApiErrors::class.java).errors }
        .getOrNull()
        .orEmpty()
}

/**
 * What to say when the write failed for a reason no field can carry.
 *
 * A lost connection no longer reaches here: it is queued and reported as such. What is
 * left is the server answering and refusing, which is never queued — a rejection would
 * be repeated identically on every retry — so "nothing was saved" is still the honest
 * ending for these.
 */
fun failureMessage(cause: Throwable): String = when {
    cause is HttpException && cause.code() == 404 -> "That record is no longer on the server."
    cause is HttpException -> "The server refused the save (${cause.code()}). Nothing was saved."
    else -> "The save could not be completed. Nothing was saved."
}
