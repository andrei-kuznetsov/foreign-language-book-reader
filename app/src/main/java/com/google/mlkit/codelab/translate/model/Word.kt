package com.google.mlkit.codelab.translate.model

import android.graphics.Rect

class Word(
  val sentence: Sentence,
  val boundingBox: Rect?,
  val text: String
) {
  override fun toString(): String = text
}
