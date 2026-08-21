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
package com.ntsocial.meshlink.core.testing

import com.ntsocial.meshlink.core.repository.AppLaunchPreferences
import com.ntsocial.meshlink.core.repository.AppPreferences
import com.ntsocial.meshlink.core.repository.CustomEmojiPrefs
import com.ntsocial.meshlink.core.repository.FilterPrefs
import com.ntsocial.meshlink.core.repository.HomoglyphPrefs
import com.ntsocial.meshlink.core.repository.MapConsentPrefs
import com.ntsocial.meshlink.core.repository.MeshPrefs
import com.ntsocial.meshlink.core.repository.PreciseLocationAdmission
import com.ntsocial.meshlink.core.repository.RadioPrefs
import com.ntsocial.meshlink.core.repository.UiPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeHomoglyphPrefs : HomoglyphPrefs {
    override val homoglyphEncodingEnabled = MutableStateFlow(false)

    override fun setHomoglyphEncodingEnabled(enabled: Boolean) {
        homoglyphEncodingEnabled.value = enabled
    }
}

class FakeFilterPrefs : FilterPrefs {
    override val filterEnabled = MutableStateFlow(false)

    override fun setFilterEnabled(enabled: Boolean) {
        filterEnabled.value = enabled
    }

    override val filterWords = MutableStateFlow(emptySet<String>())

    override fun setFilterWords(words: Set<String>) {
        filterWords.value = words
    }
}

class FakeCustomEmojiPrefs : CustomEmojiPrefs {
    override val customEmojiFrequency = MutableStateFlow<String?>(null)

    override fun setCustomEmojiFrequency(frequency: String?) {
        customEmojiFrequency.value = frequency
    }
}

@Suppress("TooManyFunctions")
class FakeUiPrefs : UiPrefs {
    override val appLaunchPreferences =
        MutableStateFlow<AppLaunchPreferences?>(AppLaunchPreferences(appIntroCompleted = false, locale = "en"))

    override val appIntroCompleted = MutableStateFlow(false)

    override fun setAppIntroCompleted(completed: Boolean) {
        appIntroCompleted.value = completed
        appLaunchPreferences.value = appLaunchPreferences.value?.copy(appIntroCompleted = completed)
    }

    override val theme = MutableStateFlow(0)

    override fun setTheme(value: Int) {
        theme.value = value
    }

    override val locale = MutableStateFlow("en")

    override fun setLocale(languageTag: String) {
        locale.value = languageTag
        appLaunchPreferences.value = appLaunchPreferences.value?.copy(locale = languageTag)
    }

    override val nodeSort = MutableStateFlow(0)

    override fun setNodeSort(value: Int) {
        nodeSort.value = value
    }

    override val includeUnknown = MutableStateFlow(true)

    override fun setIncludeUnknown(value: Boolean) {
        includeUnknown.value = value
    }

    override val excludeInfrastructure = MutableStateFlow(false)

    override fun setExcludeInfrastructure(value: Boolean) {
        excludeInfrastructure.value = value
    }

    override val onlyOnline = MutableStateFlow(false)

    override fun setOnlyOnline(value: Boolean) {
        onlyOnline.value = value
    }

    override val onlyDirect = MutableStateFlow(false)

    override fun setOnlyDirect(value: Boolean) {
        onlyDirect.value = value
    }

    override val showIgnored = MutableStateFlow(false)

    override fun setShowIgnored(value: Boolean) {
        showIgnored.value = value
    }

    override val excludeMqtt = MutableStateFlow(false)

    override fun setExcludeMqtt(value: Boolean) {
        excludeMqtt.value = value
    }

    override val hasShownNotPairedWarning = MutableStateFlow(false)

    override fun setHasShownNotPairedWarning(shown: Boolean) {
        hasShownNotPairedWarning.value = shown
    }

    override val showQuickChat = MutableStateFlow(true)

    override fun setShowQuickChat(show: Boolean) {
        showQuickChat.value = show
    }

    override val bleAutoScan = MutableStateFlow(false)

    override fun setBleAutoScan(enabled: Boolean) {
        bleAutoScan.value = enabled
    }

    override val networkAutoScan = MutableStateFlow(false)

    override fun setNetworkAutoScan(enabled: Boolean) {
        networkAutoScan.value = enabled
    }

    override val showBleTransport = MutableStateFlow(true)

    override fun setShowBleTransport(enabled: Boolean) {
        showBleTransport.value = enabled
    }

    override val showNetworkTransport = MutableStateFlow(true)

    override fun setShowNetworkTransport(enabled: Boolean) {
        showNetworkTransport.value = enabled
    }

    override val showUsbTransport = MutableStateFlow(true)

    override fun setShowUsbTransport(enabled: Boolean) {
        showUsbTransport.value = enabled
    }

    private val nodeLocationEnabled = mutableMapOf<Int, MutableStateFlow<Boolean>>()
    private val preciseLocationChannels = mutableMapOf<Int, MutableStateFlow<Int>>()
    private val preciseLocationAdmissions = mutableMapOf<Int, MutableStateFlow<PreciseLocationAdmission>>()

    override fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean> =
        nodeLocationEnabled.getOrPut(nodeNum) { MutableStateFlow(false) }

    @Suppress("UNUSED_PARAMETER")
    override fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean) {
        nodeLocationEnabled.getOrPut(nodeNum) { MutableStateFlow(false) }.value = false
        val admission = preciseLocationAdmissions.getOrPut(nodeNum) { MutableStateFlow(PreciseLocationAdmission()) }
        admission.value =
            admission.value.copy(
                enabled = false,
                cleanupPending = admission.value.enabled || admission.value.cleanupPending,
            )
    }

    override fun preciseLocationChannelIndex(nodeNum: Int): StateFlow<Int> =
        preciseLocationChannels.getOrPut(nodeNum) { MutableStateFlow(-1) }

    override fun setPreciseLocationChannelIndex(nodeNum: Int, channelIndex: Int) {
        preciseLocationChannels.getOrPut(nodeNum) { MutableStateFlow(channelIndex) }.value = channelIndex
        val admission = preciseLocationAdmissions.getOrPut(nodeNum) { MutableStateFlow(PreciseLocationAdmission()) }
        admission.value =
            PreciseLocationAdmission(
                enabled = false,
                channelIndex = channelIndex,
                cleanupPending = admission.value.enabled || admission.value.cleanupPending,
            )
        nodeLocationEnabled.getOrPut(nodeNum) { MutableStateFlow(false) }.value = false
    }

    override fun preciseLocationAdmission(nodeNum: Int): StateFlow<PreciseLocationAdmission> =
        preciseLocationAdmissions.getOrPut(nodeNum) { MutableStateFlow(PreciseLocationAdmission()) }

    override suspend fun readPreciseLocationAdmission(nodeNum: Int): PreciseLocationAdmission =
        preciseLocationAdmission(nodeNum).value

    override suspend fun setPreciseLocationSharing(
        nodeNum: Int,
        provide: Boolean,
        channelIndex: Int,
        channelIdentity: String,
        cleanupPending: Boolean,
    ) {
        preciseLocationAdmissions.getOrPut(nodeNum) { MutableStateFlow(PreciseLocationAdmission()) }.value =
            PreciseLocationAdmission(
                enabled = provide && !cleanupPending,
                channelIndex = channelIndex,
                channelIdentity = channelIdentity,
                cleanupPending = cleanupPending,
            )
        preciseLocationChannels.getOrPut(nodeNum) { MutableStateFlow(channelIndex) }.value = channelIndex
        nodeLocationEnabled.getOrPut(nodeNum) { MutableStateFlow(provide) }.value = provide && !cleanupPending
    }

    override suspend fun clearPreciseLocationCleanupPending(nodeNum: Int) {
        val admission = preciseLocationAdmissions.getOrPut(nodeNum) { MutableStateFlow(PreciseLocationAdmission()) }
        admission.value = admission.value.copy(enabled = false, cleanupPending = false)
        nodeLocationEnabled.getOrPut(nodeNum) { MutableStateFlow(false) }.value = false
    }
}

class FakeMapConsentPrefs : MapConsentPrefs {
    private val consent = mutableMapOf<Int?, MutableStateFlow<Boolean>>()

    override fun shouldReportLocation(nodeNum: Int?): StateFlow<Boolean> =
        consent.getOrPut(nodeNum) { MutableStateFlow(false) }

    override fun setShouldReportLocation(nodeNum: Int?, report: Boolean) {
        consent.getOrPut(nodeNum) { MutableStateFlow(report) }.value = report
    }
}

class FakeRadioPrefs : RadioPrefs {
    override val devAddr = MutableStateFlow<String?>(null)
    override val devName = MutableStateFlow<String?>(null)

    override fun setDevAddr(address: String?) {
        devAddr.value = address
    }

    override fun setDevName(name: String?) {
        devName.value = name
    }
}

class FakeMeshPrefs : MeshPrefs {
    override val deviceAddress = MutableStateFlow<String?>(null)

    override fun setDeviceAddress(address: String?) {
        deviceAddress.value = address
    }

    private val lastRequest = mutableMapOf<String?, MutableStateFlow<Int>>()

    override fun getStoreForwardLastRequest(address: String?): StateFlow<Int> =
        lastRequest.getOrPut(address) { MutableStateFlow(0) }

    override fun setStoreForwardLastRequest(address: String?, timestamp: Int) {
        lastRequest.getOrPut(address) { MutableStateFlow(timestamp) }.value = timestamp
    }
}

class FakeAppPreferences : AppPreferences {
    override val homoglyph = FakeHomoglyphPrefs()
    override val filter = FakeFilterPrefs()
    override val meshLog = FakeMeshLogPrefs()
    override val emoji = FakeCustomEmojiPrefs()
    override val ui = FakeUiPrefs()
    override val mapConsent = FakeMapConsentPrefs()
    override val radio = FakeRadioPrefs()
    override val mesh = FakeMeshPrefs()
    override val tak = FakeTakPrefs()
}

class FakeTakPrefs : com.ntsocial.meshlink.core.repository.TakPrefs {
    override val isTakServerEnabled = MutableStateFlow(false)

    override fun setTakServerEnabled(enabled: Boolean) {
        isTakServerEnabled.value = enabled
    }
}
