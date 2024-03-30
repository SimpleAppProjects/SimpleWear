package com.thewizrd.simplewear.viewmodels

import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplewear.DashboardActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.utils.ErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhoneSyncUiState(
    val connectionStatus: WearConnectionStatus? = null,
    val isLoading: Boolean = false,
    val showWifiButton: Boolean = false,
    val showBTButton: Boolean = false
)

class PhoneSyncViewModel(app: Application) : WearableListenerViewModel(app) {
    private val viewModelState = MutableStateFlow(PhoneSyncUiState(isLoading = true))

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    init {
        viewModelScope.launch {
            eventFlow.collect { event ->
                when (event.eventType) {
                    ACTION_UPDATECONNECTIONSTATUS -> {
                        val connectionStatus = WearConnectionStatus.valueOf(
                            event.data.getInt(
                                EXTRA_CONNECTIONSTATUS,
                                0
                            )
                        )

                        when (connectionStatus) {
                            WearConnectionStatus.DISCONNECTED -> {
                                checkNetworkStatus()
                                viewModelState.update {
                                    it.copy(
                                        isLoading = false
                                    )
                                }
                            }

                            WearConnectionStatus.CONNECTING -> {
                                viewModelState.update {
                                    it.copy(
                                        showWifiButton = false,
                                        showBTButton = false
                                    )
                                }
                            }

                            WearConnectionStatus.APPNOTINSTALLED -> {
                                viewModelState.update {
                                    it.copy(
                                        showWifiButton = false,
                                        showBTButton = false,
                                        isLoading = false
                                    )
                                }
                            }

                            WearConnectionStatus.CONNECTED -> {
                                viewModelState.update {
                                    it.copy(
                                        showWifiButton = false,
                                        showBTButton = false,
                                        isLoading = false
                                    )
                                }

                                viewModelScope.launch {
                                    // Verify connection by sending a 'ping'
                                    runCatching {
                                        sendPing(mPhoneNodeWithApp!!.id)
                                    }.onSuccess {
                                        // Continue operation
                                        activityContext?.startActivity(
                                            Intent(
                                                activityContext,
                                                DashboardActivity::class.java
                                            ).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                        )

                                        viewModelState.update {
                                            it.copy(
                                                isLoading = false
                                            )
                                        }

                                        activityContext?.finishAfterTransition()
                                    }.onFailure {
                                        setConnectionStatus(WearConnectionStatus.DISCONNECTED)
                                    }
                                }
                            }
                        }

                        viewModelState.update {
                            it.copy(
                                connectionStatus = connectionStatus
                            )
                        }
                    }
                }
            }
        }
    }

    fun refreshConnectionStatus() {
        viewModelState.update {
            it.copy(
                isLoading = true
            )
        }

        viewModelScope.launch {
            updateConnectionStatus()
        }
    }

    fun checkNetworkStatus() {
        val btAdapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
        if (btAdapter != null) {
            if (btAdapter.isEnabled || btAdapter.state == BluetoothAdapter.STATE_TURNING_ON) {
                viewModelState.update {
                    it.copy(
                        showBTButton = false
                    )
                }
            } else {
                _errorMessagesFlow.tryEmit(ErrorMessage.Resource(R.string.message_enablebt))

                viewModelState.update {
                    it.copy(
                        showBTButton = true
                    )
                }
            }
        } else {
            viewModelState.update {
                it.copy(
                    showBTButton = false
                )
            }
        }

        val wifiMgr = ContextCompat.getSystemService(appContext, WifiManager::class.java)
        if (wifiMgr != null && appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            viewModelState.update {
                it.copy(
                    showWifiButton = !wifiMgr.isWifiEnabled
                )
            }
        } else {
            viewModelState.update {
                it.copy(
                    showWifiButton = false
                )
            }
        }
    }

    fun openWifiSettings(activity: Activity) {
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    fun showProgressBar(show: Boolean = true) {
        viewModelState.update {
            it.copy(
                isLoading = show
            )
        }
    }
}