package info.benjaminhill.mediaworker

import java.awt.image.BufferedImage

/** RGB totals */
@ExperimentalUnsignedTypes
class ImageAccumulator(
    private val width: Int,
    private val height: Int
) {
    private val accumulatedChannels = UIntArray(width * height * 3)
    private var frames: ULong = 0u

    val size: Int
        get() = frames.toInt()

    fun add(bi: BufferedImage) {
        require(width == bi.width && height == bi.height)
        frames++
        bi.toPixels().forEachIndexed { index, uByteColor ->
            accumulatedChannels[index * 3 + 0] += uByteColor.red.toUInt()
            accumulatedChannels[index * 3 + 1] += uByteColor.green.toUInt()
            accumulatedChannels[index * 3 + 2] += uByteColor.blue.toUInt()
        }
    }

    fun toRawImage(): RawImage {
        val result = RawImage(width, height,
            accumulatedChannels.chunked(3).map { (r, g, b) ->
                UByteColor(
                    (r / frames).toUByte(),
                    (g / frames).toUByte(),
                    (b / frames).toUByte()
                )
            })
        check(width == result.width && height == result.height)
        frames = 0u
        accumulatedChannels.fill(0u)
        return result
    }
}