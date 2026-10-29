package com.matthieu.zi_app.domain.writing

import kotlin.math.PI

/**
 * Validates user-drawn strokes against expected Hanzi character strokes.
 * Direct Kotlin port of Hanzi Writer's matching engine (MIT License, Copyright 2017 David Chanin).
 */
object StrokeMatcher {

    const val COSINE_SIMILARITY_THRESHOLD = 0f
    const val START_AND_END_DIST_THRESHOLD = 250f
    const val FRECHET_THRESHOLD = 0.45f
    const val MIN_LEN_THRESHOLD = 0.35f
    const val AVERAGE_DISTANCE_THRESHOLD = 350f

    private val SHAPE_FIT_ROTATIONS = doubleArrayOf(
        PI / 16.0,
        PI / 32.0,
        0.0,
        -PI / 32.0,
        -PI / 16.0
    )

    fun strokeMatches(
        userPointsRaw: List<HanziPoint>,
        character: HanziCharacter,
        strokeNum: Int,
        leniency: Float = 1.0f,
        isOutlineVisible: Boolean = false
    ): StrokeMatchResult {
        val strokes = character.strokes
        if (strokeNum !in strokes.indices) {
            return StrokeMatchResult(isMatch = false)
        }

        val points = stripDuplicates(userPointsRaw)
        if (points.size < 2) {
            return StrokeMatchResult(isMatch = false)
        }

        val targetStroke = strokes[strokeNum]
        val matchData = getMatchData(
            points = points,
            stroke = targetStroke,
            leniency = leniency,
            isOutlineVisible = isOutlineVisible,
            checkBackwards = true
        )

        if (!matchData.isMatch) {
            return matchData
        }

        // If there is a significantly better match among subsequent strokes, the user likely drew out of order.
        var closestMatchDist = matchData.avgDist
        for (i in (strokeNum + 1) until strokes.size) {
            val laterMatch = getMatchData(
                points = points,
                stroke = strokes[i],
                leniency = leniency,
                isOutlineVisible = isOutlineVisible,
                checkBackwards = false
            )
            if (laterMatch.isMatch && laterMatch.avgDist < closestMatchDist) {
                closestMatchDist = laterMatch.avgDist
            }
        }

        if (closestMatchDist < matchData.avgDist) {
            val leniencyAdjustment = (0.6f * (closestMatchDist + matchData.avgDist)) / (2f * matchData.avgDist)
            return getMatchData(
                points = points,
                stroke = targetStroke,
                leniency = leniency * leniencyAdjustment,
                isOutlineVisible = isOutlineVisible,
                checkBackwards = false
            )
        }

        return matchData
    }

    private fun getMatchData(
        points: List<HanziPoint>,
        stroke: HanziStroke,
        leniency: Float,
        isOutlineVisible: Boolean,
        checkBackwards: Boolean
    ): StrokeMatchResult {
        val avgDist = stroke.getAverageDistance(points)
        val distMod = if (isOutlineVisible || stroke.strokeNum > 0) 0.5f else 1.0f
        val withinDistThresh = avgDist <= AVERAGE_DISTANCE_THRESHOLD * distMod * leniency
        if (!withinDistThresh) {
            return StrokeMatchResult(isMatch = false, avgDist = avgDist)
        }

        val startAndEndMatch = startAndEndMatches(points, stroke, leniency)
        val directionMatch = directionMatches(points, stroke)
        val shapeMatch = shapeFit(points, stroke.points, leniency)
        val lengthMatch = lengthMatches(points, stroke, leniency)

        val isMatch = withinDistThresh && startAndEndMatch && directionMatch && shapeMatch && lengthMatch
        if (isMatch) {
            return StrokeMatchResult(isMatch = true, avgDist = avgDist)
        }

        if (checkBackwards) {
            val reversedPoints = points.reversed()
            val backwardsData = getMatchData(
                points = reversedPoints,
                stroke = stroke,
                leniency = leniency,
                isOutlineVisible = isOutlineVisible,
                checkBackwards = false
            )
            if (backwardsData.isMatch) {
                return StrokeMatchResult(isMatch = false, isStrokeBackwards = true, avgDist = backwardsData.avgDist)
            }
        }

        return StrokeMatchResult(isMatch = false, avgDist = avgDist)
    }

    private fun startAndEndMatches(points: List<HanziPoint>, stroke: HanziStroke, leniency: Float): Boolean {
        val startingDist = stroke.getStartingPoint().distance(points.first())
        val endingDist = stroke.getEndingPoint().distance(points.last())
        val threshold = START_AND_END_DIST_THRESHOLD * leniency
        return startingDist <= threshold && endingDist <= threshold
    }

    private fun directionMatches(points: List<HanziPoint>, stroke: HanziStroke): Boolean {
        val edgeVectors = getEdgeVectors(points)
        val strokeVectors = stroke.getVectors()
        if (edgeVectors.isEmpty() || strokeVectors.isEmpty()) return false

        var totalSimilarity = 0f
        for (edgeVector in edgeVectors) {
            var maxSim = -1f
            for (strokeVector in strokeVectors) {
                val sim = HanziGeometry.cosineSimilarity(strokeVector, edgeVector)
                if (sim > maxSim) maxSim = sim
            }
            totalSimilarity += maxSim
        }
        val avgSimilarity = totalSimilarity / edgeVectors.size
        return avgSimilarity > COSINE_SIMILARITY_THRESHOLD
    }

    private fun lengthMatches(points: List<HanziPoint>, stroke: HanziStroke, leniency: Float): Boolean {
        val userLen = HanziGeometry.length(points)
        val strokeLen = stroke.getLength()
        return (leniency * (userLen + 25f)) / (strokeLen + 25f) >= MIN_LEN_THRESHOLD
    }

    private fun shapeFit(curve1: List<HanziPoint>, curve2: List<HanziPoint>, leniency: Float): Boolean {
        val normCurve1 = HanziGeometry.normalizeCurve(curve1)
        val normCurve2 = HanziGeometry.normalizeCurve(curve2)
        var minDist = Float.MAX_VALUE
        for (theta in SHAPE_FIT_ROTATIONS) {
            val rotated = HanziGeometry.rotate(normCurve2, theta)
            val dist = HanziGeometry.frechetDist(normCurve1, rotated)
            if (dist < minDist) {
                minDist = dist
            }
        }
        return minDist <= FRECHET_THRESHOLD * leniency
    }

    private fun getEdgeVectors(points: List<HanziPoint>): List<HanziPoint> {
        val vectors = ArrayList<HanziPoint>(points.size - 1)
        for (i in 1 until points.size) {
            vectors.add(points[i] - points[i - 1])
        }
        return vectors
    }

    private fun stripDuplicates(points: List<HanziPoint>): List<HanziPoint> {
        if (points.size < 2) return points
        val deduped = ArrayList<HanziPoint>(points.size)
        deduped.add(points.first())
        for (i in 1 until points.size) {
            val p = points[i]
            val last = deduped.last()
            if (p.x != last.x || p.y != last.y) {
                deduped.add(p)
            }
        }
        return deduped
    }
}

