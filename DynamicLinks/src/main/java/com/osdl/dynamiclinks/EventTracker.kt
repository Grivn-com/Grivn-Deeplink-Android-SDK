package com.osdl.dynamiclinks

import com.osdl.dynamiclinks.network.ApiService
import com.osdl.dynamiclinks.network.EventBatchRequest
import com.osdl.dynamiclinks.network.EventItemRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SDK 内部事件追踪器
 *
 * 批量缓存事件，满 20 条或 30 秒自动 flush 到后端。
 */
internal class EventTracker(
    private val apiService: ApiService,
    private val projectId: String,
    private val deviceId: String,
    private val deviceType: String,
    private val osVersion: String,
    private val appVersion: String,
    private val sdkVersion: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingEvents = mutableListOf<EventItemRequest>()
    private val lock = Any()
    private var flushJob: Job? = null
    private var userId: String = ""

    fun start() {
        flushJob = scope.launch {
            while (true) {
                delay(30_000L)
                flush()
            }
        }
        SDKLogger.debug("EventTracker started")
    }

    fun stop() {
        flushJob?.cancel()
        flushJob = null
        // Best-effort final flush
        scope.launch {
            flush()
        }
        SDKLogger.debug("EventTracker stopped")
    }

    fun trackEvent(name: String, params: Map<String, Any>? = null, deeplinkId: String? = null) {
        val item = EventItemRequest(
            eventName = name,
            params = params,
            timestamp = System.currentTimeMillis(),
            deeplinkId = deeplinkId ?: "",
            userId = userId
        )

        val shouldFlush: Boolean
        synchronized(lock) {
            pendingEvents.add(item)
            shouldFlush = pendingEvents.size >= 20
        }

        if (shouldFlush) {
            scope.launch { flush() }
        }
    }

    fun setUserId(id: String) {
        userId = id
        SDKLogger.debug("EventTracker userId set to: $id")
    }

    fun flush() {
        val batch: List<EventItemRequest>
        synchronized(lock) {
            if (pendingEvents.isEmpty()) return
            batch = pendingEvents.toList()
            pendingEvents.clear()
        }

        SDKLogger.debug("Flushing ${batch.size} events")

        val request = EventBatchRequest(
            projectId = projectId,
            deviceId = deviceId,
            deviceType = deviceType,
            osVersion = osVersion,
            appVersion = appVersion,
            sdkVersion = sdkVersion,
            events = batch
        )

        val response = apiService.trackEvents(request)
        if (response.isSuccess) {
            SDKLogger.info("Flushed ${batch.size} events successfully")
        } else {
            SDKLogger.warn("Failed to flush events, re-enqueuing", response.exceptionOrNull())
            synchronized(lock) {
                pendingEvents.addAll(0, batch)
                // Cap at 1000 to prevent unbounded growth
                while (pendingEvents.size > 1000) {
                    pendingEvents.removeAt(pendingEvents.size - 1)
                }
            }
        }
    }
}
