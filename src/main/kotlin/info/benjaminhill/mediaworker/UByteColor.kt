package info.benjaminhill.mediaworker

import java.awt.Color

@ExperimentalUnsignedTypes
class UByteColor(val red: UByte, val green: UByte, val blue: UByte) {

    fun distance(other: UByteColor): Double {
        var dist = 0.0
        dist += (red.toInt() - other.red.toInt()).let { it * it }
        dist += (green.toInt() - other.green.toInt()).let { it * it }
        dist += (blue.toInt() - other.blue.toInt()).let { it * it }
        dist = Math.sqrt(dist)
        dist /= (3 * 255)
        check(dist in 0.0..1.0)
        return dist
    }

    val rgb: Int
        get() = Color(red.toInt(), green.toInt(), blue.toInt()).rgb

    override fun toString() = "{r:$red, g:$green, b:$blue}"
}

@ExperimentalUnsignedTypes
fun Collection<UByteColor>.average(): UByteColor {
    val red = sumBy { it.red.toInt() }
    val green = sumBy { it.green.toInt() }
    val blue = sumBy { it.blue.toInt() }
    return UByteColor((red / size).toUByte(), (green / size).toUByte(), (blue / size).toUByte())
}