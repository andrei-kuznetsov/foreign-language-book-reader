/*
 * Copyright 2019 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.google.mlkit.codelab.translate.analyzer

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.tasks.Task
import com.google.mlkit.codelab.translate.model.Page
import com.google.mlkit.codelab.translate.model.Sentence
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Analyzes the frames passed in from the camera and returns any detected text within the requested
 * crop region.
 */
class TextAnalyzer(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val result: MutableLiveData<Page>,
) {
    private val endOfSentence = setOf('.', '!', '?', '…')
    private val detector = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    init {
        lifecycle.addObserver(detector)
    }

    fun recognizeText(
        image: InputImage
    ): Task<Text> {
        // Pass image to an ML Kit Vision API
        return detector.process(image)
            .addOnSuccessListener { text ->
                // Task completed successfully
                result.value = prepareRecognizedText(text)
            }
            .addOnFailureListener { exception ->
                // Task failed with an exception
                Log.e(TAG, "Text recognition error", exception)
                val message = getErrorMessage(exception)
                message?.let {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
    }

    private data class TextElement(
        val element: Text.Element,
        val line: Text.Line,
        val posInLine: Int,
        val maxPosInLine: Int,
    ) {
        fun endsWithHyphenation(): Boolean {
            return textWithoutHyphenation() != element.text
        }

        fun textWithoutHyphenation(): String {
            return if (element.text.endsWith("-")
                || (posInLine == maxPosInLine && line.text.endsWith("-"))
            ) {
                element.text.dropLast(1).trim()
            } else {
                element.text.trim()
            }
        }

        fun getBoundingBox() = element.boundingBox
    }

    private fun prepareRecognizedText(text: Text?): Page? {
        if (text == null) return null

        val elementList: List<TextElement> = text.textBlocks
            .flatMap(Text.TextBlock::getLines)
            .map { ln ->
                val maxPosInLine = ln.elements.size - 1
                ln.elements.mapIndexed { idx, el ->
                    TextElement(el, ln, idx, maxPosInLine)
                }
            }
            .flatten()

        val sentences = mutableListOf<Sentence>()
        var currentSentence: Sentence? = null
        val currentWordParts: MutableList<TextElement> = mutableListOf()
        for (w in elementList) {
            currentSentence = currentSentence ?: Sentence()
            currentWordParts.add(w)
            if (w.endsWithHyphenation()) {
                continue
            }

            val wordText = currentWordParts
                .map(TextElement::textWithoutHyphenation)
                .reduce(String::plus)
            val boxes = currentWordParts
                .mapNotNull(TextElement::getBoundingBox)

            currentWordParts.clear()
            val addedWord = currentSentence.addWord(boxes, wordText)
            if (addedWord.punctuation?.any { endOfSentence.contains(it) } == true) {
                sentences.add(currentSentence)
                currentSentence = null
            }
        }

        if (currentSentence != null) sentences.add(currentSentence)
        return Page(sentences)
    }

    private fun getErrorMessage(exception: Exception): String? {
        val mlKitException = exception as? MlKitException ?: return exception.message
        return if (mlKitException.errorCode == MlKitException.UNAVAILABLE) {
            "Waiting for text recognition model to be downloaded"
        } else exception.message
    }

    companion object {
        private const val TAG = "TextAnalyzer"
    }
}