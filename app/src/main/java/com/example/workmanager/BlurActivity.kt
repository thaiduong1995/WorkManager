/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.workmanager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import com.example.workmanager.databinding.ActivityBlurBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BlurActivity : AppCompatActivity() {

    private var binding: ActivityBlurBinding? = null

    private val viewModel: BlurViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlurBinding.inflate(layoutInflater)
        setContentView(binding?.root)

        initData()
        observeData()
        initListener()
    }

    private fun initData() {
        viewModel.getListInfos()
    }

    private fun observeData() {
        this.lifecycleScope.launch {
            this@BlurActivity.lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch {
                    viewModel.outputWorkInfos.collect {
                        workInfosObserver(it)
                    }
                }
            }
        }
    }

    private fun workInfosObserver(workInfosState: DataState) {
        Log.d(CHECK_WORKER, "workInfosObserver: $workInfosState")
        when (workInfosState) {
            is DataState.Error -> {}
            DataState.Idle -> {}
            DataState.Loading -> {}
            is DataState.Success -> {
                (workInfosState.data as? List<WorkInfo>)?.let { listOfWorkInfo ->

                    // Note that these next few lines grab a single WorkInfo if it exists
                    // This code could be in a Transformation in the ViewModel; they are included here
                    // so that the entire process of displaying a WorkInfo is in one location.

                    // If there are no matching work info, do nothing
                    if (listOfWorkInfo.isEmpty()) {
                        return
                    }

                    // We only care about the one output status.
                    // Every continuation has only one worker tagged TAG_OUTPUT
                    val workInfo = listOfWorkInfo[0]

                    if (workInfo.state.isFinished) {
                        showWorkFinished()

                        // Normally this processing, which is not directly related to drawing views on
                        // screen would be in the ViewModel. For simplicity we are keeping it here.
                        val outputImageUri = workInfo.outputData.getString(KEY_IMAGE_URI)

                        // If there is an output file show "See File" button
                        if (!outputImageUri.isNullOrEmpty()) {
                            viewModel.setOutputUri(outputImageUri)
                            binding?.seeFileButton?.visibility = View.VISIBLE
                        }
                    } else {
                        showWorkInProgress()
                    }

                }
            }
        }
    }

    /**
     * Shows and hides views for when the Activity is processing an image
     */
    private fun showWorkInProgress() {
        binding?.progressBar?.visibility = View.VISIBLE
        binding?.cancelButton?.visibility = View.VISIBLE
        binding?.goButton?.visibility = View.GONE
        binding?.seeFileButton?.visibility = View.GONE
    }

    /**
     * Shows and hides views for when the Activity is done processing an image
     */
    private fun showWorkFinished() {
        binding?.progressBar?.visibility = View.GONE
        binding?.cancelButton?.visibility = View.GONE
        binding?.goButton?.visibility = View.VISIBLE
    }

    private fun initListener() {
        onClickButtonGoListener()
        onClickButtonSeeFileListener()
        onClickButtonCancelListener()
    }

    private fun onClickButtonGoListener() {
        binding?.goButton?.setOnClickListener { viewModel.applyBlur(blurLevel) }
    }

    // Setup view output image file button
    private fun onClickButtonSeeFileListener() {
        binding?.seeFileButton?.setOnClickListener {
            viewModel.outputUri?.let { currentUri ->
                val actionView = Intent(Intent.ACTION_VIEW, currentUri)
                actionView.resolveActivity(packageManager)?.run {
                    startActivity(actionView)
                }
            }
        }
    }

    // Hookup the Cancel button
    private fun onClickButtonCancelListener() {
        binding?.cancelButton?.setOnClickListener { viewModel.cancelWork() }
    }

    private val blurLevel: Int
        get() =
            when (binding?.radioBlurGroup?.checkedRadioButtonId) {
                R.id.radio_blur_lv_1 -> 1
                R.id.radio_blur_lv_2 -> 2
                R.id.radio_blur_lv_3 -> 3
                else -> 1
            }

    companion object {
        const val CHECK_WORKER = "CHECK_WORKER"
    }
}
