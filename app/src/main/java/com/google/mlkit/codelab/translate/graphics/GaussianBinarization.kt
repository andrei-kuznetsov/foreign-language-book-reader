package com.google.mlkit.codelab.translate.graphics

import android.graphics.Bitmap
import kotlin.math.exp

/**
 * yes, this the same as CV2's
 * cv.adaptiveThreshold(img,255,cv.ADAPTIVE_THRESH_GAUSSIAN_C, cv.THRESH_BINARY,sz,s)
 *
 * But who wants to add +20M binaries per cpu arch just for this?
 */
class GaussianBinarization {
  fun gaussianKernel(size: Int, sigma: Double = (size - 1) / 6.0): Array<Double> {
    assert(sigma > 0) { "Sigma must be positive" }
    assert(size >= 3) { "Size must be at least 3" }
    assert(size % 2 == 1) { "Size must be odd" }
//    val size: Int = 2 * ceil(3 * sigma).toInt() + 1

    val twoSigma2 = 2.0 * sigma * sigma

    val nonNormalized = Array(size) { idx ->
      val x = idx - size / 2
      exp(-x * x / twoSigma2)
    }

    // now normalize
    val normalized = nonNormalized
    val c = (1.0 / nonNormalized.sum())
    for (i in normalized.indices) {
      normalized[i] *= c
    }

    return normalized
  }

  fun argbToGray(argb: Int): Int {
    // brightness
    val b = argb and 0x0ff
    val g = argb.shr(8) and 0x0ff
    val r = argb.shr(16) and 0x0ff
    return (299 * r + 587 * g + 114 * b) / 1000
  }

  fun grayToArgb(gray: Int): Int {
    assert(gray in 0..255) { "Invalid gray value: $gray" }
    return 255.shl(24) or gray.shl(16) or gray.shl(8) or gray
  }

  fun getBinaryImage(src: Bitmap, kernelSize: Int, c: Int): Bitmap {
    val width = src.width
    val height = src.height

    val pixels = IntArray(height * width)
    src.getPixels(pixels, 0, width, 0, 0, width, height)

    makeBinaryInPlace(pixels, width, height, kernelSize, c)

    return Bitmap.createBitmap(pixels, 0, width, width, height, Bitmap.Config.ARGB_8888)
  }

  fun makeBinaryInPlace(argbPixels :IntArray, width: Int, height: Int, kernelSize: Int, c: Int) {
    argbToGray(argbPixels)
    val thresholds = argbPixels.clone()
    applyGaussianKernelInPlace(thresholds, width, height, gaussianKernel(kernelSize))

    applyThreshold(argbPixels) {
      thresholds[it] - c
    }

    grayToArgb(argbPixels)
  }

  private fun argbToGray(argbPixels: IntArray) {
    for (i in argbPixels.indices) {
      argbPixels[i] = argbToGray(argbPixels[i])
    }
  }

  private fun grayToArgb(grayPixels: IntArray) {
    for (i in grayPixels.indices) {
      grayPixels[i] = grayToArgb(grayPixels[i])
    }
  }

  private fun applyThreshold(grayPixels: IntArray, threshold: (Int) -> Int) {
    for (i in grayPixels.indices) {
      grayPixels[i] = if (grayPixels[i] < threshold(i)) grayPixels[i] else (grayPixels[i] * 2).toInt().coerceAtMost(255)
    }
  }

  fun applyGaussianKernelInPlace(grayPixels: IntArray, width: Int, height: Int, kernel: Array<Double>) {
    val kernelSize = kernel.size
    val halfKernelSize = kernelSize / 2


    val pxb = IntArray(kernelSize)
    var rowOff = 0

    for (h in 0 until height) {
      apply1DFilter(halfKernelSize, pxb, grayPixels, rowOff, width, 1, kernel)
      rowOff += width
    }

    var colOff = 0
    for (w in 0 until width) {
      apply1DFilter(halfKernelSize, pxb, grayPixels, colOff, height, width, kernel)
      colOff += 1
    }
  }

  private fun apply1DFilter(
    outOfScreen: Int,
    pxb: IntArray,
    grayPixels: IntArray,
    pixelsOff: Int,
    pixelsCount: Int,
    pixelsStep: Int,
    kernel: Array<Double>
  ) {
    var pxbi = 0
    var pxr = pixelsOff
    var pxw = pixelsOff
    for (n in -outOfScreen until 0) {
      pxb[pxbi++] = 128
    }

    for (n in 0 until outOfScreen) {
      pxb[pxbi++] = grayPixels[pxr]
      pxr += pixelsStep
    }

    for (n in outOfScreen until pixelsCount) {
      pxb[pxbi] = grayPixels[pxr]
      grayPixels[pxw] = mult(pxb, pxbi, kernel)
      pxr += pixelsStep
      pxw += pixelsStep
      pxbi = if (pxbi == pxb.size - 1) 0 else pxbi + 1
    }

    for (n in pixelsCount until pixelsCount + outOfScreen) {
      pxb[pxbi] = 128
      grayPixels[pxw] = mult(pxb, pxbi, kernel)
      pxw += pixelsStep
      pxbi = if (pxbi == pxb.size - 1) 0 else pxbi + 1
    }
  }

  private fun mult(pxb: IntArray, pxbi: Int, kernel: Array<Double>): Int {
    val maxValue = 255
    var off = pxbi
    var accum = 0.0
    for (i in kernel.indices) {
      accum += pxb[off] * kernel[i]
      off++
      if (off == pxb.size) off = 0
    }
    return accum.toInt().coerceIn(0, maxValue)
  }
}