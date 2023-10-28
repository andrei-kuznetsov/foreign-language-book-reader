package com.google.mlkit.codelab.translate.model

data class Page(val sentences: List<Sentence>) {
  fun findWord(x: Float, y: Float): Word? {
    var foundWord: Word? = null
    val xint = x.toInt()
    val yint = y.toInt()
    for (s in sentences) {
      foundWord = s.words.find { word -> word.boundingBoxes.any { it.contains(xint, yint) } }
      if (foundWord != null) break
    }
    return foundWord
  }

  val text: String get() = sentences.joinToString(separator = " ")
}