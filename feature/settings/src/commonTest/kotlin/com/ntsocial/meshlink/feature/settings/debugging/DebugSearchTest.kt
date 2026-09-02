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
package com.ntsocial.meshlink.feature.settings.debugging

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.debug_active_filters
import com.ntsocial.meshlink.core.resources.debug_default_search
import com.ntsocial.meshlink.core.resources.debug_filter_add
import com.ntsocial.meshlink.core.resources.debug_filter_add_custom
import com.ntsocial.meshlink.core.resources.debug_filter_clear
import com.ntsocial.meshlink.core.resources.debug_filters
import com.ntsocial.meshlink.core.resources.debug_search_clear
import com.ntsocial.meshlink.core.resources.debug_search_next
import com.ntsocial.meshlink.core.resources.debug_search_prev
import com.ntsocial.meshlink.core.resources.getString
import com.ntsocial.meshlink.feature.settings.debugging.DebugViewModel.UiMeshLog
import com.ntsocial.meshlink.feature.settings.debugging.LogSearchManager.SearchMatch
import com.ntsocial.meshlink.feature.settings.debugging.LogSearchManager.SearchState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DebugSearchTest {

    @Test
    fun debugSearchBar_showsPlaceholder() = runComposeUiTest {
        val placeholder = getString(Res.string.debug_default_search)
        setContent {
            DebugSearchBar(
                searchState = SearchState(),
                onSearchTextChange = {},
                onNextMatch = {},
                onPreviousMatch = {},
                onClearSearch = {},
            )
        }
        onNodeWithText(placeholder).assertIsDisplayed()
    }

    @Test
    fun debugSearchBar_showsClearButtonWhenTextEntered() = runComposeUiTest {
        val placeholder = getString(Res.string.debug_default_search)
        val clearSearch = getString(Res.string.debug_search_clear)
        setContent {
            var searchText by remember { mutableStateOf("test") }
            DebugSearchBar(
                searchState = SearchState(searchText = searchText),
                onSearchTextChange = { searchText = it },
                onNextMatch = {},
                onPreviousMatch = {},
                onClearSearch = { searchText = "" },
            )
        }
        onNodeWithContentDescription(clearSearch).assertIsDisplayed().performClick()
        onNodeWithText(placeholder).assertIsDisplayed()
    }

    @Test
    fun debugSearchBar_searchFor_showsArrowsClearAndValues() = runComposeUiTest {
        val searchText = "test"
        val matchCount = 3
        val currentMatchIndex = 1
        val previousMatch = getString(Res.string.debug_search_prev)
        val nextMatch = getString(Res.string.debug_search_next)
        val clearSearch = getString(Res.string.debug_search_clear)

        setContent {
            DebugSearchBar(
                searchState =
                SearchState(
                    searchText = searchText,
                    currentMatchIndex = currentMatchIndex,
                    allMatches = List(matchCount) { SearchMatch(it, 0, 6, "Packet") },
                    hasMatches = true,
                ),
                onSearchTextChange = {},
                onNextMatch = {},
                onPreviousMatch = {},
                onClearSearch = {},
            )
        }
        // Check the match count display (e.g., '2/3')
        onNodeWithText("${currentMatchIndex + 1}/$matchCount").assertIsDisplayed()
        // Check the navigation arrows
        onNodeWithContentDescription(previousMatch).assertIsDisplayed()
        onNodeWithContentDescription(nextMatch).assertIsDisplayed()
        // Check the clear button
        onNodeWithContentDescription(clearSearch).assertIsDisplayed()
    }

    @Test
    fun debugFilterBar_showsFilterButtonAndMenu() = runComposeUiTest {
        val filterLabel = getString(Res.string.debug_filters)
        setContent {
            var filterTexts by remember { mutableStateOf(listOf<String>()) }
            var customFilterText by remember { mutableStateOf("") }
            val presetFilters = listOf("Error", "Warning", "Info")
            val logs =
                listOf(
                    UiMeshLog(
                        uuid = "1",
                        messageType = "Info",
                        formattedReceivedDate = "2024-01-01 12:00:00",
                        logMessage = "Sample log message",
                    ),
                )
            DebugFilterBar(
                filterTexts = filterTexts,
                onFilterTextsChange = { filterTexts = it },
                customFilterText = customFilterText,
                onCustomFilterTextChange = { customFilterText = it },
                presetFilters = presetFilters,
                logs = logs,
            )
        }
        // The filter button should be visible
        onNodeWithText(filterLabel).assertIsDisplayed()
    }

    @Test
    fun debugFilterBar_addCustomFilter_displaysActiveFilter() = runComposeUiTest {
        val activeFiltersLabel = getString(Res.string.debug_active_filters)
        val addCustomFilter = getString(Res.string.debug_filter_add_custom)
        val addFilter = getString(Res.string.debug_filter_add)
        setContent {
            var filterTexts by remember { mutableStateOf(listOf<String>()) }
            var customFilterText by remember { mutableStateOf("") }
            Column(modifier = Modifier.padding(16.dp)) {
                DebugActiveFilters(
                    filterTexts = filterTexts,
                    onFilterTextsChange = { filterTexts = it },
                    filterMode = FilterMode.OR,
                    onFilterModeChange = {},
                )
                DebugCustomFilterInput(
                    customFilterText = customFilterText,
                    onCustomFilterTextChange = { customFilterText = it },
                    filterTexts = filterTexts,
                    onFilterTextsChange = { filterTexts = it },
                )
            }
        }
        onNodeWithText(addCustomFilter).performTextInput("MyFilter")
        onNodeWithContentDescription(addFilter).performClick()
        onNodeWithText(activeFiltersLabel).assertIsDisplayed()
        onNodeWithText("MyFilter").assertIsDisplayed()
    }

    @Test
    fun debugActiveFilters_clearAllFilters_removesFilters() = runComposeUiTest {
        val activeFiltersLabel = getString(Res.string.debug_active_filters)
        val clearAllFilters = getString(Res.string.debug_filter_clear)
        setContent {
            var filterTexts by remember { mutableStateOf(listOf("A", "B")) }
            DebugActiveFilters(
                filterTexts = filterTexts,
                onFilterTextsChange = { filterTexts = it },
                filterMode = FilterMode.OR,
                onFilterModeChange = {},
            )
        }
        // The active filters label and chips should be visible
        onNodeWithText(activeFiltersLabel).assertIsDisplayed()
        onNodeWithText("A").assertIsDisplayed()
        onNodeWithText("B").assertIsDisplayed()
        // Click the clear all filters button
        onNodeWithContentDescription(clearAllFilters).performClick()
        // The filter chips should no longer be visible
        onNodeWithText("A").assertDoesNotExist()
        onNodeWithText("B").assertDoesNotExist()
    }
}
