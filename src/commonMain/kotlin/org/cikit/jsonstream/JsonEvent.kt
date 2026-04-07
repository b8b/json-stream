package org.cikit.jsonstream

sealed class JsonEvent {
    data object InProgress : JsonEvent()
    data object StartObject : JsonEvent()
    data object EndObject : JsonEvent()
    data object StartArray : JsonEvent()
    data object EndArray : JsonEvent()
    data object Value : JsonEvent()
}
