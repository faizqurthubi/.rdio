package com.otoradio.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otoradio.app.data.update.ApkInstaller
import com.otoradio.app.data.update.DownloadResult
import com.otoradio.app.data.update.UpdateCheck
import com.otoradio.app.data.update.UpdateInfo
import com.otoradio.app.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Kondisi layar pembaruan. Teksnya sengaja singkat (tanpa paragraf penjelasan). */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpToDate(val version: String) : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data class Downloading(val progress: Float) : UpdateUiState
    data object ReadyToInstall : UpdateUiState
    data class NeedPermission(val reason: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val repo = UpdateRepository(context)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var pending: UpdateInfo? = null
    private var lastCheckAt = 0L

    val currentVersion: String get() = repo.currentVersionName()

    /** Dipanggil saat app dibuka; hanya menembak jaringan bila sudah lewat 6 jam. */
    fun checkIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastCheckAt < 6 * 60 * 60 * 1000L) return
        check()
    }

    fun check() {
        lastCheckAt = System.currentTimeMillis()
        if (_state.value is UpdateUiState.Downloading) return
        _state.value = UpdateUiState.Checking
        viewModelScope.launch {
            _state.value = when (val r = repo.check()) {
                is UpdateCheck.UpToDate -> UpdateUiState.UpToDate(r.currentVersion)
                is UpdateCheck.Available -> {
                    pending = r.info
                    UpdateUiState.Available(r.info)
                }
                is UpdateCheck.Failed -> UpdateUiState.Error(r.message)
            }
        }
    }

    /** Unduh + verifikasi + buka dialog instalasi sistem. */
    fun downloadAndInstall() {
        val info = pending ?: return
        if (!ApkInstaller.canInstall(context)) {
            _state.value = UpdateUiState.NeedPermission(
                "Izinkan pemasangan aplikasi dari OtoRadio"
            )
            ApkInstaller.openInstallPermission(context)
            return
        }
        viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(0f)
            val result = repo.downloadAndVerify(info) { p ->
                _state.value = UpdateUiState.Downloading(p)
            }
            when (result) {
                is DownloadResult.Ready -> {
                    _state.value = UpdateUiState.ReadyToInstall
                    withContext(Dispatchers.Main) {
                        ApkInstaller.install(context, result.file)
                    }
                }
                is DownloadResult.Rejected ->
                    _state.value = UpdateUiState.Error(result.reason)
            }
        }
    }

    /** Setelah kembali dari dialog instalasi: versi berubah = berhasil diperbarui. */
    fun refreshAfterInstall() {
        val installed = repo.currentVersionName()
        if (installed != currentVersionAtOpen) {
            _state.value = UpdateUiState.UpToDate(installed)
            check()
        } else if (_state.value is UpdateUiState.ReadyToInstall) {
            _state.value = UpdateUiState.Available(pending ?: return)
        }
    }

    private val currentVersionAtOpen: String = repo.currentVersionName()
}
