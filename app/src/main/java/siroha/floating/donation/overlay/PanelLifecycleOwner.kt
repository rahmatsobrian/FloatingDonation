package siroha.floating.donation.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The trio Jetpack Compose needs to host a real `ComposeView` — [LifecycleOwner],
 * [ViewModelStoreOwner] and [SavedStateRegistryOwner] — normally supplied for
 * free by `ComponentActivity`/`Fragment`. Our floating panels are attached
 * directly to a `WindowManager` from [siroha.floating.donation.service.OverlayService]
 * (a `Service`, not an `Activity`), so none of the three exist on that view
 * tree by default. Without them, `ComposeView.setContent { }` crashes
 * immediately with "ViewTreeLifecycleOwner not found" (Compose's internal
 * `WindowRecomposer` looks the lifecycle up via `ViewTreeLifecycleOwner.get()`
 * to know when it's allowed to actually run recomposition/frames).
 *
 * One instance is created per "build session" of a panel (see
 * `BasePanel.buildCard()`) and driven through the state machine below by
 * `BasePanel.show()`/`dismiss()`, mirroring what `ComponentActivity` does in
 * `onCreate()`/`onStart()`/`onResume()`/.../`onDestroy()`. `LifecycleRegistry`
 * can only move forward and can never leave `DESTROYED`, so a fresh instance
 * is created for every show — not reused across a dismiss()+show() cycle.
 */
internal class PanelLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    /** No saved state to restore (these panels are always built fresh) — restores an empty bundle. */
    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun onResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    /** Tears the composition(s) hosted under this owner down and frees the ViewModelStore. */
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
