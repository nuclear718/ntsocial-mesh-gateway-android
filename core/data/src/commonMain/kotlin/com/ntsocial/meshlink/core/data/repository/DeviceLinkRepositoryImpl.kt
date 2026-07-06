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
package com.ntsocial.meshlink.core.data.repository

import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.nowMillis
import com.ntsocial.meshlink.core.common.util.safeCatching
import com.ntsocial.meshlink.core.data.datasource.DeviceLinkLocalDataSource
import com.ntsocial.meshlink.core.data.datasource.DeviceLinksJsonDataSource
import com.ntsocial.meshlink.core.database.entity.asEntity
import com.ntsocial.meshlink.core.database.entity.asExternalModel
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.DeviceLink
import com.ntsocial.meshlink.core.model.NetworkDeviceLink
import com.ntsocial.meshlink.core.model.toDeviceLink
import com.ntsocial.meshlink.core.model.util.TimeConstants
import com.ntsocial.meshlink.core.network.DeviceLinksRemoteDataSource
import com.ntsocial.meshlink.core.repository.DeviceLinkRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.concurrent.Volatile

/**
 * Caches the resolved device-links catalog from the Meshtastic API (`/resource/deviceLinks`). The server does all the
 * classification, so the client seeds from a bundled snapshot, refreshes from the network, and filters the cache.
 */
@Single
class DeviceLinkRepositoryImpl(
    private val remoteDataSource: DeviceLinksRemoteDataSource,
    private val localDataSource: DeviceLinkLocalDataSource,
    private val jsonDataSource: DeviceLinksJsonDataSource,
    private val dispatchers: CoroutineDispatchers,
) : DeviceLinkRepository {

    /** Serializes seeding and network refreshes so concurrent collectors do not duplicate writes. */
    private val writeMutex = Mutex()

    /** Single-flights stale-triggered refreshes so concurrent collectors do not start duplicate fetches. */
    private val refreshMutex = Mutex()

    @Volatile private var lastRefreshMillis = 0L

    override suspend fun ensureImported() {
        ensureSeeded()
    }

    override suspend fun reconcile() {
        safeCatching {
            val remoteLinks = remoteDataSource.getDeviceLinks()
            writeMutex.withLock {
                withContext(NonCancellable + dispatchers.io) {
                    if (store(remoteLinks)) {
                        lastRefreshMillis = nowMillis
                    }
                }
            }
        }
            .onFailure { e -> Logger.w(e) { "DeviceLinkRepository: network refresh failed" } }
    }

    override suspend fun getLinksForTarget(platformioTarget: String, regionCode: String): List<DeviceLink> =
        withContext(dispatchers.io) {
            if (platformioTarget.isBlank()) return@withContext emptyList()
            ensureSeeded()
            localDataSource
                .getAll()
                .map { it.asExternalModel() }
                .filter { link -> platformioTarget in link.targets.orEmpty() }
                .filter { link ->
                    val regions = link.regions
                    regions.isNullOrEmpty() || regionCode in regions
                }
                .sortedByDescending { it.isVendor }
        }

    override fun observeAllLinks(): Flow<List<DeviceLink>> = flow {
        ensureSeeded()
        coroutineScope {
            launch { refreshIfStale() }
            emitAll(localDataSource.observeAll().map { entities -> entities.map { it.asExternalModel() } })
        }
    }

    private suspend fun ensureSeeded() {
        if (localDataSource.count() > 0) return
        writeMutex.withLock {
            if (localDataSource.count() == 0) {
                safeCatching { store(jsonDataSource.loadDeviceLinksFromJsonAsset().links) }
                    .onFailure { e -> Logger.w(e) { "DeviceLinkRepository: failed to seed from bundled JSON" } }
            }
        }
    }

    private suspend fun refreshIfStale() {
        if (nowMillis - lastRefreshMillis <= CACHE_EXPIRATION_TIME_MS) return
        refreshMutex.withLock {
            if (nowMillis - lastRefreshMillis > CACHE_EXPIRATION_TIME_MS) {
                reconcile()
            }
        }
    }

    private suspend fun store(networkLinks: List<NetworkDeviceLink>): Boolean {
        val links = networkLinks.filter { it.type != NetworkDeviceLink.TYPE_INTERNAL }.map { it.toDeviceLink() }
        if (links.isEmpty()) {
            Logger.w { "DeviceLinkRepository: no device links to store; leaving cache untouched" }
            return false
        }
        localDataSource.upsertAll(links.map { it.asEntity() })
        localDataSource.deleteNotIn(links.map { it.shortCode })
        Logger.i { "DeviceLinkRepository: stored ${links.size} device links" }
        return true
    }

    private companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeConstants.ONE_DAY.inWholeMilliseconds
    }
}
