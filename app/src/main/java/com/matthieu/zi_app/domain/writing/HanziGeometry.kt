package com.matthieu.zi_app.domain.writing

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometric operations for Hanzi stroke curve normalization and matching.
 * Ported from and inspired by Hanzi Writer (MIT License, Copyright 2017 David Chanin).
 */
object HanziGeometry {

    fun cosineSimilarity(point1: HanziPoint, point2: HanziPoint): Float {
        val mag1 = point1.magnitude()
        val mag2 = point2.magnitude()
        if (mag1 == 0f || mag2 == 0f) return 0f
        val rawDotProduct = point1.x * point2.x + point1.y * point2.y
        return rawDotProduct / (mag1 * mag2)
    }

    fun extendPointOnLine(p1: HanziPoint, p2: HanziPoint, dist: Float): HanziPoint {
        val vect = p2 - p1
        val mag = vect.magnitude()
        if (mag == 0f) return p2
        val norm = dist / mag
        return HanziPoint(p2.x + norm * vect.x, p2.y + norm * vect.y)
    }

    fun length(points: List<HanziPoint>): Float {
        if (points.size < 2) return 0f
        var total = 0f
        for (i in 1 until points.size) {
            total += points[i].distance(points[i - 1])
        }
        return total
    }

    /**
     * Discrete Fréchet distance algorithm between two polygonal curves.
     * Based on Eiter & Mannila (1994).
     */
    fun frechetDist(curve1: List<HanziPoint>, curve2: List<HanziPoint>): Float {
        if (curve1.isEmpty() || curve2.isEmpty()) return Float.MAX_VALUE
        val longCurve = if (curve1.size >= curve2.size) curve1 else curve2
        val shortCurve = if (curve1.size >= curve2.size) curve2 else curve1

        var prevResultsCol = FloatArray(shortCurve.size)
        for (i in longCurve.indices) {
            val curResultsCol = FloatArray(shortCurve.size)
            for (j in shortCurve.indices) {
                val d = longCurve[i].distance(shortCurve[j])
                curResultsCol[j] = when {
                    i == 0 && j == 0 -> d
                    i > 0 && j == 0 -> maxOf(prevResultsCol[0], d)
                    i == 0 && j > 0 -> maxOf(curResultsCol[j - 1], d)
                    else -> maxOf(
                        minOf(prevResultsCol[j], prevResultsCol[j - 1], curResultsCol[j - 1]),
                        d
                    )
                }
            }
            prevResultsCol = curResultsCol
        }
        return prevResultsCol[shortCurve.size - 1]
    }

    fun subdivideCurve(curve: List<HanziPoint>, maxLen: Float = 0.05f): List<HanziPoint> {
        if (curve.size < 2) return curve
        val newCurve = ArrayList<HanziPoint>()
        newCurve.add(curve[0])

        for (i in 1 until curve.size) {
            val point = curve[i]
            val prevPoint = newCurve.last()
            val segLen = point.distance(prevPoint)
            if (segLen > maxLen) {
                val numNewPoints = ceil(segLen / maxLen).toInt()
                val newSegLen = segLen / numNewPoints
                for (j in 0 until numNewPoints) {
                    newCurve.add(extendPointOnLine(point, prevPoint, -1f * newSegLen * (j + 1)))
                }
            } else {
                newCurve.add(point)
            }
        }
        return newCurve
    }

    fun outlineCurve(curve: List<HanziPoint>, numPoints: Int = 30): List<HanziPoint> {
        if (curve.size <= 1) return curve
        val curveLen = length(curve)
        if (curveLen <= 0f) return List(numPoints) { curve.first() }

        val segmentLen = curveLen / (numPoints - 1)
        val outlinePoints = ArrayList<HanziPoint>(numPoints)
        outlinePoints.add(curve.first())
        val remainingCurvePoints = ArrayDeque(curve.subList(1, curve.size))

        for (i in 0 until numPoints - 2) {
            var lastPoint = outlinePoints.last()
            var remainingDist = segmentLen
            var outlinePointFound = false
            while (!outlinePointFound && remainingCurvePoints.isNotEmpty()) {
                val nextPointDist = lastPoint.distance(remainingCurvePoints.first())
                if (nextPointDist < remainingDist) {
                    remainingDist -= nextPointDist
                    lastPoint = remainingCurvePoints.removeFirst()
                } else {
                    val nextPoint = extendPointOnLine(
                        lastPoint,
                        remainingCurvePoints.first(),
                        remainingDist - nextPointDist
                    )
                    outlinePoints.add(nextPoint)
                    outlinePointFound = true
                }
            }
        }
        outlinePoints.add(curve.last())
        return outlinePoints
    }

    /**
     * Translate and scale curve to normalized bounds using Procrustes analysis.
     */
    fun normalizeCurve(curve: List<HanziPoint>): List<HanziPoint> {
        val outlined = outlineCurve(curve, 30)
        var sumX = 0f
        var sumY = 0f
        for (p in outlined) {
            sumX += p.x
            sumY += p.y
        }
        val mean = HanziPoint(sumX / outlined.size, sumY / outlined.size)
        val translated = outlined.map { it - mean }
        val first = translated.first()
        val last = translated.last()
        val scale = sqrt(((first.x * first.x + first.y * first.y) + (last.x * last.x + last.y * last.y)) / 2f)
        val actualScale = if (scale > 0f) scale else 1f
        val scaled = translated.map { HanziPoint(it.x / actualScale, it.y / actualScale) }
        return subdivideCurve(scaled)
    }

    fun rotate(curve: List<HanziPoint>, theta: Double): List<HanziPoint> {
        val cosT = cos(theta).toFloat()
        val sinT = sin(theta).toFloat()
        return curve.map { p ->
            HanziPoint(cosT * p.x - sinT * p.y, sinT * p.x + cosT * p.y)
        }
    }
}

