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
package com.ntsocial.meshlink.feature.settings.di

import com.ntsocial.meshlink.core.ui.viewmodel.EndpointViewModelFactory
import com.ntsocial.meshlink.feature.settings.DeviceLinkDirectoryViewModel
import com.ntsocial.meshlink.feature.settings.SettingsViewModel
import com.ntsocial.meshlink.feature.settings.channel.ChannelViewModel
import com.ntsocial.meshlink.feature.settings.debugging.DebugViewModel
import com.ntsocial.meshlink.feature.settings.filter.FilterSettingsViewModel
import com.ntsocial.meshlink.feature.settings.radio.CleanNodeDatabaseViewModel
import com.ntsocial.meshlink.feature.settings.radio.RadioConfigViewModel

fun EndpointViewModelFactory.registerSettingsEndpointViewModels() {
    register(SettingsViewModel::class) { _, _ ->
        SettingsViewModel(
            radioConfigRepository = get(),
            radioController = get(),
            nodeRepository = get(),
            uiPrefs = get(),
            buildConfigProvider = get(),
            databaseManager = get(),
            meshLogPrefs = get(),
            notificationPrefs = get(),
            setThemeUseCase = get(),
            setLocaleUseCase = get(),
            setAppIntroCompletedUseCase = get(),
            setProvideLocationUseCase = get(),
            setDatabaseCacheLimitUseCase = get(),
            setMeshLogSettingsUseCase = get(),
            setNotificationSettingsUseCase = get(),
            meshLocationUseCase = get(),
            exportDataUseCase = get(),
            isOtaCapableUseCase = get(),
            fileService = get(),
        )
    }
    registerRadioConfiguration()
    register(ChannelViewModel::class) { _, _ ->
        ChannelViewModel(
            radioController = get(),
            radioConfigRepository = get(),
            analytics = get(),
            channelReliabilityManager = get(),
            alertManager = get(),
        )
    }
    register(CleanNodeDatabaseViewModel::class) { _, _ ->
        CleanNodeDatabaseViewModel(cleanNodeDatabaseUseCase = get(), alertManager = get())
    }
    register(DebugViewModel::class) { _, _ ->
        DebugViewModel(
            meshLogRepository = get(),
            nodeRepository = get(),
            meshLogPrefs = get(),
            alertManager = get(),
            dispatchers = get(),
        )
    }
    register(DeviceLinkDirectoryViewModel::class) { _, _ -> DeviceLinkDirectoryViewModel(deviceLinkRepository = get()) }
    register(FilterSettingsViewModel::class) { _, _ ->
        FilterSettingsViewModel(filterPrefs = get(), messageFilter = get())
    }
}

private fun EndpointViewModelFactory.registerRadioConfiguration() {
    register(RadioConfigViewModel::class) { state, _ ->
        RadioConfigViewModel(
            savedStateHandle = state,
            radioConfigRepository = get(),
            packetRepository = get(),
            serviceRepository = get(),
            nodeRepository = get(),
            locationRepository = get(),
            mapConsentPrefs = get(),
            homoglyphEncodingPrefs = get(),
            toggleHomoglyphEncodingUseCase = get(),
            importProfileUseCase = get(),
            exportProfileUseCase = get(),
            exportSecurityConfigUseCase = get(),
            installProfileUseCase = get(),
            radioConfigUseCase = get(),
            adminActionsUseCase = get(),
            processRadioResponseUseCase = get(),
            locationService = get(),
            fileService = get(),
            mqttManager = get(),
            channelReliabilityManager = get(),
            alertManager = get(),
        )
    }
}
