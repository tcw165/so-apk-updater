package co.sodalabs.updaterengine.extension

import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.Maybe
import io.reactivex.MaybeSource
import io.reactivex.MaybeTransformer
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.ObservableTransformer
import io.reactivex.Single
import io.reactivex.SingleSource
import io.reactivex.SingleTransformer
import org.reactivestreams.Publisher

@Suppress("unused")
fun <T> Observable<T>.echo(initialValue: T): Observable<Pair<T, T>> {
    return compose(ObservableEchoTransformer(initialValue))
}

@Suppress("unused")
fun <T> Flowable<T>.echo(initialValue: T): Flowable<Pair<T, T>> {
    return compose(FlowableEchoTransformer(initialValue))
}

@Suppress("unused")
fun <T> Maybe<T>.echo(initialValue: T): Maybe<Pair<T, T>> {
    return compose(MaybeEchoTransformer(initialValue))
}

@Suppress("unused")
fun <T> Single<T>.echo(initialValue: T): Single<Pair<T, T>> {
    return compose(SingleEchoTransformer(initialValue))
}

class ObservableEchoTransformer<T>(private var lastValue: T) : ObservableTransformer<T, Pair<T, T>> {

    override fun apply(upstream: Observable<T>): ObservableSource<Pair<T, T>> {
        return upstream.map { newValue ->
            synchronized(this) {
                val result = Pair(lastValue, newValue)
                lastValue = newValue
                result
            }
        }
    }
}

class FlowableEchoTransformer<T>(private var lastValue: T) : FlowableTransformer<T, Pair<T, T>> {

    override fun apply(upstream: Flowable<T>): Publisher<Pair<T, T>> {
        return upstream.map { newValue ->
            synchronized(this) {
                val result = Pair(lastValue, newValue)
                lastValue = newValue
                result
            }
        }
    }
}

class MaybeEchoTransformer<T>(private var lastValue: T) : MaybeTransformer<T, Pair<T, T>> {

    override fun apply(upstream: Maybe<T>): MaybeSource<Pair<T, T>> {
        return upstream.map { newValue ->
            synchronized(this) {
                val result = Pair(lastValue, newValue)
                lastValue = newValue
                result
            }
        }
    }
}

class SingleEchoTransformer<T>(private var lastValue: T) : SingleTransformer<T, Pair<T, T>> {

    override fun apply(upstream: Single<T>): SingleSource<Pair<T, T>> {
        return upstream.map { newValue ->
            synchronized(this) {
                val result = Pair(lastValue, newValue)
                lastValue = newValue
                result
            }
        }
    }
}