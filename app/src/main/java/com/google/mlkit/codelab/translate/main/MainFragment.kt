/*
 * Copyright 2020 Google Inc. All Rights Reserved.
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

package com.google.mlkit.codelab.translate.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.camera.core.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mlkit.codelab.translate.R
import com.google.mlkit.codelab.translate.analyzer.TextAnalyzer
import com.google.mlkit.codelab.translate.databinding.MainFragmentBinding
import com.google.mlkit.codelab.translate.util.Language
import com.google.mlkit.codelab.translate.util.ScopedExecutor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()

        // We only need to analyze the part of the image that has text, so we set crop percentages
        // to avoid analyze the entire image from the live camera feed.
        const val DESIRED_WIDTH_CROP_PERCENT = 8
        const val DESIRED_HEIGHT_CROP_PERCENT = 74

        // This is an arbitrary number we are using to keep tab of the permission
        // request. Where an app has multiple context for requesting permission,
        // this can help differentiate the different contexts
        private const val REQUEST_CODE_PERMISSIONS = 10

        // This is an array of all the permission specified in the manifest
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private const val TAG = "MainFragment"
    }

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var container: ConstraintLayout
    private lateinit var binding: MainFragmentBinding

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var scopedExecutor: ScopedExecutor

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()
        scopedExecutor.shutdown()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        container = view as ConstraintLayout
        binding = MainFragmentBinding.bind(view)

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        scopedExecutor = ScopedExecutor(cameraExecutor)

        loadImage()

        // Request camera permissions
        if (allPermissionsGranted()) {
            // Wait for the views to be properly laid out

        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Get available language list and set up the target language spinner
        // with default selections.
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item, viewModel.availableLanguages
        )

        binding.targetLangSelector.adapter = adapter
        binding.targetLangSelector.setSelection(adapter.getPosition(Language("en")))
        binding.targetLangSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                viewModel.targetLang.value = adapter.getItem(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        viewModel.sourceLang.observe(viewLifecycleOwner) { binding.srcLang.text = it.displayName }
        viewModel.translatedText.observe(viewLifecycleOwner) { resultOrError ->
            resultOrError?.let {
                if (it.error != null) {
                    binding.translatedText.error = resultOrError.error?.localizedMessage
                } else {
                    binding.translatedText.text = resultOrError.result
                }
            }
        }
        viewModel.modelDownloading.observe(viewLifecycleOwner) { isDownloading ->
            binding.progressBar.visibility = if (isDownloading) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            binding.progressText.visibility = binding.progressBar.visibility
        }

        binding.overlay.apply {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSPARENT)
        }

        binding.nlImage.selectedWord.observe(viewLifecycleOwner) { word ->
            viewModel.selectedWord.value = word
            viewModel.selectedSentence.value = word?.sentence
        }

        viewModel.selectedSentence.observe(viewLifecycleOwner) {
            binding.srcText.text = it?.text
        }
    }


    private fun loadImage() {
        viewModel.bitmap.observe(viewLifecycleOwner) { bitmap ->
            if (bitmap != null) {
                binding.nlImage.setImageBitmap(bitmap)
                TextAnalyzer(
                    requireContext(),
                    lifecycle,
                    viewModel.sourceText
                ).recognizeText(InputImage.fromBitmap(bitmap, 0))
            }
        }

        viewModel.sourceText.observe(viewLifecycleOwner) {
            binding.nlImage.setPage(it)
        }
    }

    private fun drawOverlay(
        holder: SurfaceHolder,
        text: Text,
    ) {
        if (true) return

        val drawingRect = Rect()
        val nlImage = binding.nlImage
        nlImage.getDrawingRect(drawingRect)

        Log.i(TAG, "w=${nlImage.width}, h=${nlImage.height}, drawingRect=$drawingRect")
        Log.i(TAG, "bounds=${(nlImage.drawable as BitmapDrawable).bounds}")

        val canvas = holder.lockCanvas()
        val bgPaint = Paint().apply {
            alpha = 140
        }
        canvas.drawPaint(bgPaint)
        val linePaint = Paint()
        linePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        linePaint.style = Paint.Style.FILL
        linePaint.color = Color.WHITE
        val wordPaint = Paint()
        wordPaint.style = Paint.Style.STROKE
        wordPaint.color = Color.RED
        wordPaint.strokeWidth = 1f

        val cornerRadius = 5f

        text.textBlocks.forEach { tb ->
            tb.lines.forEach { ln ->

                val lineBoundingBox = ln.boundingBox?.toRectF()
                if (lineBoundingBox != null) {
                    canvas.drawRoundRect(lineBoundingBox, cornerRadius, cornerRadius, linePaint)
                }

                ln.elements.forEach { el ->
                    val wordBoundingBox = el.boundingBox?.toRectF()
                    if (wordBoundingBox != null) {
                        canvas.drawRect(wordBoundingBox, wordPaint)
                    }
                }
            }
        }

        holder.unlockCanvasAndPost(canvas)
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by comparing absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = ln(max(width, height).toDouble() / min(width, height))
        if (abs(previewRatio - ln(RATIO_4_3_VALUE))
            <= abs(previewRatio - ln(RATIO_16_9_VALUE))
        ) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
//                viewFinder.post {
//                    // Keep track of the display in which this view is attached
//                    displayId = viewFinder.display.displayId
//
//                    // Set up the camera and its use cases
//                    setUpCamera()
//                }
            } else {
                Toast.makeText(
                    context,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }
}
