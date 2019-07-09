package co.sodalabs.updaterengine

import io.reactivex.Scheduler

interface IThreadSchedulers {

    fun main(): Scheduler

    fun computation(): Scheduler

    fun io(): Scheduler

    fun single(): Scheduler

    fun ensureMainThread()
}