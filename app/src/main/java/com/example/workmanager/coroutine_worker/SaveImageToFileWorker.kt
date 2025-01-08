package com.example.workmanager.coroutine_worker

import android.content.Context
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.workmanager.DELAY_TIME_MILLIS
import com.example.workmanager.KEY_IMAGE_URI
import com.example.workmanager.makeStatusNotification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale

@HiltWorker
class SaveImageToFileWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    private val title = "Blurred Image"
    private val dateFormatter = SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss z", Locale.getDefault())

    override suspend fun doWork(): Result {
        // Makes a notification when the work starts and slows down the work so that
        // it's easier to see each WorkRequest start, even on emulated devices
        makeStatusNotification("Saving image", applicationContext)
        delay(DELAY_TIME_MILLIS)

        val resolver = applicationContext.contentResolver
        return withContext(Dispatchers.IO) {
            try {
                // Use one of these two methods to check if the worker has been destroyed.
                ensureActive()
                if (!coroutineContext.isActive) return@withContext Result.failure()
                val resourceUri = inputData.getString(KEY_IMAGE_URI)
                val bitmap = BitmapFactory.decodeStream(
                    resolver.openInputStream(Uri.parse(resourceUri))
                )
                val imageUrl = MediaStore.Images.Media.insertImage(
                    resolver, bitmap, title, dateFormatter.format(Date())
                )
                if (!imageUrl.isNullOrEmpty()) {
                    val output = workDataOf(KEY_IMAGE_URI to imageUrl)

                    Result.success(output)
                } else {
                    Log.e(TAG, "Writing to MediaStore failed")
                    Result.failure()
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "CleanupWorker"
    }
}