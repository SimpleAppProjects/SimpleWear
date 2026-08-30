package com.thewizrd.simplewear.viewmodels

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import androidx.wear.compose.material3.R as wearM3Res

class ConfirmationViewModel : ViewModel() {
    private val _confirmationEventsFlow = MutableStateFlow<ConfirmationData?>(null)

    val confirmationEventsFlow = _confirmationEventsFlow
        .distinctUntilChanged(areEquivalent = { old, new -> old == new })
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = null
        )

    fun showConfirmation(data: ConfirmationData) {
        _confirmationEventsFlow.update { data }
    }

    fun showSuccess(message: String? = null) {
        _confirmationEventsFlow.update {
            ConfirmationData(
                animatedVectorResId = wearM3Res.drawable.wear_m3c_check_animation,
                confirmationType = ConfirmationType.Success,
                message = message
            )
        }
    }

    fun showSuccess(@StringRes messageResId: Int) {
        _confirmationEventsFlow.update {
            ConfirmationData(
                animatedVectorResId = wearM3Res.drawable.wear_m3c_check_animation,
                confirmationType = ConfirmationType.Success,
                messageResId = messageResId
            )
        }
    }

    fun showFailure(message: String? = null) {
        _confirmationEventsFlow.update {
            ConfirmationData(
                animatedVectorResId = wearM3Res.drawable.wear_m3c_failure_animation,
                confirmationType = ConfirmationType.Failure,
                message = message
            )
        }
    }

    fun showFailure(@StringRes messageResId: Int) {
        _confirmationEventsFlow.update {
            ConfirmationData(
                animatedVectorResId = wearM3Res.drawable.wear_m3c_failure_animation,
                confirmationType = ConfirmationType.Failure,
                messageResId = messageResId
            )
        }
    }

    fun showOpenOnPhone(message: String? = null) {
        _confirmationEventsFlow.update {
            ConfirmationData(
                animatedVectorResId = wearM3Res.drawable.wear_m3c_open_on_phone_animation,
                confirmationType = ConfirmationType.OpenOnPhone,
                message = message
            )
        }
    }

    fun showOpenOnPhone(@StringRes messageResId: Int) {
        _confirmationEventsFlow.update {
            ConfirmationData(
                animatedVectorResId = wearM3Res.drawable.wear_m3c_open_on_phone_animation,
                confirmationType = ConfirmationType.OpenOnPhone,
                messageResId = messageResId
            )
        }
    }

    fun showOpenOnPhoneForFailure(message: String? = null) {
        _confirmationEventsFlow.update {
            ConfirmationData(
                animatedVectorResId = wearM3Res.drawable.wear_m3c_open_on_phone_animation,
                confirmationType = ConfirmationType.Custom,
                message = message
            )
        }
    }

    fun showOpenOnPhoneForFailure(@StringRes messageResId: Int) {
        _confirmationEventsFlow.update {
            ConfirmationData(
                animatedVectorResId = wearM3Res.drawable.wear_m3c_open_on_phone_animation,
                confirmationType = ConfirmationType.Custom,
                messageResId = messageResId
            )
        }
    }

    fun clearFlow() {
        _confirmationEventsFlow.update { null }
    }
}

data class ConfirmationData(
    val message: String? = null,
    @param:StringRes val messageResId: Int? = null,
    @param:DrawableRes val iconResId: Int? = null,
    @param:DrawableRes val animatedVectorResId: Int? = null,
    val confirmationType: ConfirmationType = ConfirmationType.Custom,
    val durationMs: Long = ConfirmationDialogDefaults.DurationMillis
)

enum class ConfirmationType {
    Success, Failure, OpenOnPhone, Custom
}