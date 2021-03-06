package coil.transition

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import coil.annotation.ExperimentalCoilApi
import coil.decode.DataSource
import coil.drawable.CrossfadeDrawable
import coil.request.ErrorResult
import coil.request.RequestResult
import coil.request.SuccessResult
import coil.size.Scale
import coil.util.scale
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A [Transition] that crossfades from the current drawable to a new one. */
@ExperimentalCoilApi
class CrossfadeTransition @JvmOverloads constructor(
    val durationMillis: Int = CrossfadeDrawable.DEFAULT_DURATION
) : Transition {

    init {
        require(durationMillis > 0) { "durationMillis must be > 0." }
    }

    override suspend fun transition(
        target: TransitionTarget<*>,
        result: RequestResult
    ) {
        // Don't animate if the request was fulfilled by the memory cache.
        if (result is SuccessResult && result.source == DataSource.MEMORY_CACHE) {
            target.onSuccess(result.drawable)
            return
        }

        // Don't animate if the view is not visible as CrossfadeDrawable.onDraw
        // won't be called until the view becomes visible.
        if (!target.view.isVisible) {
            when (result) {
                is SuccessResult -> target.onSuccess(result.drawable)
                is ErrorResult -> target.onError(result.drawable)
            }
            return
        }

        // Animate the drawable and suspend until the animation is completes.
        suspendCancellableCoroutine<Unit> { continuation ->
            val crossfadeDrawable = createCrossfade(continuation, target, result.drawable)
            when (result) {
                is SuccessResult -> target.onSuccess(crossfadeDrawable)
                is ErrorResult -> target.onError(crossfadeDrawable)
            }
        }
    }

    /** Create a [CrossfadeDrawable]. [continuation] will suspend until the crossfade animation completes. */
    private fun createCrossfade(
        continuation: CancellableContinuation<Unit>,
        target: TransitionTarget<*>,
        drawable: Drawable?
    ): CrossfadeDrawable {
        val crossfade = CrossfadeDrawable(
            start = target.drawable,
            end = drawable,
            scale = (target.view as? ImageView)?.scale ?: Scale.FILL,
            durationMillis = durationMillis
        )
        val callback = Callback(crossfade, continuation)
        crossfade.registerAnimationCallback(callback)
        continuation.invokeOnCancellation(callback)
        return crossfade
    }

    /** Handle cancellation of the continuation and completion of the animation in one object. */
    private class Callback(
        private val crossfade: CrossfadeDrawable,
        private val continuation: CancellableContinuation<Unit>
    ) : Animatable2Compat.AnimationCallback(), CompletionHandler {

        override fun onAnimationEnd(drawable: Drawable) {
            crossfade.unregisterAnimationCallback(this)
            continuation.resume(Unit)
        }

        override fun invoke(cause: Throwable?) = crossfade.stop()
    }
}
