package com.chen.myviewer

import com.chen.myviewer.reader.comic.NaturalOrder
import org.junit.Assert.*
import org.junit.Test

class NaturalOrderTest {
    @Test fun sortsPagesAndNestedVolumesNumerically() {
        val result = listOf("卷10/1.jpg", "卷2/10.jpg", "卷2/2.jpg", "卷2/1.jpg").sortedWith(NaturalOrder)
        assertEquals(listOf("卷2/1.jpg", "卷2/2.jpg", "卷2/10.jpg", "卷10/1.jpg"), result)
    }
    @Test fun supportsNumbersLargerThanLongAndStableLeadingZeros() {
        val pages = listOf("999999999999999999999.png", "2.png", "02.png", "1000000000000000000000.png")
        assertEquals(listOf("02.png", "2.png", "999999999999999999999.png", "1000000000000000000000.png"), pages.sortedWith(NaturalOrder))
    }
    @Test fun comparatorIsAntisymmetric() {
        val entries = listOf("1.png", "01.png", "A2.png", "a02.png", "a10.png", "中文/2.jpg")
        entries.forEach { a -> entries.forEach { b -> assertEquals(-NaturalOrder.compare(b,a).sign, NaturalOrder.compare(a,b).sign) } }
    }
    private val Int.sign get() = compareTo(0)
}

