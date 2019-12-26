package co.sodalabs.updaterengine.utils

import android.os.Looper
import co.sodalabs.updaterengine.IThreadSchedulers
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

class TestThreadSchedulers : IThreadSchedulers {

    override fun main(): Scheduler {
        return Schedulers.trampoline()
    }

    override fun computation(): Scheduler {
        return Schedulers.trampoline()
    }

    override fun io(): Scheduler {
        return Schedulers.trampoline()
    }

    override fun single(): Scheduler {
        return Schedulers.trampoline()
    }

    override fun ensureMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalThreadStateException("Not in UI thread")
        }
    }
}