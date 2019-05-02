package info.benjaminhill.mediaworker

import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.DataBufferInt


/** A big bucket of width, height, and UByteColors */
@ExperimentalUnsignedTypes
open class RawImage(
    internal val width: Int,
    internal val height: Int,
    initialPixels: List<UByteColor>
) {
    private val pixels: Array<UByteColor> = initialPixels.toTypedArray()

    init {
        require(pixels.size == width * height) { "Must init with full set of pixels: ${pixels.size}" }
    }

    constructor(bi: BufferedImage) : this(bi.width, bi.height, bi.toPixels())

    /** BGR IntArray to a TYPE_3BYTE_BGR Image */
    fun toBufferedImage(): BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR).apply {
        setRGB(0, 0, width, height, IntArray(width * height) { i ->
            pixels[i].rgb
        }, 0, width)
    }

    operator fun get(idx: Int) {
        require(idx in 0..pixels.size)
        pixels[idx]
    }

    operator fun set(idx: Int, value: UByteColor) {
        require(idx in 0..pixels.size)
        pixels[idx] = value
    }

    /**
     * Distance between all pixels
     */
    fun distance(other: RawImage): Double {
        var distance = 0.0
        for (i in pixels.indices) {
            distance += pixels[i].distance(other.pixels[i])
        }
        return distance / pixels.size
    }
}

@ExperimentalUnsignedTypes
fun BufferedImage.toPixels(): MutableList<UByteColor> {
    val data = mutableListOf<UByteColor>()
    when (type) {
        BufferedImage.TYPE_3BYTE_BGR,
        BufferedImage.TYPE_4BYTE_ABGR -> (raster.dataBuffer!! as DataBufferByte).data.asIterable()
            .chunked(if (alphaRaster == null) 3 else 4)
            .forEach { channels ->
                // ignore alpha channel 3 if it exists
                data.add(
                    UByteColor(
                        channels[2].toUByte(),
                        channels[1].toUByte(),
                        channels[0].toUByte()
                    )
                )
            }
        BufferedImage.TYPE_INT_RGB,
        BufferedImage.TYPE_INT_BGR,
        BufferedImage.TYPE_INT_ARGB -> (raster.dataBuffer!! as DataBufferInt).data.asIterable()
            .forEach { pixel ->
                // ignore alpha shift 24 if it exists
                data.add(
                    UByteColor(
                        (pixel shr 16).toUByte(),
                        (pixel shr 8).toUByte(),
                        (pixel shr 0).toUByte()
                    )
                )
            }
        else -> throw IllegalArgumentException("Unknown backing image type: $type")
    }
    check(data.size == width * height)
    return data
}


@ExperimentalUnsignedTypes
fun BufferedImage.toRawImage() = RawImage(this)

