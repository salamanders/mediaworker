package info.benjaminhill

import info.benjaminhill.mediaworker.*
import info.benjaminhill.stats.normalizeToRange
import info.benjaminhill.stats.pso.OptimizableFunction
import info.benjaminhill.stats.pso.PSOSwarm
import info.benjaminhill.stats.smooth7
import java.io.File

private const val FPS = 60.0

/** Churn through a list of "how big a delta is this frame" and output a "script" for the frame merger */
fun frameDeltasToFrameCounts(frameDeltas: List<Double>, pow: Double): List<Int> {
    var runningDelta = 0.0
    var currentFrameBuffer = 0
    val inputsPerFrame = mutableListOf<Int>()
    frameDeltas.forEach { delta ->
        require(delta in 0.0..1.0)
        // Scale with y, then recurve with X
        runningDelta += Math.pow(delta, pow)
        currentFrameBuffer++
        if (runningDelta >= 1.0) {
            inputsPerFrame.add(currentFrameBuffer)
            currentFrameBuffer = 0
            runningDelta = 0.0
        }
    }
    return inputsPerFrame
}

@ExperimentalUnsignedTypes
fun main() {
    val sourceFile = File("eat")

    println("Getting or loading deltas between thumbnails.")
    val frameDeltasNice = cachedOrCalculated(sourceFile.name) {
        toImageSequence(sourceFile, CommonFilters.ZOOM_CROP_THUMB.filter)
            .map { it.toRawImage() }
            .zipWithNext()
            .map { (a, b) -> a.distance(b) }
            .toList()
    }
        // Playing with reshaping the data
        .smooth7(50)
        //.coercePercentile(.01) // hack off the extremes
        //.boxCox() // reshape the curve.  Nice to do after the coerce, more centered.
        .normalizeToRange() // squash into 0..1, also in case noise makes it so delta is always big

/*
*  Now the fun part: We have to fit the clip into ~30 seconds.
*  At least one frame should be un-merged.
*  Which means the threshold should be at 1.0
* Our only control over how "scrunched" the video is is the power to multiply it by.
 */

    val optimizeThresholdToSeconds = OptimizableFunction(
        arrayOf(
            (0.0).rangeTo(frameDeltasNice.size.toDouble()) // pow
        )
    ) { (pow) ->

        val goalLengthSeconds = 20..40
        val inputsPerFrame = frameDeltasToFrameCounts(frameDeltasNice, pow)
        val totalTimeSec = inputsPerFrame.size / FPS

        Math.abs(goalLengthSeconds.average() - totalTimeSec)
    }
    val pso = PSOSwarm(optimizeThresholdToSeconds)
    pso.run()
    val (pow) = pso.getBest()

    println("Best Values: $pow")
    val inputsPerFrame = frameDeltasToFrameCounts(frameDeltasNice, pow)


    println("Seconds: ${inputsPerFrame.size / FPS}")
    println(inputsPerFrame.joinToString("\n"))

    sequence {
        var bufferedFramesToMerge: ImageAccumulator? = null
        val inputsCountdown = inputsPerFrame.toMutableList()

        toImageSequence(sourceFile).forEach { bi ->
            if (bufferedFramesToMerge == null) {
                bufferedFramesToMerge = ImageAccumulator(bi.width, bi.height)
            }
            bufferedFramesToMerge?.let { ia ->
                ia.add(bi)
                if (inputsCountdown.isNotEmpty() && ia.size >= inputsCountdown.first()) {
                    println(ia.size)
                    yield(ia.toRawImage().toBufferedImage())
                    inputsCountdown.removeAt(0)
                }
            }
        }
    }.framesToVideoFile(File("${sourceFile.nameWithoutExtension}_mushed.mp4"), FPS)
}

