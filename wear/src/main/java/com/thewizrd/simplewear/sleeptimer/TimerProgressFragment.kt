package com.thewizrd.simplewear.sleeptimer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.databinding.FragmentSleeptimerStopBinding
import java.util.*

class TimerProgressFragment : Fragment(), OnMessageReceivedListener {
    private lateinit var binding: FragmentSleeptimerStopBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentSleeptimerStopBinding.inflate(inflater, container, false)

        binding.timerProgressScroller.isTouchEnabled = false
        binding.timerProgressScroller.shouldSaveColorState = false

        binding.fab.setOnClickListener {
            LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(Intent(SleepTimerHelper.SleepTimerStopPath))
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        // stringToBytes(String.format(Locale.ROOT, "%d;%d", timeStartInMillis, timeInMillis)));
        if (messageEvent.data != null && messageEvent.path == SleepTimerHelper.SleepTimerStatusPath) {
            val data: String = messageEvent.data.bytesToString()
            val datas = data.split(";").toTypedArray()
            val startTimeMs = datas[0].toLong()
            val progressMs = datas[1].toLong()

            binding.timerProgressScroller.max = startTimeMs.toInt()
            binding.timerProgressScroller.progress = (startTimeMs - progressMs).toInt()
            setProgressText(progressMs)
        }
    }

    private fun setProgressText(progressMs: Long) {
        val hours = progressMs / 3600000L
        val mins = progressMs % 3600000L / 60000L
        val secs = progressMs / 1000 % 60

        if (hours > 0) {
            binding.progressText.text = String.format(Locale.ROOT, "%02d:%02d:%02d", hours, mins, secs)
        } else if (mins > 0) {
            binding.progressText.text = String.format(Locale.ROOT, "%02d:%02d", mins, secs)
        } else {
            binding.progressText.text = String.format(Locale.ROOT, "%02d", secs)
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(requireActivity()).addListener(this)
    }

    override fun onPause() {
        Wearable.getMessageClient(requireActivity()).removeListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}