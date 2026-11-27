package com.atlas.desktop.backup

import java.io.File
import java.io.IOException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.gzip
import okio.sink
import okio.source

/**
 * The `.tachibk` stream format, as the Android app writes and reads it
 * ([com.atlas.reader.data.backup.BackupWriter] / `BackupReader`):
 * a gzip stream of protobuf — each [BackupManga] entry as a varint tag
 * (field 1, length-delimited) + varint length + message bytes, then the
 * [Backup] metadata (no entries of its own) as raw protobuf bytes.
 * The gzip wrapper is detected by its magic bytes on read; a plain
 * protobuf stream decodes too, and JSON input is rejected.
 */
class TachibkWriter(
    private val sink: BufferedSink,
    private val parser: ProtoBuf,
) {
    var isEmpty = true
        private set

    fun writeEntry(entry: BackupManga) {
        val bytes = parser.encodeToByteArray(BackupManga.serializer(), entry)
        sink.writeVarint(ENTRY_TAG)
        sink.writeVarint(bytes.size.toLong())
        sink.write(bytes)
        isEmpty = false
    }

    /**
     * Writes the other fields after the entries, as [metadata] without entries of its own.
     */
    fun writeMetadata(metadata: Backup) {
        val bytes = parser.encodeToByteArray(Backup.serializer(), metadata)
        sink.write(bytes)
        if (bytes.isNotEmpty()) isEmpty = false
    }
}

/**
 * Reads an encoded [Backup] one entry at a time. Every other field is set aside for [readMetadata], wherever it
 * appears.
 *
 * Malformed or truncated input throws an [IOException].
 */
class TachibkReader(
    private val source: BufferedSource,
    private val parser: ProtoBuf,
) {
    private val metadata = Buffer()
    private var entryLength = NO_ENTRY

    /**
     * Moves to the next entry, which [readEntry] or [skipEntry] then consumes.
     *
     * @return false once the input is exhausted.
     */
    fun nextEntry(): Boolean {
        check(entryLength == NO_ENTRY)
        while (!source.exhausted()) {
            val tag = source.readVarint()
            val fieldNumber = tag ushr 3
            val wireType = (tag and 0x7).toInt()
            if (fieldNumber == 0L) throw IOException("Invalid field number 0")

            if (tag == ENTRY_TAG) {
                entryLength = source.readLength()
                return true
            }

            metadata.writeVarint(tag)
            when (wireType) {
                WIRE_TYPE_VARINT -> metadata.writeVarint(source.readVarint())
                WIRE_TYPE_FIXED64 -> metadata.write(source, 8)
                WIRE_TYPE_LENGTH_DELIMITED -> {
                    val length = source.readLength()
                    metadata.writeVarint(length)
                    metadata.write(source, length)
                }
                WIRE_TYPE_FIXED32 -> metadata.write(source, 4)
                else -> throw IOException("Unsupported wire type $wireType")
            }
        }
        return false
    }

    fun readEntry(): BackupManga = readEntry(BackupManga.serializer())

    /**
     * Decodes the entry as [deserializer], which can declare only the fields it needs so the rest are skipped.
     */
    fun <T> readEntry(deserializer: DeserializationStrategy<T>): T {
        val bytes = source.readByteArray(takeEntryLength())
        return decode(deserializer, bytes)
    }

    fun skipEntry() {
        source.skip(takeEntryLength())
    }

    /**
     * Skips the remaining entries and decodes everything else as a [Backup] without entries.
     */
    fun readMetadata(): Backup {
        if (entryLength != NO_ENTRY) skipEntry()
        while (nextEntry()) skipEntry()
        return decode(Backup.serializer(), metadata.readByteArray())
    }

    private fun takeEntryLength(): Long {
        check(entryLength != NO_ENTRY)
        return entryLength.also { entryLength = NO_ENTRY }
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        return try {
            parser.decodeFromByteArray(deserializer, bytes)
        } catch (e: SerializationException) {
            throw IOException(e)
        }
    }
}

/** Everything a `.tachibk` file holds: the library entries plus the metadata. */
data class TachibkContents(
    val entries: List<BackupManga>,
    val metadata: Backup,
)

/** Writes [entries] + [metadata] to [file] as a gzipped `.tachibk` stream. */
fun writeBackupFile(file: File, entries: List<BackupManga>, metadata: Backup) {
    file.parentFile?.mkdirs()
    file.sink().gzip().buffer().use { sink ->
        val writer = TachibkWriter(sink, ProtoBuf)
        entries.forEach(writer::writeEntry)
        writer.writeMetadata(metadata)
    }
}

/** Reads a `.tachibk` file (gzipped or plain protobuf) into memory. */
fun readBackupFile(file: File): TachibkContents {
    return file.source().buffer().use { source ->
        val peeked = source.peek().apply {
            require(2)
        }
        val id1id2 = peeked.readShort()
        val backupSource: BufferedSource = when (id1id2.toInt()) {
            0x1f8b -> source.gzip().buffer() // 0x1f8b is gzip magic bytes
            MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                throw IOException("Not a .tachibk backup (looks like JSON)")
            }
            else -> source
        }
        backupSource.use { decoded ->
            val reader = TachibkReader(decoded, ProtoBuf)
            val entries = buildList {
                while (reader.nextEntry()) add(reader.readEntry())
            }
            TachibkContents(entries, reader.readMetadata())
        }
    }
}

private fun BufferedSource.readVarint(): Long {
    var result = 0L
    for (shift in 0 until Long.SIZE_BITS step 7) {
        val byte = readByte().toInt()
        result = result or ((byte and 0x7F).toLong() shl shift)
        if ((byte and 0x80) == 0) return result
    }
    throw IOException("Malformed varint")
}

private fun BufferedSource.readLength(): Long {
    val length = readVarint()
    if (length !in 0..Int.MAX_VALUE) throw IOException("Invalid length $length")
    return length
}

private fun BufferedSink.writeVarint(value: Long) {
    var remaining = value
    while ((remaining and 0x7FL.inv()) != 0L) {
        writeByte((remaining and 0x7F).toInt() or 0x80)
        remaining = remaining ushr 7
    }
    writeByte(remaining.toInt())
}

private const val WIRE_TYPE_VARINT = 0
private const val WIRE_TYPE_FIXED64 = 1
private const val WIRE_TYPE_LENGTH_DELIMITED = 2
private const val WIRE_TYPE_FIXED32 = 5

// Backup.backupManga is field 1, and each entry is length-delimited
private const val ENTRY_TAG = 1L shl 3 or WIRE_TYPE_LENGTH_DELIMITED.toLong()

private const val NO_ENTRY = -1L

private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
