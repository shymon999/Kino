package com.nuvio.tv.ui.screens.player

/** A slow, hysteretic controller for single-quality server streams. All times are monotonic. */
internal class ServerBitratePolicy(private val responsive: Boolean = false) {
    private val levels = listOf(40, 35, 30, 25, 20, 15, 10).map { it * 1_000_000L }
    private var target: String? = null
    private var startedAt = 0L
    private var lastChangeAt = 0L
    private var lowSince: Long? = null
    private var stableSince: Long? = null
    private var lastPosition: Long? = null
    private var failedUpgradeCeiling: Long? = null
    private var blockedUpgradeUntil = 0L
    private var previousChangeWasUpgrade = false
    private var bufferingSince: Long? = null
    private var lastSampleAt: Long? = null
    private var lastBandwidthSampleAt = Long.MIN_VALUE
    private var goodBandwidthSamples = 0
    private var lastHeadroomSampleAt: Long? = null
    private var lastInsufficientUpgradeAt: Long? = null
    private var retryAfter = 0L

    fun reset() { target = null }

    fun sample(
        key: String, now: Long, positionMs: Long, bufferedMs: Long, buffering: Boolean,
        paused: Boolean, bitrate: Long, requiredBitrate: Long?, bandwidth: Long?, slowSegment: Boolean,
        bandwidthSampleAt: Long = now
    ): Long? {
        if (target != key) {
            target = key; startedAt = now; lastChangeAt = now
            lowSince = null; stableSince = null; lastPosition = null; failedUpgradeCeiling = null
            blockedUpgradeUntil = 0; previousChangeWasUpgrade = false
            bufferingSince = null; lastSampleAt = null; lastBandwidthSampleAt = Long.MIN_VALUE
            goodBandwidthSamples = 0; lastHeadroomSampleAt = null; lastInsufficientUpgradeAt = null; retryAfter = 0
        }
        val previous = lastPosition
        val elapsed = lastSampleAt?.let { (now - it).coerceAtLeast(0) } ?: 0L
        lastSampleAt = now
        lastPosition = positionMs
        val seek = previous != null && if (responsive) {
            kotlin.math.abs(positionMs - previous) > elapsed + 5_000
        } else kotlin.math.abs(positionMs - previous) > 5_000
        if (paused || seek) {
            lowSince = null; stableSince = null; startedAt = now
            bufferingSince = null; goodBandwidthSamples = 0; lastInsufficientUpgradeAt = null
            return null
        }
        if (responsive) return responsiveSample(now, bufferedMs, buffering, bitrate, requiredBitrate, bandwidth,
            slowSegment, bandwidthSampleAt)
        if (now - startedAt < 30_000 || now - lastChangeAt < 60_000) return null
        val insufficient = bandwidth != null && requiredBitrate != null && bandwidth < requiredBitrate * 1.15
        val struggling = (buffering || bufferedMs < 4_000) && (insufficient || slowSegment)
        if (struggling) {
            stableSince = null
            if (lowSince == null) lowSince = now
            if (now - lowSince!! >= 8_000) {
                lowSince = null
                return levels.firstOrNull { it < minOf(bitrate, requiredBitrate ?: bitrate) }
            }
        } else {
            lowSince = null
            val next = levels.lastOrNull { it > bitrate }
            val safe = !buffering && bufferedMs >= 25_000 && next != null &&
                bandwidth != null && bandwidth >= next * 1.4 && (next != failedUpgradeCeiling || now >= blockedUpgradeUntil)
            if (!safe) stableSince = null else {
                if (stableSince == null) stableSince = now
                if (now - stableSince!! >= 180_000) { stableSince = null; return next }
            }
        }
        return null
    }

    private fun responsiveSample(
        now: Long, buffer: Long, buffering: Boolean, bitrate: Long, required: Long?,
        bandwidth: Long?, slowSegment: Boolean, bandwidthSampleAt: Long
    ): Long? {
        if (now < retryAfter) return null
        val insufficient = bandwidth != null && required != null && bandwidth < required * 1.1
        val pressure = (buffering || buffer < 10_000) && (insufficient || slowSegment)
        if (buffering && pressure) {
            if (bufferingSince == null) bufferingSince = now
        } else bufferingSince = null
        if (pressure) {
            stableSince = null; goodBandwidthSamples = 0
            if (lowSince == null) lowSince = now
            // Preparation after a seek/restart is not evidence of congestion.
            val cooldown = if (previousChangeWasUpgrade) 10_000 else 20_000
            val initial = lastChangeAt == startedAt
            val ready = now - startedAt >= 8_000 && (initial || now - lastChangeAt >= cooldown)
            val urgent = bufferingSince?.let { now - it >= 2_000 } == true
            if (ready && (urgent || now - lowSince!! >= 4_000)) {
                val lower = levels.filter { it < minOf(bitrate, required ?: bitrate) }
                // A confirmed stall can skip one rung to avoid another immediate restart.
                return if (urgent && bandwidth != null) {
                    lower.take(2).firstOrNull { it <= bandwidth * 0.9 } ?: lower.take(2).lastOrNull()
                } else lower.firstOrNull()
            }
            return null
        }
        lowSince = null
        val next = levels.lastOrNull { it > bitrate } ?: return null
        if (buffering || buffer < 12_000 || slowSegment) {
            stableSince = null; goodBandwidthSamples = 0
            return null
        }
        if (buffer >= 20_000 && stableSince == null) stableSince = now
        if (bandwidth != null && bandwidthSampleAt != lastBandwidthSampleAt && now - bandwidthSampleAt in 0L..20_000L) {
            lastBandwidthSampleAt = bandwidthSampleAt
            if (bandwidth >= next * 1.3) {
                if (lastHeadroomSampleAt?.let { bandwidthSampleAt - it > 30_000 } == true) goodBandwidthSamples = 0
                goodBandwidthSamples = (goodBandwidthSamples + 1).coerceAtMost(3)
                lastHeadroomSampleAt = bandwidthSampleAt
            } else {
                goodBandwidthSamples = 0
                lastInsufficientUpgradeAt = now
            }
        }
        // Idle downloads must not erase a healthy-buffer stability window. A fresh, positive
        // transfer observation is still mandatory before requesting the higher representation.
        val recentHeadroom = bandwidth != null && bandwidth >= next * 1.3 &&
            now - bandwidthSampleAt in 0L..20_000L && goodBandwidthSamples >= 3
        val quiet = lastInsufficientUpgradeAt?.let { now - it >= 30_000 } ?: true
        if (buffer >= 20_000 && stableSince?.let { now - it >= 180_000 } == true && recentHeadroom && quiet &&
            now - lastChangeAt >= 90_000 && (next != failedUpgradeCeiling || now >= blockedUpgradeUntil)) return next
        return null
    }

    fun diagnostics(now: Long): String = "stableSeconds=${stableSince?.let { (now - it) / 1_000 } ?: 0} " +
        "headroomSamples=$goodBandwidthSamples upgradeBlockedSeconds=${((blockedUpgradeUntil - now) / 1_000).coerceAtLeast(0)}"

    fun changed(now: Long, previous: Long, next: Long) {
        // A quick reversal after an upgrade blocks repeating that upgrade for this item.
        if (next < previous && previousChangeWasUpgrade && now - lastChangeAt < 240_000) {
            failedUpgradeCeiling = previous
            blockedUpgradeUntil = now + 600_000
        }
        previousChangeWasUpgrade = next > previous
        lastChangeAt = now; lowSince = null; stableSince = null
        bufferingSince = null; goodBandwidthSamples = 0; lastInsufficientUpgradeAt = null
        lastBandwidthSampleAt = Long.MIN_VALUE
    }

    fun failed(now: Long) {
        lastChangeAt = now; lowSince = null; stableSince = null
        bufferingSince = null; goodBandwidthSamples = 0; retryAfter = now + 60_000
    }
}
