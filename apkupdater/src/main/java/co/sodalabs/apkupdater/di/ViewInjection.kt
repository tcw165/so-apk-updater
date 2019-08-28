package co.sodalabs.apkupdater.di

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import dagger.android.HasAndroidInjector
import dagger.internal.Preconditions.checkNotNull
import timber.log.Timber
import java.util.HashSet

object ViewInjection {

    /**
     * Inject the view.
     */
    fun inject(view: View) {
        checkNotNull(view, "view")
        val hasViewInjector = findHasViewInjector(view)
        val viewInjector = hasViewInjector.androidInjector()
        checkNotNull(viewInjector, "%s.viewInjector() returned null", viewInjector.javaClass.canonicalName)

        viewInjector.inject(view)
    }

    private fun findHasViewInjector(view: View): HasAndroidInjector {
        Timber.d("[Injection] Find view injector for $view...")
        if (view.parent != null) {
            var parent = view.parent
            while (parent != null) {
                Timber.d("[Injection] Searching parent...")
                if (parent is HasAndroidInjector) {
                    Timber.d("[Injection] Parent has the injector! $parent")
                    return parent
                }

                parent = parent.parent
            }
        }

        Timber.d("[Injection] Fall back to searching context...")
        val visitedContext = HashSet<Context>()
        var context = view.context
        while (!visitedContext.contains(context)) {
            if (context is HasAndroidInjector) {
                Timber.d("[Injection] Context has the injector! $context")
                return context
            } else if (context.applicationContext is HasAndroidInjector) {
                Timber.d("[Injection] Application context has the injector! $context")
                return context.applicationContext as HasAndroidInjector
            }

            // Mark the context is visited.
            visitedContext.add(context)

            if (context is ContextWrapper) {
                // Peel the wrapper.
                Timber.d("[Injection] Peel the context wrapper")
                context = context.baseContext
            }
        }

        // Make sure no leaking Context.
        visitedContext.clear()

        throw IllegalArgumentException(String.format("No injector was found for %s", view.javaClass.canonicalName))
    }
}