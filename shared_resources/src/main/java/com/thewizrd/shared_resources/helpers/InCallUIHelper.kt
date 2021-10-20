package com.thewizrd.shared_resources.helpers

import androidx.annotation.IntDef

object InCallUIHelper {
    const val CallStatePath = "/incallui"
    const val CallStateBridgePath = "/incallui/bridge"

    const val DisconnectPath = "/incallui/disconnect"
    const val EndCallPath = "/incallui/hangup"
    const val MuteMicPath = "/incallui/mute"
    const val MuteMicStatusPath = "/incallui/mute/status"
    const val SpeakerphonePath = "/incallui/speakerphone"
    const val SpeakerphoneStatusPath = "/incallui/speakerphone/status"
    const val DTMFPath = "/incallui/dtmf"

    const val KEY_CALLERNAME = "key_callername"
    const val KEY_CALLERBMP = "key_callerbmp"
    const val KEY_CALLACTIVE = "key_callactive"
    const val KEY_SUPPORTEDFEATURES = "key_supportedfeatures"

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        INCALL_FEATURE_SPEAKERPHONE,
        INCALL_FEATURE_DTMF
    )
    annotation class InCallUIFeatures

    const val INCALL_FEATURE_SPEAKERPHONE = 0b1
    const val INCALL_FEATURE_DTMF = 0b10
}