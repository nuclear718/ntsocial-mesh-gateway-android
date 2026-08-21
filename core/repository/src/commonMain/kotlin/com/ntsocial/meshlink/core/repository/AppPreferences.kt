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
package com.ntsocial.meshlink.core.repository

import kotlinx.coroutines.flow.StateFlow

/** Reactive interface for homoglyph encoding preferences. */
interface HomoglyphPrefs {
    val homoglyphEncodingEnabled: StateFlow<Boolean>

    fun setHomoglyphEncodingEnabled(enabled: Boolean)
}

/** Reactive interface for message filtering preferences. */
interface FilterPrefs {
    val filterEnabled: StateFlow<Boolean>

    fun setFilterEnabled(enabled: Boolean)

    val filterWords: StateFlow<Set<String>>

    fun setFilterWords(words: Set<String>)
}

/** Reactive interface for mesh log preferences. */
interface MeshLogPrefs {
    val retentionDays: StateFlow<Int>

    fun setRetentionDays(days: Int)

    val loggingEnabled: StateFlow<Boolean>

    fun setLoggingEnabled(enabled: Boolean)

    companion object {
        const val DEFAULT_RETENTION_DAYS = 30
        const val MIN_RETENTION_DAYS = -1
        const val MAX_RETENTION_DAYS = 365
    }
}

/** Reactive interface for emoji preferences. */
interface CustomEmojiPrefs {
    val customEmojiFrequency: StateFlow<String?>

    fun setCustomEmojiFrequency(frequency: String?)
}

/** Reactive interface for general UI preferences. */
@Suppress("TooManyFunctions")
interface UiPrefs {
    /**
     * Authoritative launch preferences. A null value means DataStore has not completed its first read yet, so hosts
     * must not infer that a returning user is on a fresh install from the individual flows' fallback values.
     */
    val appLaunchPreferences: StateFlow<AppLaunchPreferences?>

    val appIntroCompleted: StateFlow<Boolean>

    fun setAppIntroCompleted(completed: Boolean)

    val theme: StateFlow<Int>

    fun setTheme(value: Int)

    val locale: StateFlow<String>

    fun setLocale(languageTag: String)

    /** Persists a locale before a host applies it and allows Android to recreate the current Activity. */
    suspend fun setLocaleAndAwait(languageTag: String) {
        setLocale(languageTag)
    }

    val nodeSort: StateFlow<Int>

    fun setNodeSort(value: Int)

    val includeUnknown: StateFlow<Boolean>

    fun setIncludeUnknown(value: Boolean)

    val excludeInfrastructure: StateFlow<Boolean>

    fun setExcludeInfrastructure(value: Boolean)

    val onlyOnline: StateFlow<Boolean>

    fun setOnlyOnline(value: Boolean)

    val onlyDirect: StateFlow<Boolean>

    fun setOnlyDirect(value: Boolean)

    val showIgnored: StateFlow<Boolean>

    fun setShowIgnored(value: Boolean)

    val excludeMqtt: StateFlow<Boolean>

    fun setExcludeMqtt(value: Boolean)

    val hasShownNotPairedWarning: StateFlow<Boolean>

    fun setHasShownNotPairedWarning(shown: Boolean)

    val showQuickChat: StateFlow<Boolean>

    fun setShowQuickChat(show: Boolean)

    /** Whether BLE scanning should auto-start when the Connections screen is opened. */
    val bleAutoScan: StateFlow<Boolean>

    fun setBleAutoScan(enabled: Boolean)

    /** Whether NSD network scanning should auto-start when the Connections screen is opened. */
    val networkAutoScan: StateFlow<Boolean>

    fun setNetworkAutoScan(enabled: Boolean)

    /** Whether the BLE transport section is visible in the Connections device list. */
    val showBleTransport: StateFlow<Boolean>

    fun setShowBleTransport(enabled: Boolean)

    /** Whether the network (TCP/NSD) transport section is visible in the Connections device list. */
    val showNetworkTransport: StateFlow<Boolean>

    fun setShowNetworkTransport(enabled: Boolean)

    /** Whether the USB transport section is visible in the Connections device list. */
    val showUsbTransport: StateFlow<Boolean>

    fun setShowUsbTransport(enabled: Boolean)

    fun shouldProvideNodeLocation(nodeNum: Int): StateFlow<Boolean>

    /** Legacy compatibility setter. It may revoke sharing but must never enable a feed without verified admission. */
    fun setShouldProvideNodeLocation(nodeNum: Int, provide: Boolean)

    /** Selected radio channel index for exact phone-location sharing, or `-1` when none has been selected. */
    fun preciseLocationChannelIndex(nodeNum: Int): StateFlow<Int>

    /** One atomic preference snapshot used to admit exact phone-location forwarding. */
    fun preciseLocationAdmission(nodeNum: Int): StateFlow<PreciseLocationAdmission>

    /** Reads persisted admission authoritatively instead of trusting a StateFlow's cold-start initial value. */
    suspend fun readPreciseLocationAdmission(nodeNum: Int): PreciseLocationAdmission =
        preciseLocationAdmission(nodeNum).value

    /** Legacy preselection setter. Changing only an index must revoke admission and clear its channel identity. */
    fun setPreciseLocationChannelIndex(nodeNum: Int, channelIndex: Int)

    /** Atomically changes exact-location admission, cleanup state, and its selected channel. */
    suspend fun setPreciseLocationSharing(
        nodeNum: Int,
        provide: Boolean,
        channelIndex: Int,
        channelIdentity: String = "",
        cleanupPending: Boolean = false,
    )

    /** Clears cleanup only after a verified all-p0 radio readback; this operation can never restore consent. */
    suspend fun clearPreciseLocationCleanupPending(nodeNum: Int)
}

/** The two preferences needed to choose the Android app's first rendered surface without a cold-start race. */
data class AppLaunchPreferences(val appIntroCompleted: Boolean, val locale: String)

/** Atomic user-consent, radio cleanup, and channel selection state for exact phone-location forwarding. */
data class PreciseLocationAdmission(
    val enabled: Boolean = false,
    val channelIndex: Int = -1,
    val channelIdentity: String = "",
    val cleanupPending: Boolean = false,
)

/** Reactive interface for notification preferences. */
interface NotificationPrefs {
    val messagesEnabled: StateFlow<Boolean>

    fun setMessagesEnabled(enabled: Boolean)

    val nodeEventsEnabled: StateFlow<Boolean>

    fun setNodeEventsEnabled(enabled: Boolean)

    val nodeEventsAutoDisabledForEvent: StateFlow<Boolean>

    fun setNodeEventsAutoDisabledForEvent(disabled: Boolean)

    val lowBatteryEnabled: StateFlow<Boolean>

    fun setLowBatteryEnabled(enabled: Boolean)
}

/** Reactive interface for map consent. */
interface MapConsentPrefs {
    fun shouldReportLocation(nodeNum: Int?): StateFlow<Boolean>

    fun setShouldReportLocation(nodeNum: Int?, report: Boolean)
}

/** Reactive interface for radio settings. */
interface RadioPrefs {
    val devAddr: StateFlow<String?>

    /** Reads the authoritative persisted selection without relying on the eager StateFlow's initial value. */
    suspend fun readPersistedDevAddr(): String? = devAddr.value

    /** The persisted user-visible name of the connected device (e.g. "Meshtastic_1234"). */
    val devName: StateFlow<String?>

    fun setDevAddr(address: String?)

    fun setDevName(name: String?)
}

fun RadioPrefs.isBle() = devAddr.value?.startsWith("x") == true

fun RadioPrefs.isSerial() = devAddr.value?.startsWith("s") == true

fun RadioPrefs.isMock() = devAddr.value?.startsWith("m") == true

fun RadioPrefs.isTcp() = devAddr.value?.startsWith("t") == true

fun RadioPrefs.isNoop() = devAddr.value?.startsWith("n") == true

/** Reactive interface for mesh connection settings. */
interface MeshPrefs {
    val deviceAddress: StateFlow<String?>

    fun setDeviceAddress(address: String?)

    fun getStoreForwardLastRequest(address: String?): StateFlow<Int>

    fun setStoreForwardLastRequest(address: String?, timestamp: Int)
}

/** Reactive interface for TAK server settings. */
interface TakPrefs {
    val isTakServerEnabled: StateFlow<Boolean>

    fun setTakServerEnabled(enabled: Boolean)
}

/** Consolidated interface for all application preferences. */
interface AppPreferences {
    val homoglyph: HomoglyphPrefs
    val filter: FilterPrefs
    val meshLog: MeshLogPrefs
    val emoji: CustomEmojiPrefs
    val ui: UiPrefs
    val mapConsent: MapConsentPrefs
    val radio: RadioPrefs
    val mesh: MeshPrefs
    val tak: TakPrefs
}
