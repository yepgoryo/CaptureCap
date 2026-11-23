/*
 * This code has been co-authored by an AI.
 * Model name: Qwen 3 Coder Next
 */

package com.yepgoryo.CaptureCap

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

import kotlinx.coroutines.*

import java.io.IOException
import java.nio.ByteBuffer

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class RecordingTrimmer(private val context: Context) {
    private val logger = Logger(this, context)

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun trimVideo(
        sourceUri: Uri,
        destUri: Uri,
        startMs: Long,
        endMs: Long
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val durationMs = getVideoDuration(sourceUri)
        if (startMs < 0 || startMs >= endMs || endMs > durationMs) {
            throw IllegalArgumentException("Invalid trim range: [$startMs, $endMs]ms (duration: $durationMs ms)")
        }

        val srcPfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: throw IOException("Source not found")
        val srcFd = srcPfd.fileDescriptor

        val destPfd = context.contentResolver.openFileDescriptor(destUri, "rw") ?: throw IOException("Output URI not writable")

        val muxer = MediaMuxer(destPfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(srcFd)

            var videoTrackIndex = -1
            var audioTrackIndex = -1

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) videoTrackIndex = i
                else if (mime.startsWith("audio/")) audioTrackIndex = i
            }

            if (videoTrackIndex == -1) throw IllegalStateException("No video track found")

            extractor.selectTrack(videoTrackIndex)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val videoFormat = extractor.getTrackFormat(videoTrackIndex)

            muxer.addTrack(videoFormat)

            if (audioTrackIndex != -1) {
                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val audioFormat = extractor.getTrackFormat(audioTrackIndex)
                muxer.addTrack(audioFormat)
            }

            muxer.start()

            val buffer = ByteBuffer.allocateDirect(512 * 1024)
            val info = MediaCodec.BufferInfo()

            val maxDurationUs = (endMs - startMs) * 1000L

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break
                }

                val sampleTimeUs = extractor.sampleTime - (startMs * 1000L)
                if (sampleTimeUs > maxDurationUs) {
                    extractor.advance()
                    break
                }

                buffer.position(0)
                buffer.limit(sampleSize)

                muxer.writeSampleData(
                    extractor.sampleTrackIndex,
                    buffer,
                    info.apply {
                        this.offset = 0
                        this.size = sampleSize
                        this.presentationTimeUs = sampleTimeUs
                        this.flags = extractor.sampleFlags
                    }
                )

                buffer.clear()
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            srcPfd.close()
            destPfd.close()

            Result.success(destUri)

        } catch (e: Exception) {
            logger.logError(e)
            Log.e("TrimVideo", "Trim failed", e)
            muxer?.stop()
            muxer?.release()
            Result.failure(e)
        }
    }

    private fun getVideoDuration(uri: Uri): Long {
        val extractor = MediaExtractor().apply {
            setDataSource(context, uri, null)
        }
        try {
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    return format.getLong(MediaFormat.KEY_DURATION) / 1000
                }
            }
            return 0L
        } finally {
            extractor.release()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun trimAudio(
        sourceUri: Uri,
        destUri: Uri,
        startMs: Long,
        endMs: Long
    ): Result<Uri> = withContext(Dispatchers.IO) {

        val durationMs = getAudioDuration(sourceUri)
        if (startMs < 0 || startMs >= endMs || endMs > durationMs) {
            throw IllegalArgumentException(
                "Invalid trim range: [$startMs, $endMs]ms (duration: $durationMs ms)"
            )
        }

        val srcPfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: throw IOException("Source not found")
        val srcFd = srcPfd.fileDescriptor

        val destPfd = context.contentResolver.openFileDescriptor(destUri, "rw") ?: throw IOException("Output URI not writable")

        val muxer = MediaMuxer(destPfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        try {
            val extractor = MediaExtractor().apply {
                setDataSource(srcFd)
            }

            val audioTrackIndex = (0 until extractor.trackCount).firstOrNull {
                val mime = extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                mime?.startsWith("audio/") == true
            }?.let { extractor.selectTrack(it); it }

            if (audioTrackIndex == null) {
                throw IllegalStateException("No audio track found")
            }

            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val audioFormat = extractor.getTrackFormat(audioTrackIndex)
            val audioMuxerIndex = muxer.addTrack(audioFormat)

            muxer.start()

            val buffer = ByteBuffer.allocateDirect(128 * 1024)
            val info = MediaCodec.BufferInfo()

            val maxDurationUs = (endMs - startMs) * 1000L

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    break
                }

                val sampleTimeUs = extractor.sampleTime - (startMs * 1000L)
                if (sampleTimeUs > maxDurationUs) {
                    break
                }

                val flags = extractor.sampleFlags
                info.set(0, sampleSize, sampleTimeUs, flags)

                buffer.position(0)
                buffer.limit(sampleSize)
                muxer.writeSampleData(audioMuxerIndex, buffer, info)

                buffer.clear()
                extractor.advance()
            }

            if (extractor.sampleSize < 0) {
                muxer.stop()
            } else {
                info.set(0, 0, maxDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                muxer.writeSampleData(audioMuxerIndex, ByteBuffer.allocate(0), info)
                muxer.stop()
            }

            extractor.release()
            srcPfd.close()
            destPfd.close()

            Result.success(destUri)
        } catch (e: Exception) {
            logger.logError(e)
            muxer.stop()
            muxer.release()
            srcPfd.close()
            Result.failure(e)
        }
    }

    private fun getAudioDuration(uri: Uri): Long {
        val extractor = MediaExtractor().apply {
            setDataSource(context, uri, null)
        }
        return try {
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    return durationUs / 1000
                }
            }
            0L
        } finally {
            extractor.release()
        }
    }
}