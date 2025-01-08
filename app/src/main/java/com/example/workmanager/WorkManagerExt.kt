package com.example.workmanager

import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Convert LiveData listening to Flow
 * @param tag The tag of the work
 * @return A Flow list of WorkInfo for work tagged with tag
 */
fun WorkManager.getWorkInfosByTagFlow(tag: String): Flow<List<WorkInfo>> = callbackFlow {
    val liveData = getWorkInfosByTagLiveData(tag)
    val observer = androidx.lifecycle.Observer<List<WorkInfo>> { workInfos ->
        trySend(workInfos)
    }
    launch(Dispatchers.Main) {
        liveData.observeForever(observer)
    }
    awaitClose {
        liveData.removeObserver(observer)
    }
}