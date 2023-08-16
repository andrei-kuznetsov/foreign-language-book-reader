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
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mlkit.codelab.translate.R
import com.google.mlkit.codelab.translate.analyzer.TextAnalyzer
import com.google.mlkit.codelab.translate.databinding.MainFragmentBinding
import com.google.mlkit.codelab.translate.util.Language
import com.google.mlkit.codelab.translate.util.ScopedExecutor
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


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

        binding.srcLang.text = viewModel.sourceLang.displayName
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

        binding.nlImage.selectedSentence.observe(viewLifecycleOwner) { sentence ->
            viewModel.selectedSentence.value = sentence
        }
        binding.nlImage.selectedWord.observe(viewLifecycleOwner) { word ->
            viewModel.selectedWord.value = word
            if (word != null) {
                translateWithGoogleTranslateApp(word.text)
            }
        }
        binding.translateSentence.setOnClickListener {
            viewModel.shownText.value?.text?.let { text ->
                translateWithGoogleTranslateApp(text)
            }
        }

        viewModel.selectedSentence.observe(viewLifecycleOwner) {
            binding.srcText.text = it?.text
        }
    }

    private fun translateWithGoogleTranslateApp(text: String) {
        val i = Intent()
            .setAction(Intent.ACTION_TRANSLATE)
            .putExtra(Intent.EXTRA_TEXT, text)
            .putExtra("key_text_input", text)
            .putExtra("key_language_from", viewModel.sourceLang.code)

        try {
            startActivity(i)
        } catch (e: Throwable) {
            Toast.makeText(
                requireContext(),
                e.toString(),
                Toast.LENGTH_SHORT
            ).show();
        }
    }


    private fun loadImage() {
        viewModel.bitmap.observe(viewLifecycleOwner) { bitmap ->
            if (bitmap != null) {
                binding.nlImage.bitmap = bitmap
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
