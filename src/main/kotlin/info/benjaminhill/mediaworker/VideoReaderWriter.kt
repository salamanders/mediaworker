package info.benjaminhill.mediaworker

import mu.KotlinLogging
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameUtils
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private val LOG = KotlinLogging.logger {}

fun toImageSequence(
    file: File,
    filterStr: String? = null
) = if (file.isDirectory) {
    folderToImageSequence(file, filterStr)
} else {
    fileToImageSequence(file, filterStr)
}

/**
 * Reads a MP4 into a sequence of BufferedImage.TYPE_3BYTE_BGR
 * @param filterStr ffmpeg compatible filters, ex: "scale=640:-1"
 */
private fun fileToImageSequence(
    sourceFile: File,
    filterStr: String? = null
) = sequence<BufferedImage> {
    require(sourceFile.canRead()) { "Can't read '${sourceFile.absolutePath}'" }
    LOG.debug { "Started video read sequence." }
    avutil.av_log_set_level(avutil.AV_LOG_ERROR)

    var frameCount = 0
    FFmpegFrameGrabber(sourceFile).use { g ->
        LOG.debug { "Starting FFmpegFrameGrabber." }
        g.start()

        val totalFrames = g.lengthInVideoFrames
        val optionalFilter = filterStr?.let {
            val filter = FFmpegFrameFilter(filterStr, g.imageWidth, g.imageHeight)
            filter.pixelFormat = g.pixelFormat
            filter.start()
            filter
        }

        val videoRotation = (g.getVideoMetadata("rotate") ?: "0").toDouble()
        LOG.debug { "Starting frame grab of ${sourceFile.name} (frames:${g.lengthInVideoFrames}, fps:${g.videoFrameRate})" }
        while (true) {
            val rawFrame = g.grabImage() ?: break
            val filteredFrame = optionalFilter?.let {
                it.push(rawFrame)
                it.pull()
            } ?: rawFrame

            yield(Java2DFrameUtils.toBufferedImage(filteredFrame))

            if (frameCount > 0 && frameCount and frameCount - 1 == 0) {
                LOG.debug { "Read frame $frameCount, (${Math.round(100 * frameCount.toDouble() / totalFrames)}%)" }
            }

            frameCount++
        }
        optionalFilter?.stop()
        g.stop()
    }
    LOG.debug { "All frames read: $frameCount" }
}.constrainOnce()

enum class CommonFilters(val filter: String) {
    /** Very small thumbnails */
    ZOOM_CROP_THUMB("crop=in_w*.5:in_h*.5:in_w*.25:in_h*.25,scale=32:32")
}

private fun folderToImageSequence(
    sourceDir: File,
    filterStr: String? = null
) = sequence {
    require(sourceDir.isDirectory)
    var frameCount = 0
    sourceDir
        .walkTopDown()
        .filter { it.isFile && !it.isHidden && it.canRead() }
        .sortedBy { it.nameWithoutExtension }
        .forEach { file ->
            when (file.extension.toLowerCase()) {
                "png", "jpg", "jpeg" -> yield(ImageIO.read(file)).also { if (filterStr != null) LOG.warn { "filter strings can not be used on images, only videos" } }
                "mp4", "mov", "moov" -> yieldAll(fileToImageSequence(file, filterStr))
                else -> LOG.warn { "Don't know how to handle file ${file.name}" }
            }
            frameCount++
        }
    LOG.debug { "All read: $frameCount" }
}

fun Sequence<BufferedImage>.framesToVideoFile(
    destinationFile: File = File("out.mp4"),
    fps: Double = 30.0
) {
    var ffr: FFmpegFrameRecorder? = null
    var frameCount = 0
    for (bi in this) {
        if (ffr == null) {
            ffr = FFmpegFrameRecorder(destinationFile, bi.width, bi.height, 0).apply {
                frameRate = fps
                videoBitrate = 0 // max
                videoQuality = 0.0 // max
                start()
            }
            LOG.info { "Starting recording to ${destinationFile.name} (${ffr.imageWidth}, ${ffr.imageHeight})" }
        }

        val frame = BufferedImage(ffr.imageWidth, ffr.imageHeight, bi.type)
        frame.createGraphics()!!.apply {
            drawImage(bi, 0, 0, null)
            dispose()
        }

        ffr.record(Java2DFrameUtils.toFrame(frame))
        if (frameCount > 0 && frameCount and frameCount - 1 == 0) {
            LOG.debug { "Recorded frame $frameCount (${Math.round(frameCount / fps)}sec." }
        }

        frameCount++
    }

    ffr?.close()
    LOG.debug { "All frames recorded: $frameCount" }
}

