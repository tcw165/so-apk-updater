package co.sodalabs.updaterengine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import io.reactivex.Observable
import timber.log.Timber

/**
 * Wrap the [BroadcastReceiver] in the Reactive way.
 */
object RxBroadcastReceiver {

    @JvmStatic
    fun bind(
        context: Context,
        intentFilter: IntentFilter,
        captureStickyIntent: Boolean = true
    ): Observable<Intent> {
        return Observable.create { e ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (e.isDisposed) return

                    // Timber.v("Receive $intent from the global broadcast for $context")
                    e.onNext(intent)
                }
            }

            e.setCancellable {
                // Timber.d("Unregister the global broadcast listening from $context")
                try {
                    context.unregisterReceiver(receiver)
                } catch (err: Throwable) {
                    // IGNORED because we couldn't check if the receiver has
                    // registered.
                    Timber.e(err)
                }
            }

            val stickyIntent: Intent? = context.registerReceiver(receiver, intentFilter)
            stickyIntent?.let {
                if (captureStickyIntent) {
                    // Timber.v("Got sticky intent as registering broadcast: $stickyIntent")
                    e.onNext(it)
                }
            }
        }
    }
}