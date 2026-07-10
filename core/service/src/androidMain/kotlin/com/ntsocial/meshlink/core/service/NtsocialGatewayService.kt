/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.ntsocial.meshlink.core.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.os.RemoteCallbackList
import android.os.RemoteException
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.toRemoteExceptions
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.gateway.INtsocialEnvelopeCallback
import com.ntsocial.meshlink.core.gateway.INtsocialGatewayService
import com.ntsocial.meshlink.core.gateway.NtsocialEnvelopeData
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayContract
import com.ntsocial.meshlink.core.gateway.NtsocialGatewayStatus
import com.ntsocial.meshlink.core.model.ntsocial.NtsocialCachedEnvelope
import com.ntsocial.meshlink.core.repository.NtsocialGatewayRepository
import com.ntsocial.meshlink.core.repository.ServiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okio.ByteString.Companion.toByteString
import org.koin.android.ext.android.inject

/**
 * @deprecated Transitional Binder compatibility adapter. New NTsocial integrations use [NtsocialGatewayProvider] and
 *   [NtsocialGatewayCommandReceiver] instead.
 */
@Deprecated("Use the ContentProvider and command/event Gateway IPC instead")
class NtsocialGatewayService : Service() {
    private val ntsocialGatewayRepository: NtsocialGatewayRepository by inject()

    private val serviceRepository: ServiceRepository by inject()

    private val dispatchers: CoroutineDispatchers by inject()

    private val callbacks = RemoteCallbackList<INtsocialEnvelopeCallback>()
    private val deliveredLock = Any()
    private val deliveredCacheKeys = mutableSetOf<String>()
    private val serviceScope by lazy { CoroutineScope(dispatchers.io + SupervisorJob()) }

    private val binder =
        object : INtsocialGatewayService.Stub() {
            override fun sendNtsocialPayload(channelIndex: Int, payload: ByteArray?): Int {
                enforceGatewayPermission()
                return toRemoteExceptions {
                    requireNotNull(payload) { "payload is required" }
                    ntsocialGatewayRepository
                        .sendTestPayload(payload = payload.toByteString(), channelIndex = channelIndex)
                        .packetId
                }
            }

            override fun observeNtsocialEnvelope(callback: INtsocialEnvelopeCallback?) {
                enforceGatewayPermission()
                toRemoteExceptions {
                    requireNotNull(callback) { "callback is required" }
                    callbacks.register(callback)
                    sendCachedEnvelopes(callback)
                }
            }

            override fun stopObservingNtsocialEnvelope(callback: INtsocialEnvelopeCallback?) {
                enforceGatewayPermission()
                toRemoteExceptions {
                    requireNotNull(callback) { "callback is required" }
                    callbacks.unregister(callback)
                }
            }

            override fun getGatewayStatus(): NtsocialGatewayStatus {
                enforceGatewayPermission()
                return buildStatus()
            }

            override fun getCachedNtsocialEnvelopes(): List<NtsocialEnvelopeData> {
                enforceGatewayPermission()
                return ntsocialGatewayRepository.cachedEnvelopes.value.map { it.toGatewayEnvelopeData() }
            }
        }

    override fun onCreate() {
        super.onCreate()
        Logger.i { "Creating NTsocial gateway service" }
        ntsocialGatewayRepository.cachedEnvelopes.onEach(::publishNewEnvelopes).launchIn(serviceScope)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Logger.i { "Destroying NTsocial gateway service" }
        callbacks.kill()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildStatus(): NtsocialGatewayStatus = buildNtsocialGatewayStatus(
        connectionState = serviceRepository.connectionState.value,
        cachedEnvelopeCount = ntsocialGatewayRepository.cachedEnvelopes.value.size,
    )

    private fun publishNewEnvelopes(envelopes: List<NtsocialCachedEnvelope>) {
        val newEnvelopes = synchronized(deliveredLock) { envelopes.filter { deliveredCacheKeys.add(it.deliveryKey()) } }

        if (newEnvelopes.isEmpty()) return

        val callbackCount = callbacks.beginBroadcast()
        try {
            repeat(callbackCount) { index ->
                val callback = callbacks.getBroadcastItem(index)
                newEnvelopes.forEach { envelope -> notifyCallback(callback, envelope.toGatewayEnvelopeData()) }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private fun sendCachedEnvelopes(callback: INtsocialEnvelopeCallback) {
        ntsocialGatewayRepository.cachedEnvelopes.value.forEach { envelope ->
            notifyCallback(callback, envelope.toGatewayEnvelopeData())
        }
    }

    private fun notifyCallback(callback: INtsocialEnvelopeCallback, envelope: NtsocialEnvelopeData) {
        try {
            callback.onNtsocialEnvelope(envelope)
        } catch (ex: RemoteException) {
            Logger.w(ex) { "NTsocial gateway envelope callback failed" }
        }
    }

    @Suppress("DEPRECATION")
    private fun enforceGatewayPermission() {
        if (Binder.getCallingUid() != Process.myUid()) {
            enforceCallingPermission(
                NtsocialGatewayContract.PERMISSION_BIND,
                "Caller must hold ${NtsocialGatewayContract.PERMISSION_BIND}",
            )
        }
    }

    private fun NtsocialCachedEnvelope.deliveryKey(): String = "${direction.name}:$packetId:$cacheKey"
}
