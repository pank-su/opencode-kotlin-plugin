package us.panks.opencode.dsl.internal

import js.errors.JsError
import js.errors.JsErrorLike
import js.promise.Promise
import opencode.plugin.promise.Cleanup
import opencode.plugin.promise.Context
import opencode.plugin.promise.Registration

internal fun interface SetupAction {
    fun register(context: Context): Promise<Registration>
}

internal fun executeSetupPlan(
    context: Context,
    actions: List<SetupAction>,
): Promise<Cleanup> {
    val registrations = mutableListOf<Registration>()

    fun register(index: Int): Promise<Cleanup> {
        if (index >= actions.size) {
            var cleanupPromise: Promise<Unit>? = null
            val cleanup: Cleanup = {
                cleanupPromise ?: disposeRegistrations(registrations).also { cleanupPromise = it }
            }
            return Promise.resolve(cleanup)
        }

        return Promise.flatTry { actions[index].register(context) }.flatThen(
            onFulfilled = { registration ->
                registrations += registration
                register(index + 1)
            },
            onRejected = { registrationError ->
                disposeRegistrations(registrations).flatThen(
                    onFulfilled = { rejected(registrationError) },
                    onRejected = { rejected(registrationError) },
                )
            },
        )
    }

    return register(index = 0)
}

private fun disposeRegistrations(
    registrations: List<Registration>,
): Promise<Unit> {
    fun dispose(
        index: Int,
        hasFailure: Boolean,
        firstFailure: JsErrorLike?,
    ): Promise<Unit> {
        if (index < 0) {
            return if (!hasFailure) Promise.resolve(Unit) else rejected(firstFailure)
        }

        return Promise.flatTry { registrations[index].dispose() }.flatThen(
            onFulfilled = { dispose(index - 1, hasFailure, firstFailure) },
            onRejected = { failure ->
                dispose(
                    index = index - 1,
                    hasFailure = true,
                    firstFailure = if (hasFailure) firstFailure else failure,
                )
            },
        )
    }

    return dispose(
        index = registrations.lastIndex,
        hasFailure = false,
        firstFailure = null,
    )
}

private fun <T> rejected(error: JsErrorLike?): Promise<T> =
    Promise.reject(error.unsafeCast<JsError>())
