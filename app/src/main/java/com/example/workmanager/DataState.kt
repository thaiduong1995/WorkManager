package com.example.workmanager

import androidx.annotation.Keep

@Keep
sealed class DataState {
    data object Idle : DataState()
    data object Loading: DataState()
    data class Success(val data: Any): DataState()
    data class Error(val message: String?): DataState()
}