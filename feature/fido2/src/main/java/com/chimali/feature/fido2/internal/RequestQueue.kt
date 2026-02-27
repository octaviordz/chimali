package com.chimali.feature.fido2.internal

import java.util.concurrent.LinkedBlockingQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestQueue @Inject constructor() {
    private val queue = LinkedBlockingQueue<ByteArray>()

    fun enqueue(request: ByteArray) {
        queue.put(request)
    }

    fun dequeue(): ByteArray? {
        return queue.poll()
    }

    fun isEmpty(): Boolean = queue.isEmpty()
}
