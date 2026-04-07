package org.cikit.jsonstream

class JsonParseException(
    val offset: Long,
    message: String,
) : Exception("$message at byte offset $offset")
