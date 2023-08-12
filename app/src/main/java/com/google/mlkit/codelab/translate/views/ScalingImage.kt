package com.google.mlkit.codelab.translate.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.core.graphics.scaleMatrix
import androidx.core.graphics.toRectF
import androidx.core.graphics.translationMatrix
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.mlkit.codelab.translate.model.Page
import com.google.mlkit.codelab.translate.model.Word


class ScalingImage : androidx.appcompat.widget.AppCompatImageView {
  companion object {
    const val TAG = "ScalingImage"
  }

  private var page: Page? = null
  private val _selectedWord: MutableLiveData<Word?> = MutableLiveData()
  val selectedWord: LiveData<Word?> get() = _selectedWord

  class ScalePanListener internal constructor(private var imageView: ScalingImage) :
    ScaleGestureDetector.OnScaleGestureListener, GestureDetector.SimpleOnGestureListener() {
    private var scaleFactor = 1.0f
    private var distanceX = 0f
    private var distanceY = 0f

    override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
      scaleFactor *= scaleGestureDetector.scaleFactor
      Log.i(TAG, "Scale factor: ${scaleGestureDetector.scaleFactor}, scaleFactor ${scaleFactor}")
      scaleFactor = scaleFactor.coerceIn(0.1f, 10.0f)
      imageView.imageMatrix = getTransformMatrix()
      return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
      distanceY -= dy
      distanceX -= dx

      distanceX = distanceX.coerceAtMost(0f)
      distanceY = distanceY.coerceAtMost(0f)

      Log.i(TAG, "onScroll: dx=$dx, dy=$dy, distanceX=$distanceX, distanceY=$distanceY")

      imageView.imageMatrix = getTransformMatrix()
      return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
      val point = floatArrayOf(e.x, e.y)
      val invertedMatring = getInvertedTransformMatrix()
      invertedMatring.mapPoints(point)
      imageView.onClicked(point[0], point[1])
      return true
    }

    private fun getTransformMatrix(): Matrix {
      return scaleMatrix(scaleFactor, scaleFactor).also { it.postTranslate(distanceX, distanceY) }
    }

    private fun getInvertedTransformMatrix(): Matrix {
      return translationMatrix(-distanceX, -distanceY).also { it.postScale(1f / scaleFactor, 1f / scaleFactor) }
    }
  }

  private fun onClicked(x: Float, y: Float) {
    val oldWord = selectedWord.value
    val newWord = page?.findWord(x, y)
    if (newWord !== oldWord) {
      _selectedWord.value = newWord
      invalidate()
    }
  }

  private val scalePanListener = ScalePanListener(this)
  private val scaleGestureDetector: ScaleGestureDetector = ScaleGestureDetector(context, scalePanListener)
  private val detector: GestureDetector = GestureDetector(context, scalePanListener)

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  init {
    scaleType = ScaleType.MATRIX
  }

  fun setPage(page: Page?) {
    this.page = page
    invalidate()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    scaleGestureDetector.onTouchEvent(event)
    detector.onTouchEvent(event)
    return true
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    Log.i(TAG, imageMatrix.toString())

    canvas.setMatrix(imageMatrix)
    drawText(canvas)
  }

  private fun drawText(canvas: Canvas) {
    val p = page ?: return

    val wordPaint = Paint()
    wordPaint.style = Paint.Style.STROKE
    wordPaint.color = Color.RED
    wordPaint.strokeWidth = 2f


    val selectedWordPaint = Paint()
    selectedWordPaint.style = Paint.Style.STROKE
    selectedWordPaint.color = Color.GREEN
    selectedWordPaint.strokeWidth = 2f

    val selectedSentencePaint = Paint()
    selectedSentencePaint.style = Paint.Style.STROKE
    selectedSentencePaint.color = Color.YELLOW
    selectedSentencePaint.strokeWidth = 2f

    val selected = selectedWord.value
    val selectedSentence = selected?.sentence

    p.sentences.forEach { s ->
      s.words.forEach { w ->
        val wordBoundingBox = w.boundingBox?.toRectF()
        if (wordBoundingBox != null) {
          canvas.drawRect(
            wordBoundingBox, when {
              w == selected -> selectedWordPaint
              w.sentence == selectedSentence -> selectedSentencePaint
              else -> wordPaint
            }
          )
        }
      }
    }
  }
}