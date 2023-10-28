package com.google.mlkit.codelab.translate.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.graphics.scaleMatrix
import androidx.core.graphics.translationMatrix
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.mlkit.codelab.translate.model.Page
import com.google.mlkit.codelab.translate.model.Sentence
import com.google.mlkit.codelab.translate.model.Word


class ScalingImage : View {
  companion object {
    const val TAG = "ScalingImage"
  }

  var bitmap: Bitmap? = null
    set(value) {
      field = value
      scalePanListener.unscaledImageWidth = bitmap?.width?.toFloat() ?: 0.0f
      scalePanListener.unscaledImageHeight = bitmap?.height?.toFloat() ?: 0.0f
      adjustScale(width.toFloat(), height.toFloat())
      scalePanListener.scaleFactor = scalePanListener.minScale
    }

  private var imageMatrix: Matrix = scaleMatrix(1f, 1f)
    set(value) {
      field = value
      invalidate()
    }

  private var page: Page? = null
  private val _selectedWord: MutableLiveData<Word?> = MutableLiveData()
  private val _selectedSentence: MutableLiveData<Sentence?> = MutableLiveData()

  val selectedWord: LiveData<Word?> = _selectedWord
  val selectedSentence: LiveData<Sentence?> = _selectedSentence

  class ScalePanListener internal constructor(private var imageView: ScalingImage) :
    ScaleGestureDetector.OnScaleGestureListener, GestureDetector.SimpleOnGestureListener() {
    var minScale: Float = 1.0f
    val maxScale: Float = 10.0f
    var scaleFactor = 1.0f

    var minScaledImageWidth: Float = 0.0f
    var unscaledImageWidth: Float = 0.0f

    var minScaledImageHeight: Float = 0.0f
    var unscaledImageHeight: Float = 0.0f

    private var distanceX = 0f
    private var distanceY = 0f

    override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
      scaleFactor *= scaleGestureDetector.scaleFactor
      imageView.imageMatrix = getTransformMatrix()
      return true
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

    override fun onScaleEnd(detector: ScaleGestureDetector) {}

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
      distanceY -= dy
      distanceX -= dx
      imageView.imageMatrix = getTransformMatrix()
      return true
    }

    private fun fixBounds() {
      scaleFactor = scaleFactor.coerceIn(minScale, maxScale)

      val minDistanceX = (minScaledImageWidth - unscaledImageWidth * scaleFactor).coerceAtMost(0.0f)
      val minDistanceY = (minScaledImageHeight - unscaledImageHeight * scaleFactor).coerceAtMost(0.0f)

      distanceX = distanceX.coerceIn(minDistanceX, 0.0f)
      distanceY = distanceY.coerceIn(minDistanceY, 0.0f)
    }

    private fun screenToPictureCoords(e: MotionEvent): PointF {
      val point = floatArrayOf(e.x, e.y)
      val invertedMatrix = getInvertedTransformMatrix()
      invertedMatrix.mapPoints(point)
      return PointF(point[0], point[1])
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
      val point = screenToPictureCoords(e)
      imageView.onClicked(point.x, point.y)
      return true
    }

    override fun onLongPress(e: MotionEvent) {
      val point = screenToPictureCoords(e)
      imageView.onLongClicked(point.x, point.y)
    }

    fun getTransformMatrix(): Matrix {
      fixBounds()
      return scaleMatrix(scaleFactor, scaleFactor).also { it.postTranslate(distanceX, distanceY) }
    }

    private fun getInvertedTransformMatrix(): Matrix {
      fixBounds()
      return translationMatrix(-distanceX, -distanceY).also { it.postScale(1f / scaleFactor, 1f / scaleFactor) }
    }
  }

  private fun onClicked(x: Float, y: Float) {
    val oldWord = selectedWord.value
    val newWord = page?.findWord(x, y)
    if (newWord !== oldWord) {
      _selectedSentence.value = newWord?.sentence
      _selectedWord.value = newWord
      invalidate()
    }
  }

  private fun onLongClicked(x: Float, y: Float) {
    val oldSentence = _selectedSentence.value
    val newSentence = page?.findWord(x, y)?.sentence
    if (newSentence !== oldSentence) {
      _selectedSentence.value = newSentence
      invalidate()
    }
  }

  private val scalePanListener = ScalePanListener(this)
  private val scaleGestureDetector: ScaleGestureDetector = ScaleGestureDetector(context, scalePanListener)
  private val detector: GestureDetector = GestureDetector(context, scalePanListener)

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

  fun setPage(page: Page?) {
    this.page = page
    invalidate()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    adjustScale((right - left).toFloat(), (bottom - top).toFloat())
  }

  private fun adjustScale(viewportWidth: Float, viewportHeight: Float) {
    val notNullBitmap = bitmap ?: return

    val scaleX = viewportWidth / notNullBitmap.width

    scalePanListener.minScale = scaleX

    scalePanListener.unscaledImageWidth = notNullBitmap.width.toFloat()
    scalePanListener.minScaledImageWidth = viewportWidth

    scalePanListener.unscaledImageHeight = notNullBitmap.height.toFloat()
    scalePanListener.minScaledImageHeight = viewportHeight

    imageMatrix = scalePanListener.getTransformMatrix()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    scaleGestureDetector.onTouchEvent(event)
    detector.onTouchEvent(event)
    return true
  }

  override fun onDraw(canvas: Canvas) {
    val notNullBitmap = bitmap ?: return
    val saveCount = canvas.saveCount
    canvas.save()

    canvas.setMatrix(imageMatrix)
    canvas.drawBitmap(notNullBitmap, 0f, 0f, null)
    drawText(canvas)

    canvas.restoreToCount(saveCount)
  }

  private fun drawText(canvas: Canvas) {
    val p = page ?: return

    val wordPaint = Paint()
//    wordPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    wordPaint.style = Paint.Style.STROKE
    wordPaint.color = Color.argb(128, 255, 0, 0)
    wordPaint.strokeWidth = 2f


    val alpha = Color.argb(96, 255, 255, 255)
    val selectedWordPaint = Paint()
    selectedWordPaint.style = Paint.Style.STROKE
    selectedWordPaint.color = Color.GREEN.and(alpha)
    selectedWordPaint.strokeWidth = 2f

    val selectedSentencePaint = Paint()
    selectedSentencePaint.style = Paint.Style.STROKE
    selectedSentencePaint.color = Color.YELLOW.and(alpha)
    selectedSentencePaint.strokeWidth = 2f

    val selected = selectedWord.value
    val selectedSentence = selectedSentence.value

    p.sentences.forEach { s ->
      s.words.forEach { w ->
        w.boundingBoxes.forEach { wordBoundingBox ->
          val paint = when {
            w == selected -> selectedWordPaint
            w.sentence == selectedSentence -> selectedSentencePaint
            else -> null
          }
          if (paint != null) {
            canvas.drawRect(wordBoundingBox, paint)
          }
        }
      }
    }
  }
}