package com.domopi.app.network

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

/** Registration is owned by the active flow collection. Events carry no network facts. */
fun interface NetworkInvalidations {
    fun register(invalidate: () -> Unit): AutoCloseable
}

class NetworkEvidenceObserver(
    private val invalidations: NetworkInvalidations,
    private val collectSnapshot: () -> NetworkEvidence
) {
    fun observe(): Flow<NetworkEvidence> = callbackFlow {
        val registration = invalidations.register { trySend(Unit) }
        trySend(Unit) // Initial snapshot, after registration to avoid an observation gap.
        awaitClose { registration.close() }
    }.buffer(Channel.CONFLATED).map { collectSnapshot() }
}
