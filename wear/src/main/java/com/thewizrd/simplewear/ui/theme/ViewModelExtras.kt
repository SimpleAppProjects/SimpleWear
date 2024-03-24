package com.thewizrd.simplewear.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/** Try to fetch a viewModel in [store] */
@Composable
inline fun <reified T : ViewModel, S : ViewModelStoreOwner> viewModelInStore(store: S): Result<T> =
    runCatching {
        var result: Result<T>? = null
        CompositionLocalProvider(LocalViewModelStoreOwner provides store) {
            result = runCatching { viewModel(T::class.java) }
        }
        result!!.getOrThrow()
    }

/** Try to fetch a viewModel with current context (i.e. activity)  */
@Composable
inline fun <reified T : ViewModel> safeActivityViewModel(): Result<T> = runCatching {
    val activity = LocalContext.current as? ViewModelStoreOwner
        ?: throw IllegalStateException("Current context is not a viewModelStoreOwner.")
    return viewModelInStore(activity)
}

/** Try to fetch a viewModel with current context (i.e. activity)  */
@Composable
inline fun <reified T : ViewModel> safeActivityViewModel(context: Context): Result<T> =
    runCatching {
        val activity = context as? ViewModelStoreOwner
            ?: throw IllegalStateException("Current context is not a viewModelStoreOwner.")
        return viewModelInStore(activity)
    }

/** Force fetch a viewModel inside context's viewModelStore */
@Composable
inline fun <reified T : ViewModel> activityViewModel(): T = safeActivityViewModel<T>().getOrThrow()

@Composable
inline fun <reified T : ViewModel> activityViewModel(context: Context): T =
    safeActivityViewModel<T>(context).getOrThrow()