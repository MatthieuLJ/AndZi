package com.matthieu.zi_app.domain.writing

import kotlin.math.sqrt

data class HanziPoint(
    val x: Float,
    val y: Float
) {
    operator fun minus(other: HanziPoint): HanziPoint = HanziPoint(x - other.x, y - other.y)
    operator fun plus(other: HanziPoint): HanziPoint = HanziPoint(x + other.x, y + other.y)
    operator fun times(factor: Float): HanziPoint = HanziPoint(x * factor, y * factor)
    operator fun div(factor: Float): HanziPoint = HanziPoint(x / factor, y / factor)

    fun magnitude(): Float = sqrt(x * x + y * y)
    fun distance(other: HanziPoint): Float = (this - other).magnitude()
}

data class HanziStroke(
    val strokeNum: Int,
    val svgPath: String,
    val points: List<HanziPoint>
) {
    fun getStartingPoint(): HanziPoint = points.first()
    fun getEndingPoint(): HanziPoint = points.last()

    fun getLength(): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += points[i].distance(points[i - 1])
        }
        return total
    }

    fun getVectors(): List<HanziPoint> {
        val vectors = ArrayList<HanziPoint>(points.size - 1)
        for (i in 1 until points.size) {
            vectors.add(points[i] - points[i - 1])
        }
        return vectors
    }

    fun getDistance(point: HanziPoint): Float {
        var minDist = Float.MAX_VALUE
        for (p in points) {
            val d = p.distance(point)
            if (d < minDist) minDist = d
        }
        return minDist
    }

    fun getAverageDistance(userPoints: List<HanziPoint>): Float {
        if (userPoints.isEmpty()) return Float.MAX_VALUE
        var totalDist = 0f
        for (up in userPoints) {
            totalDist += getDistance(up)
        }
        return totalDist / userPoints.size
    }
}

data class HanziCharacter(
    val hanzi: String,
    val strokes: List<HanziStroke>,
    val strokeCount: Int = strokes.size
)

data class StrokeMatchResult(
    val isMatch: Boolean,
    val isStrokeBackwards: Boolean = false,
    val avgDist: Float = Float.MAX_VALUE
)

enum class WritingScaffoldingLevel(val label: String) {
    TEST("Test (Memory)"),
    GUIDED_FULL("Level 1 (Full Trace)"),
    GUIDED_DIRECTION("Level 2 (Trajectory Guide)"),
    GUIDED_FIRST_STROKE("Level 3 (First Stroke Cue)")
}

