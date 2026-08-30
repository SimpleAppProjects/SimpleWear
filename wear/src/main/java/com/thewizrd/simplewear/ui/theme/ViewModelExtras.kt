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
inline fun <reified T : ViewModel, S : ViewModelStoreOwner> viewModelInStore(store: S): T {
    var result: T? = null
    CompositionLocalProvider(LocalViewModelStoreOwner provides store) {
        result = viewModel(T::class.java)
    }
    return result!!
}

/** Try to fetch a viewModel with current context (i.e. activity)  */
@Composable
inline fun <reified T : ViewModel> safeActivityViewModel(): Result<T> {
    val context = LocalContext.current
    return if (context is ViewModelStoreOwner) {
        Result.success(viewModelInStore(context))
    } else {
        Result.failure(IllegalStateException("Current context is not a viewModelStoreOwner."))
    }
}

/** Try to fetch a viewModel with current context (i.e. activity)  */
@Composable
inline fun <reified T : ViewModel> safeActivityViewModel(context: Context): Result<T> {
    return if (context is ViewModelStoreOwner) {
        Result.success(viewModelInStore(context))
    } else {
        Result.failure(IllegalStateException("Current context is not a viewModelStoreOwner."))
    }
}

/** Force fetch a viewModel inside context's viewModelStore */
@Composable
inline fun <reified T : ViewModel> activityViewModel(): T = safeActivityViewModel<T>().getOrThrow()

@Composable
inline fun <reified T : ViewModel> activityViewModel(context: Context): T =
    safeActivityViewModel<T>(context).getOrThrow()