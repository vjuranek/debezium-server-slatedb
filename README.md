# Debezium Server SlateDB Sink

A [Debezium Server](https://debezium.io/documentation/reference/stable/operations/debezium-server.html) sink adapter
that stores change events in a [SlateDB](https://github.com/slatedb/slatedb) database.

The sink uses the official [SlateDB Java client](https://github.com/slatedb/slatedb/tree/main/bindings/java)
(`io.slatedb:slatedb-uniffi`), which bundles the native SlateDB library. The adapter extracts the native library
for the current platform at startup, so no manual native installation is required on the supported platforms
(`linux-x86-64`, `linux-aarch64`, `darwin-x86-64`, `darwin-aarch64`, `win32-x86-64`, `win32-aarch64`).

## How it works

Each change event is written to SlateDB as a key/value pair:

* the event key and value are serialized to bytes (`byte[]` and `String` payloads are both supported);
* a whole Debezium batch is written using a single SlateDB `WriteBatch`;
* tombstone events (events with a `null` value) are translated into SlateDB deletes;
* events without a key are skipped (a key/value store cannot address a value without a key).

## Configuration

Enable the sink with `debezium.sink.type=slatedb` and configure it with the following properties.

| Property                                 | Default        | Description                                                                                                                                                 |
|------------------------------------------|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `debezium.sink.slatedb.objectstore.uri` | `file:///`     | URI of the object store backing the database, e.g. `file:///`, `memory:///` or `s3://bucket`. The URI must identify only the backend and contain no path. |
| `debezium.sink.slatedb.path`            | `/tmp/slatedb` | Path (prefix) of the database within the object store. For `file:///` this is an absolute filesystem path; for `s3://bucket` it is the key prefix.          |
| `debezium.sink.slatedb.flush.on.batch`  | `true`         | Whether to flush the database to the object store after each processed batch. Disabling it defers durability to SlateDB's own flush policy.                |
| `debezium.sink.slatedb.operation.timeout.ms` | `30000`    | Maximum time in milliseconds to wait for a single asynchronous SlateDB operation.                                                                          |

Credentials and other options for remote object stores (such as S3) are picked up from the process environment, as
supported by the underlying [`object_store`](https://docs.rs/object_store/latest/object_store/fn.parse_url_opts.html)
crate.

### Example

```properties
debezium.sink.type=slatedb
debezium.sink.slatedb.objectstore.uri=file:///
debezium.sink.slatedb.path=/var/lib/debezium/slatedb
```

## Build

```bash
mvn clean install
```

Use `-Dquick` to skip integration tests and Docker-related steps.
