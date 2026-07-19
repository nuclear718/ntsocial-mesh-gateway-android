/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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

import com.ntsocial.meshlink.core.data.datasource.DeviceLinkLocalDataSource
import com.ntsocial.meshlink.core.data.datasource.DeviceLinksJsonDataSource
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.NetworkDeviceHardware
import com.ntsocial.meshlink.core.model.NetworkDeviceLink
import com.ntsocial.meshlink.core.model.NetworkDeviceLinksResponse
import com.ntsocial.meshlink.core.model.NetworkFirmwareReleases
import com.ntsocial.meshlink.core.network.DeviceLinksRemoteDataSource
import com.ntsocial.meshlink.core.network.service.ApiService
import com.ntsocial.meshlink.core.testing.FakeDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceLinkRepositoryImplTest {

    private class FakeApiService(var response: NetworkDeviceLinksResponse) : ApiService {
        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = error("unused")

        override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = response

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = error("unused")
    }

    private class FakeDeviceLinksJsonDataSource(var response: NetworkDeviceLinksResponse) : DeviceLinksJsonDataSource {
        override fun loadDeviceLinksFromJsonAsset(): NetworkDeviceLinksResponse = response
    }

    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var localDataSource: DeviceLinkLocalDataSource
    private lateinit var api: FakeApiService
    private lateinit var jsonDataSource: FakeDeviceLinksJsonDataSource
    private lateinit var repository: DeviceLinkRepositoryImpl

    private fun link(
        shortCode: String,
        type: String = NetworkDeviceLink.TYPE_VENDOR,
        targets: List<String>? = null,
        regions: List<String>? = null,
    ) = NetworkDeviceLink(
        shortCode = shortCode,
        url = "https://msh.to/$shortCode",
        description = shortCode,
        type = type,
        targets = targets,
        regions = regions,
    )

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        localDataSource = DeviceLinkLocalDataSource(dbProvider, dispatchers)
        api = FakeApiService(NetworkDeviceLinksResponse())
        jsonDataSource = FakeDeviceLinksJsonDataSource(NetworkDeviceLinksResponse())
        repository =
            DeviceLinkRepositoryImpl(
                remoteDataSource = DeviceLinksRemoteDataSource(api, dispatchers),
                localDataSource = localDataSource,
                jsonDataSource = jsonDataSource,
                dispatchers = dispatchers,
            )
    }

    @AfterTest
    fun tearDown() {
        dbProvider.close()
    }

    @Test
    fun seedsFromBundledJsonWhenEmptyAndDropsInternalLinks() = runBlocking {
        jsonDataSource.response =
            NetworkDeviceLinksResponse(
                links =
                listOf(
                    link("rak4631", targets = listOf("rak4631")),
                    link("github", type = NetworkDeviceLink.TYPE_INTERNAL),
                ),
            )

        repository.ensureImported()

        assertEquals(setOf("rak4631"), localDataSource.getAll().map { it.shortCode }.toSet())
    }

    @Test
    fun ensureImportedSeedsOnlyWhenEmpty() = runBlocking {
        jsonDataSource.response =
            NetworkDeviceLinksResponse(links = listOf(link("rak4631", targets = listOf("rak4631"))))
        repository.ensureImported()
        assertEquals(1, localDataSource.count())

        jsonDataSource.response =
            NetworkDeviceLinksResponse(
                links =
                listOf(
                    link("rak4631", targets = listOf("rak4631")),
                    link("heltec-v3", targets = listOf("heltec-v3")),
                ),
            )
        repository.ensureImported()

        assertEquals(1, localDataSource.count())
    }

    @Test
    fun getLinksForTargetFiltersByTargetAndRegionVendorFirst() = runBlocking {
        api.response =
            NetworkDeviceLinksResponse(
                links =
                listOf(
                    link(
                        "rokland-rak4631",
                        type = NetworkDeviceLink.TYPE_MARKETPLACE,
                        targets = listOf("rak4631"),
                        regions = listOf("US"),
                    ),
                    link("rak4631", targets = listOf("rak4631")),
                    link("heltec-v3", targets = listOf("heltec-v3")),
                    link(
                        "de-only",
                        type = NetworkDeviceLink.TYPE_MARKETPLACE,
                        targets = listOf("rak4631"),
                        regions = listOf("DE"),
                    ),
                ),
            )
        repository.reconcile()

        val links = repository.getLinksForTarget("rak4631", regionCode = "US")

        assertEquals(listOf("rak4631", "rokland-rak4631"), links.map { it.shortCode })
        assertTrue(links.first().isVendor)
    }

    @Test
    fun worldwideLinksShowRegardlessOfRegion() = runBlocking {
        api.response =
            NetworkDeviceLinksResponse(
                links =
                listOf(link("ww", type = NetworkDeviceLink.TYPE_MARKETPLACE, targets = listOf("t"), regions = null)),
            )
        repository.reconcile()

        assertEquals(listOf("ww"), repository.getLinksForTarget("t", regionCode = "ZZ").map { it.shortCode })
    }

    @Test
    fun reconcilePrunesShortCodesNoLongerInCatalog() = runBlocking {
        api.response =
            NetworkDeviceLinksResponse(
                links = listOf(link("a", targets = listOf("t")), link("b", targets = listOf("t"))),
            )
        repository.reconcile()
        assertEquals(2, localDataSource.count())

        api.response = NetworkDeviceLinksResponse(links = listOf(link("a", targets = listOf("t"))))
        repository.reconcile()

        assertEquals(setOf("a"), localDataSource.getAll().map { it.shortCode }.toSet())
    }

    @Test
    fun emptyResponseLeavesCacheUntouched() = runBlocking {
        api.response = NetworkDeviceLinksResponse(links = listOf(link("a", targets = listOf("t"))))
        repository.reconcile()
        assertEquals(1, localDataSource.count())

        api.response = NetworkDeviceLinksResponse(links = emptyList())
        repository.reconcile()

        assertEquals(1, localDataSource.count())
    }
}
