package com.jaycefr.jayto.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class AppendingShuffleOrderTest {

    private fun ShuffleOrder.playOrder(): List<Int> {
        val order = mutableListOf<Int>()
        var index = firstIndex
        while (index != C.INDEX_UNSET) {
            order += index
            index = getNextIndex(index)
        }
        return order
    }

    @Test
    fun initialOrderIsPermutation() {
        val order = AppendingShuffleOrder(0, Random(1)).cloneAndInsert(0, 16)
        assertEquals((0 until 16).toList(), order.playOrder().sorted())
        assertEquals(16, order.length)
    }

    @Test
    fun appendedItemsComeAfterAllExistingItems() {
        val initial = AppendingShuffleOrder(0, Random(2)).cloneAndInsert(0, 16)
        val before = initial.playOrder()
        val appended = initial.cloneAndInsert(16, 10)
        val after = appended.playOrder()

        assertEquals(before, after.take(16))
        assertEquals((16 until 26).toSet(), after.drop(16).toSet())
        // Walking forward from the old last item reaches every new item.
        assertEquals(10, after.size - after.indexOf(before.last()) - 1)
    }

    @Test
    fun insertInMiddleShiftsExistingIndices() {
        val order = AppendingShuffleOrder(0, Random(3)).cloneAndInsert(0, 4)
        val before = order.playOrder()
        val after = order.cloneAndInsert(2, 3).playOrder()
        assertEquals(before.map { if (it >= 2) it + 3 else it }, after.take(4))
        assertEquals(setOf(2, 3, 4), after.drop(4).toSet())
    }

    @Test
    fun removeKeepsRelativeOrderAndReindexes() {
        val order = AppendingShuffleOrder(0, Random(4)).cloneAndInsert(0, 8)
        val before = order.playOrder()
        val after = order.cloneAndRemove(2, 5).playOrder()
        val expected = before.filter { it < 2 || it >= 5 }.map { if (it >= 5) it - 3 else it }
        assertEquals(expected, after)
    }

    @Test
    fun clearAndPreviousIndex() {
        val order = AppendingShuffleOrder(0, Random(5)).cloneAndInsert(0, 5)
        val play = order.playOrder()
        assertEquals(C.INDEX_UNSET, order.getPreviousIndex(play.first()))
        assertEquals(play[2], order.getPreviousIndex(play[3]))
        assertEquals(play.last(), order.lastIndex)
        assertEquals(0, order.cloneAndClear().length)
    }
}
