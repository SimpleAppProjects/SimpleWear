package com.thewizrd.shared_resources.actions

import android.os.*
import androidx.annotation.Keep
import com.thewizrd.shared_resources.utils.JSONParser

const val ACTION_PERFORMACTION = "SimpleWear.wearsettings.action.PERFORM_ACTION"
const val EXTRA_ACTION_DATA = "SimpleWear.wearsettings.extra.ACTION_DATA"
const val EXTRA_ACTION_ERROR = "SimpleWear.wearsettings.extra.ACTION_ERROR"
const val EXTRA_ACTION_CALLINGPKG = "SimpleWear.wearsettings.extra.CALLING_PACKAGE"

@Keep
class RemoteAction : Parcelable {
    var action: Action?
    var resultReceiver: ResultReceiver?

    constructor(action: Action, resultReceiver: ResultReceiver) {
        this.action = action
        this.resultReceiver = resultReceiver.toParcelableReceiver()
    }

    private constructor() {
        action = null
        resultReceiver = null
    }

    private constructor(parcel: Parcel) : this() {
        action = JSONParser.deserializer(parcel.readString(), Action::class.java)
        resultReceiver = parcel.readParcelable(ResultReceiver::class.java.classLoader)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(JSONParser.serializer(action, Action::class.java))
        parcel.writeParcelable(resultReceiver, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<RemoteAction> {
        override fun createFromParcel(parcel: Parcel): RemoteAction {
            return RemoteAction(parcel)
        }

        override fun newArray(size: Int): Array<RemoteAction?> {
            return arrayOfNulls(size)
        }
    }
}

fun ResultReceiver.toParcelableReceiver(): ResultReceiver {
    val parcel = Parcel.obtain()
    this.writeToParcel(parcel, 0)
    parcel.setDataPosition(0)

    val result = ResultReceiver.CREATOR.createFromParcel(parcel)
    parcel.recycle()
    return result
}

@Keep
class RemoteActionReceiver : ResultReceiver(Handler(getRemoteReceiverLooper())) {
    companion object {
        private val obj = Object()
        private var mRemoteReceiverLooper: Looper? = null

        private fun getRemoteReceiverLooper(): Looper {
            synchronized(obj) {
                if (mRemoteReceiverLooper == null) {
                    mRemoteReceiverLooper = HandlerThread("RemoteActionReceiver").apply {
                        start()
                    }.looper
                }

                return mRemoteReceiverLooper!!
            }
        }
    }

    var resultReceiver: IResultReceiver? = null

    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        if (resultData != null) {
            resultReceiver?.onReceiveResult(resultCode, resultData)
        }
    }

    interface IResultReceiver {
        fun onReceiveResult(resultCode: Int, resultData: Bundle)
    }
}

fun Action.toRemoteAction(receiver: ResultReceiver): RemoteAction {
    return RemoteAction(this, receiver)
}