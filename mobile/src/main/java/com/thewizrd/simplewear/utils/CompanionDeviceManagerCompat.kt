@file:RequiresApi(Build.VERSION_CODES.O)

package com.thewizrd.simplewear.utils

import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.thewizrd.shared_resources.utils.Logger
import java.util.concurrent.Executors

fun CompanionDeviceManager.disassociateAll() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        for (assoc in myAssociations) {
            if (assoc != null) {
                runCatching {
                    disassociate(assoc.id)
                }.onFailure {
                    Logger.writeLine(Log.ERROR, it)
                }
            }
        }
    } else {
        for (assoc in associations) {
            if (assoc != null) {
                runCatching {
                    disassociate(assoc)
                }.onFailure {
                    Logger.writeLine(Log.ERROR, it)
                }
            }
        }
    }
}

fun CompanionDeviceManager.hasAssociations(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        myAssociations.isNotEmpty()
    } else {
        associations.isNotEmpty()
    }
}

fun CompanionDeviceManager.associate(
    request: AssociationRequest,
    onDeviceFound: (IntentSender) -> Unit,
    onFailure: (CharSequence?) -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        associate(
            request,
            Executors.newSingleThreadExecutor(),
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    onDeviceFound.invoke(intentSender)
                }

                override fun onFailure(error: CharSequence?) {
                    onFailure.invoke(error)
                }
            })
    } else {
        associate(request, object : CompanionDeviceManager.Callback() {
            @Deprecated("Deprecated in Java", ReplaceWith("onAssociationPending"))
            override fun onDeviceFound(intentSender: IntentSender) {
                onDeviceFound.invoke(intentSender)
            }

            override fun onFailure(error: CharSequence?) {
                onFailure.invoke(error)
            }
        }, null)
    }
}