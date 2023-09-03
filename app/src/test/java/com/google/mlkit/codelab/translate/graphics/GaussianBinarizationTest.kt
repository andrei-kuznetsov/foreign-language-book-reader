package com.google.mlkit.codelab.translate.graphics

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class GaussianBinarizationTest {
  @Test
  fun testKernel_11_2() {
    val inst = GaussianBinarization()
    val kernel = inst.gaussianKernel(11, 2.0)
    val expected = arrayOf(
      0.00881223, 0.02714358, 0.06511406, 0.12164907, 0.17699836,
      0.20056541,
      0.17699836, 0.12164907, 0.06511406, 0.02714358, 0.00881223
    )
    compareArrays(expected, kernel)
  }

  @Test
  fun testKernel_11_3() {
    val inst = GaussianBinarization()
    val kernel = inst.gaussianKernel(11, 3.0)
    val expected = arrayOf(
      0.03548293, 0.05850147, 0.08630959, 0.1139453, 0.13461047,
      0.14230046,
      0.13461047, 0.1139453, 0.08630959, 0.05850147, 0.03548293
    )
    compareArrays(expected, kernel)
  }

  @Test
  fun testArgbToGray() {
    val inst = GaussianBinarization()
    assertEquals(255 * 299 / 1000, inst.argbToGray(Color.RED))
    assertEquals(255 * 587 / 1000, inst.argbToGray(Color.GREEN))
    assertEquals(255 * 114 / 1000, inst.argbToGray(Color.BLUE))
    assertEquals(0, inst.argbToGray(Color.BLACK))
    assertEquals(255, inst.argbToGray(Color.WHITE))
  }

  @Test
  fun testConvertToBinaryInPlace() {
    val inst = GaussianBinarization()
    val g000 = inst.grayToArgb(0)
    val g064 = inst.grayToArgb(64)
    val g128 = inst.grayToArgb(128)
    val g192 = inst.grayToArgb(192)
    val g255 = inst.grayToArgb(255)
    val src =
      IntArray(10) { g000 } +
          IntArray(10) { g000 } +
          IntArray(10) { g000 } +
          IntArray(10) { g000 } +
          IntArray(10) { g255 } +
          IntArray(10) { g000 } +
          IntArray(10) { g000 } +
          IntArray(10) { g000 } +
          IntArray(10) { g000 } +
          IntArray(10) { g255 }

    assertEquals(100, src.size) // sanity check

    val res = src.clone()

    // original image is already binary.
    inst.makeBinaryInPlace(res, 10, 10, 5, 1)
    assertEquals(src.toList(), res.toList())

    // try kernel larger than image
//    inst.makeBinaryInPlace(res, 10, res.size, 11, 1)
//    assertEquals(src.toList(), res.toList())
  }

  private fun compareArrays(expected: Array<Double>, actual: Array<Double>, threshold: Double = 0.001) {
    assertEquals(expected.size, actual.size)
    for (i in expected.indices) {
      assertEquals("in position $i", expected[i], actual[i], threshold)
    }
  }
}