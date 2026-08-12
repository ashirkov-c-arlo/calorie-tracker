package app.kcal.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The only place production code names a dispatcher. Tests pass a test dispatcher instead, so
 * background work stays deterministic and nothing reaches a real thread pool.
 */
data class DispatcherProvider(val io: CoroutineDispatcher = Dispatchers.IO)
