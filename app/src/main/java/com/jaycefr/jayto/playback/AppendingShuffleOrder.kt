package com.jaycefr.jayto.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * Shuffle order that puts newly inserted items at the END of the play order (shuffled among
 * themselves), instead of scattering them across the whole order like [ShuffleOrder.DefaultShuffleOrder].
 *
 * The default behaviour breaks windowed playback: batches appended by the prefetcher partly land
 * before the current item in play order, so they're never reached and the queue runs dry even
 * though more songs were loaded.
 *
 * @param shuffled play order, as a permutation of media item (list) indices.
 */
@UnstableApi
class AppendingShuffleOrder private constructor(
    private val shuffled: IntArray,
    private val random: Random
) : ShuffleOrder {

    constructor(length: Int = 0, random: Random = Random.Default) :
        this(IntArray(length) { it }.also { it.shuffle(random) }, random)

    private val positionOf = IntArray(shuffled.size).also { pos ->
        shuffled.forEachIndexed { position, index -> pos[index] = position }
    }

    override fun getLength(): Int = shuffled.size

    override fun getNextIndex(index: Int): Int {
        val position = positionOf[index] + 1
        return if (position < shuffled.size) shuffled[position] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val position = positionOf[index] - 1
        return if (position >= 0) shuffled[position] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = shuffled.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = shuffled.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        // Existing list indices at/after the insertion point move down by insertionCount.
        val existing = IntArray(shuffled.size) { i ->
            val index = shuffled[i]
            if (index >= insertionIndex) index + insertionCount else index
        }
        val inserted = IntArray(insertionCount) { insertionIndex + it }.also { it.shuffle(random) }
        return AppendingShuffleOrder(existing + inserted, random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removedCount = indexToExclusive - indexFrom
        val remaining = shuffled
            .filter { it < indexFrom || it >= indexToExclusive }
            .map { if (it >= indexToExclusive) it - removedCount else it }
            .toIntArray()
        return AppendingShuffleOrder(remaining, random)
    }

    override fun cloneAndClear(): ShuffleOrder = AppendingShuffleOrder(IntArray(0), random)
}
