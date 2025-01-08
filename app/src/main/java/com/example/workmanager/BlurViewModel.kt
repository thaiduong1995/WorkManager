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

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.workmanager.coroutine_worker.BlurWorker
import com.example.workmanager.coroutine_worker.CleanupWorker
import com.example.workmanager.coroutine_worker.SaveImageToFileWorker
//import com.example.workmanager.wokers.BlurWorker
//import com.example.workmanager.wokers.CleanupWorker
//import com.example.workmanager.wokers.SaveImageToFileWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BlurViewModel @Inject constructor(
    @ApplicationContext private val application: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(application)

    private var imageUri: Uri? = null
    internal var outputUri: Uri? = null

    private val _outputWorkInfos: MutableStateFlow<DataState> = MutableStateFlow(DataState.Idle)
    val outputWorkInfos: StateFlow<DataState> = _outputWorkInfos.asStateFlow()

    fun getListInfos() {
        viewModelScope.launch {
            _outputWorkInfos.update { DataState.Loading }
            try {
                withContext(Dispatchers.IO) {
                    workManager.getWorkInfosByTagFlow(TAG_OUTPUT).collect { workInfos ->
                        _outputWorkInfos.update {
                            if (workInfos.isEmpty()) {
                                DataState.Idle
                            } else {
                                DataState.Success(workInfos)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _outputWorkInfos.update {
                    DataState.Error("getWorkInfosByTagFlow failed ${e.message}")
                }
            }
        }
    }

    init {
        // This transformation makes sure that whenever the current work Id changes the WorkInfo
        // the UI is listening to changes
        imageUri = getImageUri(application.applicationContext)
    }

    /**
     * Create the WorkRequest to apply the blur and save the resulting image
     * @param blurLevel The amount to blur the image
     */
    internal fun applyBlur(blurLevel: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // Add WorkRequest to Cleanup temporary images
            var continuation = workManager.beginUniqueWork(
                    IMAGE_MANIPULATION_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequest.from(CleanupWorker::class.java)
                )
            // Add WorkRequests to blur the image the number of times requested
            for (i in 0 until blurLevel) {
                val blurBuilder = OneTimeWorkRequestBuilder<BlurWorker>()

                // Input the Uri if this is the first blur operation
                // After the first blur operation the input will be the output of previous
                // blur operations.
                if (i == 0) {
                    blurBuilder.setInputData(createInputDataForUri())
                }

                continuation = continuation.then(blurBuilder.build())
            }

            // Create charging constraint
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build()

            // Add WorkRequest to save the image to the filesystem
            val save = OneTimeWorkRequestBuilder<SaveImageToFileWorker>()
                .setConstraints(constraints)
                .addTag(TAG_OUTPUT)
                .build()
            continuation = continuation.then(save)

            // Actually start the work
            continuation.enqueue()
        }
    }

    /**
     * Creates the input data bundle which includes the Uri to operate on
     * @return Data which contains the Image Uri as a String
     */
    private fun createInputDataForUri(): Data {
        val builder = Data.Builder()
        imageUri?.let {
            builder.putString(KEY_IMAGE_URI, imageUri.toString())
        }
        return builder.build()
    }

    private fun uriOrNull(uriString: String?): Uri? {
        return if (!uriString.isNullOrEmpty()) {
            Uri.parse(uriString)
        } else {
            null
        }
    }

    private fun getImageUri(context: Context): Uri {
        val resources = context.resources

        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(R.drawable.android_cupcake))
            .appendPath(resources.getResourceTypeName(R.drawable.android_cupcake))
            .appendPath(resources.getResourceEntryName(R.drawable.android_cupcake))
            .build()
    }

    internal fun setOutputUri(outputImageUri: String?) {
        outputUri = uriOrNull(outputImageUri)
    }

    internal fun cancelWork() {
        viewModelScope.launch(Dispatchers.IO) {
            workManager.cancelUniqueWork(IMAGE_MANIPULATION_WORK_NAME)
        }
    }
}

