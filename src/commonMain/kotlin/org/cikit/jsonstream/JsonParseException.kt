package org.cikit.jsonstream

/**
 * [offset] is the index of the first byte that could not be accepted.
 */
class JsonParseException(
    val offset: Long,
    message: String,
) : Exception("$message at byte offset $offset")
