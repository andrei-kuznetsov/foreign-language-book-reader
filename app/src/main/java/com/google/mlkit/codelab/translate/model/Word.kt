package com.google.mlkit.codelab.translate.model

import android.graphics.Rect

class Word(
  val sentence: Sentence,
  val boundingBox: Rect?,
  val word: String,
  val punctuation: String?
) {
  val text: String = word + (punctuation ?: "")
  override fun toString(): String = text

  companion object {
    fun fromText(sentence: Sentence, boundingBox: Rect?, text: String): Word {
      val (word, punctuation) = extractPunctuation(text)
      return Word(sentence, boundingBox, word, punctuation.ifBlank { null })
    }

    private fun extractPunctuation(text: String): Pair<String, String> {
      var punctEnd = text.length - 1
      for (i in (text.length - 1 downTo 0)) {
        if (text[i].isLetterOrDigit()) {
          break
        }
        punctEnd = i
      }
      val wordWitoutPunct = text.subSequence(0 until punctEnd).toString()
      val punct = text.substringAfter(wordWitoutPunct)
      return Pair(wordWitoutPunct, punct)
    }
  }
}
