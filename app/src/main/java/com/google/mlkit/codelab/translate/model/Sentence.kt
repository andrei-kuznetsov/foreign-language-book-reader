package com.google.mlkit.codelab.translate.model

import android.graphics.Rect

class Sentence {
  val text: String get() = words.joinToString(separator = " ")

  private val _words: MutableList<Word> = mutableListOf()
  val words: List<Word> get() = _words

  fun addWord(boundingBox: Rect?, text: String) {
    _words.add(Word(this, boundingBox, text))
  }

  override fun toString(): String = text
}