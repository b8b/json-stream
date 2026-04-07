# json-stream

A streaming JSON parser for Kotlin Multiplatform (JVM, JS, WasmJS).

Emits a flat stream of events (`StartObject`, `EndObject`, `StartArray`,
`EndArray`, `Value`) as it tokenizes JSON input, without building an in-memory
tree. Designed for incremental / push-based parsing where input arrives in
chunks.

## Usage

### Push parser

`JsonParser` is the low-level, non-suspending parser. You push byte chunks
into it and pull events out:

```kotlin
val p = JsonParser()
p.push("""{"key": "value", "n": 42}""".encodeToByteArray())
p.finish()

while (true) {
    val event = p.next() ?: break
    when (event) {
        is JsonEvent.StartObject -> println("{")
        is JsonEvent.EndObject   -> println("}")
        is JsonEvent.StartArray  -> println("[")
        is JsonEvent.EndArray    -> println("]")
        is JsonEvent.Value       -> println("value: ${p.currentValue()}")
        is JsonEvent.InProgress  -> { /* need more data -- call push() */ }
    }
}
```

Input can be fed incrementally. When the parser needs more data it returns
`JsonEvent.InProgress`. Push another chunk and continue calling `next()`:

```kotlin
val p = JsonParser()

p.push(firstChunk)
while (true) {
    val event = p.next()!!
    if (event is JsonEvent.InProgress) break
    // handle event
}

p.push(secondChunk)
p.finish()
while (true) {
    val event = p.next() ?: break
    // handle event
}
```

### Suspending parser

`SuspendingJsonParser` wraps `JsonParser` and reads from a Ktor
`ByteReadChannel`. It automatically feeds the underlying parser when it needs
more data:

```kotlin
val parser = SuspendingJsonParser(byteReadChannel)

while (true) {
    val event = parser.next() ?: break
    when (event) {
        is JsonEvent.Value -> println(parser.currentValue())
        // ...
        else -> {}
    }
}
```

Convenience methods build `kotlinx.serialization.json.JsonElement` trees from
the event stream:

```kotlin
val element: JsonElement? = parser.nextJsonElement()
```

To skip over a value without allocating a `JsonElement` tree:

```kotlin
parser.skipJsonElement()
```

### Selective field extraction

Combine `next()` with `skipJsonElement()` to efficiently extract specific
fields from large JSON without materializing the entire structure:

```kotlin
val parser = SuspendingJsonParser(channel)
parser.expect<JsonEvent.StartObject>()

while (true) {
    when (val ev = parser.next()) {
        is JsonEvent.Value -> {
            val fieldName = parser.currentValue()
            if (fieldName == "\"target\"") {
                val value = parser.nextJsonElement()
                // use value
            } else {
                parser.skipJsonElement() // skip fields we don't care about
            }
        }
        is JsonEvent.EndObject -> break
        else -> error("unexpected: $ev")
    }
}
```

## Events

| Event | Meaning |
|---|---|
| `StartObject` | `{` |
| `EndObject` | `}` |
| `StartArray` | `[` |
| `EndArray` | `]` |
| `Value` | A complete value (string, number, boolean, null, or field name) |
| `InProgress` | Buffer exhausted -- push more data (`JsonParser` only) |

Field names and values both emit `Value`. Call `currentValue()` to get the raw
JSON text of the token (strings include their surrounding quotes and escape
sequences).

## Platforms

- JVM
- JS (browser + Node.js)
- WasmJS (browser + Node.js)

## Dependencies

- `kotlinx-serialization-json` 1.10.0
- `kotlinx-coroutines-core` 1.10.2
- `io.ktor:ktor-io` 3.4.0

## Building

```sh
./gradlew build
```

## License

See [LICENSE](LICENSE) for details.
