package com.thewizrd.shared_resources.lifecycle

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class LifecycleAwareFragment : Fragment() {
    /**
     * Check if the current fragment's lifecycle is alive
     *
     * @return Returns true if fragment's lifecycle is at least initialized
     */
    val isAlive: Boolean
        get() = lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)

    /**
     * Check if the current fragment's view lifecycle is alive
     *
     * @return Returns true if fragment's lifecycle is at least created and the view has been created but not yet destroyed
     */
    val isViewAlive: Boolean
        get() {
            val isFragmentCreated = lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
            if (isFragmentCreated) {
                try {
                    return viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)
                } catch (ignored: IllegalStateException) {
                    // Can't access the Fragment View's LifecycleOwner when the fragment's getView() is null i.e., before onCreateView() or after onDestroyView()
                }
            }
            return false
        }

    /**
     * Launches and runs the given runnable if the fragment is at least initialized
     * The action will be signalled to cancel if the fragment goes into the destroyed state
     * Note: This will run on the UI thread
     *
     * @param action The runnable to be executed
     */
    protected fun runOnUiThread(action: Runnable) {
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            action.run()
        }
    }

    /**
     * Launches and runs the given runnable if the fragment is at least initialized
     * The action will be signalled to cancel if the fragment goes into the destroyed state
     * Note: This will run on the UI thread
     *
     * @param context additional to [CoroutineScope.coroutineContext] context of the coroutine.
     * @param block the coroutine code which will be invoked in the context of the viewLifeCyclerOwner lifecycle scope.
     */
    fun runWithView(context: CoroutineContext = EmptyCoroutineContext,
                    block: suspend CoroutineScope.() -> Unit): Job? {
        var job: Job? = null

        runCatching {
            viewLifecycleOwner.lifecycleScope
        }.onFailure {
            // no-op
        }.onSuccess {
            job = it.launch(context = context, block = block)
        }

        return job
    }

    /**
     * Launches and runs the given runnable when the fragment is in the started
     * The action will be signalled to cancel if the fragment goes into the destroyed state
     * Note: This will run on the UI thread
     *
     * @param block the coroutine code which will be invoked when [Lifecycle] is at least in [Lifecycle.State.STARTED] state.
     */
    protected fun runWhenViewStarted(block: suspend CoroutineScope.() -> Unit): Job? {
        var job: Job? = null

        runCatching {
            viewLifecycleOwner.lifecycleScope
        }.onFailure {
            // no-op
        }.onSuccess {
            job = it.launchWhenStarted(block = block)
        }

        return job
    }
}