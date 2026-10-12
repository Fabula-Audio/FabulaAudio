package com.fabula.audio

/** Decode only the first audio stream, preserving its sample rate and channels.
 * Artwork, video and container metadata must not enter the FLAC processing chain.
 */
object AudioImport {
    const val OUTPUT_OPTIONS = "-map 0:a:0 -vn -sn -dn -map_metadata -1"
}
