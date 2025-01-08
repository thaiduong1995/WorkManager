package com.example.workmanager.coroutine_worker

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.workmanager.DELAY_TIME_MILLIS
import com.example.workmanager.KEY_IMAGE_URI
import com.example.workmanager.blurBitmap
import com.example.workmanager.makeStatusNotification
import com.example.workmanager.writeBitmapToFile
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@HiltWorker
class BlurWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val appContext = applicationContext

        val resourceUri = inputData.getString(KEY_IMAGE_URI)

        makeStatusNotification("Blurring image", appContext)

        // This is an utility function added to emulate slower work.
        delay(DELAY_TIME_MILLIS)

        return withContext(Dispatchers.IO) {
            try {
                // Use one of these two methods to check if the worker has been destroyed.
                ensureActive()
                if (!coroutineContext.isActive) return@withContext Result.failure()
                if (TextUtils.isEmpty(resourceUri)) {
                    Log.e(TAG, "Invalid input uri")
                    throw IllegalArgumentException("Invalid input uri")
                }

                val resolver = appContext.contentResolver

                val picture = BitmapFactory.decodeStream(
                    resolver.openInputStream(Uri.parse(resourceUri))
                )

                val output = blurBitmap(picture, appContext)

                // Write bitmap to a temp file
                val outputUri = writeBitmapToFile(appContext, output)

                val outputData = workDataOf(KEY_IMAGE_URI to outputUri.toString())

                Result.success(outputData)
            } catch (throwable: Throwable) {
                Log.e(TAG, "Error applying blur")
                throwable.printStackTrace()
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "BlurWorker"
    }
}