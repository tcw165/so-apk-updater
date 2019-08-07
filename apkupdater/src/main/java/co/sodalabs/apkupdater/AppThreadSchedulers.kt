package co.sodalabs.apkupdater

import android.os.Looper
import co.sodalabs.updaterengine.IThreadSchedulers
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.internal.schedulers.SingleScheduler
import io.reactivex.schedulers.Schedulers

class AppThreadSchedulers : IThreadSchedulers {

    private val singleIoScheduler = SingleScheduler()

    override fun main(): Scheduler {
        return AndroidSchedulers.mainThread()
    }

    override fun computation(): Scheduler {
        return Schedulers.computation()
    }

    override fun io(): Scheduler {
        return Schedulers.io()
    }

    override fun single(): Scheduler {
        return singleIoScheduler
    }

    override fun ensureMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalThreadStateException("Not in UI thread")
        }
    }
}