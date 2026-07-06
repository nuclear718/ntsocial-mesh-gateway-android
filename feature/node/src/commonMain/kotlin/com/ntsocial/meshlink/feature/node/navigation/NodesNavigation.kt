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
package com.ntsocial.meshlink.feature.node.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.ntsocial.meshlink.core.navigation.ContactsRoute
import com.ntsocial.meshlink.core.navigation.NodeDetailRoute
import com.ntsocial.meshlink.core.navigation.NodesRoute
import com.ntsocial.meshlink.core.navigation.Route
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.air_quality
import com.ntsocial.meshlink.core.resources.device
import com.ntsocial.meshlink.core.resources.environment
import com.ntsocial.meshlink.core.resources.host
import com.ntsocial.meshlink.core.resources.ic_air
import com.ntsocial.meshlink.core.resources.ic_cell_tower
import com.ntsocial.meshlink.core.resources.ic_group
import com.ntsocial.meshlink.core.resources.ic_groups
import com.ntsocial.meshlink.core.resources.ic_light_mode
import com.ntsocial.meshlink.core.resources.ic_location_on
import com.ntsocial.meshlink.core.resources.ic_memory
import com.ntsocial.meshlink.core.resources.ic_perm_scan_wifi
import com.ntsocial.meshlink.core.resources.ic_power
import com.ntsocial.meshlink.core.resources.ic_router
import com.ntsocial.meshlink.core.resources.neighbor_info
import com.ntsocial.meshlink.core.resources.pax
import com.ntsocial.meshlink.core.resources.position_log
import com.ntsocial.meshlink.core.resources.power
import com.ntsocial.meshlink.core.resources.signal
import com.ntsocial.meshlink.core.resources.traceroute
import com.ntsocial.meshlink.core.ui.component.ScrollToTopEvent
import com.ntsocial.meshlink.feature.node.compass.CompassViewModel
import com.ntsocial.meshlink.feature.node.detail.NodeDetailScreen
import com.ntsocial.meshlink.feature.node.detail.NodeDetailViewModel
import com.ntsocial.meshlink.feature.node.metrics.AirQualityMetricsScreen
import com.ntsocial.meshlink.feature.node.metrics.DeviceMetricsScreen
import com.ntsocial.meshlink.feature.node.metrics.EnvironmentMetricsScreen
import com.ntsocial.meshlink.feature.node.metrics.HostMetricsLogScreen
import com.ntsocial.meshlink.feature.node.metrics.MetricsViewModel
import com.ntsocial.meshlink.feature.node.metrics.NeighborInfoLogScreen
import com.ntsocial.meshlink.feature.node.metrics.PaxMetricsScreen
import com.ntsocial.meshlink.feature.node.metrics.PositionLogScreen
import com.ntsocial.meshlink.feature.node.metrics.PowerMetricsScreen
import com.ntsocial.meshlink.feature.node.metrics.SignalMetricsScreen
import com.ntsocial.meshlink.feature.node.metrics.TracerouteLogScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.reflect.KClass

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod")
fun EntryProviderScope<NavKey>.nodesGraph(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent> = MutableSharedFlow(),
    onHandleDeepLink: (com.ntsocial.meshlink.core.common.util.CommonUri, onInvalid: () -> Unit) -> Unit = { _, _ -> },
    onNavigateToConnections: () -> Unit = {},
) {
    entry<NodesRoute.NodesGraph>(metadata = { ListDetailSceneStrategy.listPane() }) {
        AdaptiveNodeListScreen(
            backStack = backStack,
            scrollToTopEvents = scrollToTopEvents,
            onHandleDeepLink = onHandleDeepLink,
            onNavigateToConnections = onNavigateToConnections,
        )
    }

    entry<NodesRoute.Nodes>(metadata = { ListDetailSceneStrategy.listPane() }) {
        AdaptiveNodeListScreen(
            backStack = backStack,
            scrollToTopEvents = scrollToTopEvents,
            onHandleDeepLink = onHandleDeepLink,
            onNavigateToConnections = onNavigateToConnections,
        )
    }

    nodeDetailGraph(backStack, scrollToTopEvents, onHandleDeepLink, onNavigateToConnections)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod")
fun EntryProviderScope<NavKey>.nodeDetailGraph(
    backStack: NavBackStack<NavKey>,
    scrollToTopEvents: Flow<ScrollToTopEvent>,
    onHandleDeepLink: (com.ntsocial.meshlink.core.common.util.CommonUri, onInvalid: () -> Unit) -> Unit = { _, _ -> },
    onNavigateToConnections: () -> Unit = {},
) {
    entry<NodesRoute.NodeDetailGraph>(metadata = { ListDetailSceneStrategy.listPane() }) { args ->
        AdaptiveNodeListScreen(
            backStack = backStack,
            scrollToTopEvents = scrollToTopEvents,
            onHandleDeepLink = onHandleDeepLink,
            onNavigateToConnections = onNavigateToConnections,
        )
    }

    entry<NodesRoute.NodeDetail>(metadata = { ListDetailSceneStrategy.detailPane() }) { args ->
        val nodeDetailViewModel: NodeDetailViewModel = koinViewModel()
        val compassViewModel: CompassViewModel = koinViewModel()
        val destNum = args.destNum ?: 0 // Handle nullable destNum if needed
        NodeDetailScreen(
            nodeId = destNum,
            viewModel = nodeDetailViewModel,
            compassViewModel = compassViewModel,
            navigateToMessages = { key -> backStack.add(ContactsRoute.Messages(key)) },
            onNavigate = { route -> backStack.add(route) },
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
        )
    }

    entry<NodeDetailRoute.TracerouteLog>(metadata = { ListDetailSceneStrategy.extraPane() }) { args ->
        val metricsViewModel = koinViewModel<MetricsViewModel> { parametersOf(args.destNum) }
        metricsViewModel.setNodeId(args.destNum)

        TracerouteLogScreen(
            viewModel = metricsViewModel,
            onNavigateUp = dropUnlessResumed { backStack.removeLastOrNull() },
            onViewOnMap = { requestId, responseLogUuid ->
                backStack.add(
                    NodeDetailRoute.TracerouteMap(
                        destNum = args.destNum,
                        requestId = requestId,
                        logUuid = responseLogUuid,
                    ),
                )
            },
        )
    }

    entry<NodeDetailRoute.TracerouteMap>(metadata = { ListDetailSceneStrategy.extraPane() }) { args ->
        val tracerouteMapScreen = com.ntsocial.meshlink.core.ui.util.LocalTracerouteMapScreenProvider.current
        tracerouteMapScreen(args.destNum, args.requestId, args.logUuid) { backStack.removeLastOrNull() }
    }

    NodeDetailScreen.entries.forEach { routeInfo ->
        when (routeInfo.routeClass) {
            NodeDetailRoute.DeviceMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoute.DeviceMetrics>(backStack, routeInfo) { it.destNum }

            NodeDetailRoute.PositionLog::class ->
                addNodeDetailScreenComposable<NodeDetailRoute.PositionLog>(backStack, routeInfo) { it.destNum }

            NodeDetailRoute.EnvironmentMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoute.EnvironmentMetrics>(backStack, routeInfo) { it.destNum }

            NodeDetailRoute.SignalMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoute.SignalMetrics>(backStack, routeInfo) { it.destNum }

            NodeDetailRoute.PowerMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoute.PowerMetrics>(backStack, routeInfo) { it.destNum }

            NodeDetailRoute.AirQualityMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoute.AirQualityMetrics>(backStack, routeInfo) { it.destNum }

            NodeDetailRoute.HostMetricsLog::class ->
                addNodeDetailScreenComposable<NodeDetailRoute.HostMetricsLog>(backStack, routeInfo) { it.destNum }

            NodeDetailRoute.PaxMetrics::class ->
                addNodeDetailScreenComposable<NodeDetailRoute.PaxMetrics>(backStack, routeInfo) { it.destNum }

            NodeDetailRoute.NeighborInfoLog::class ->
                addNodeDetailScreenComposable<NodeDetailRoute.NeighborInfoLog>(backStack, routeInfo) { it.destNum }

            else -> Unit
        }
    }
}

fun NavKey.isNodeDetailRoute(): Boolean = NodeDetailScreen.entries.any { this::class == it.routeClass }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private inline fun <reified R : Route> EntryProviderScope<NavKey>.addNodeDetailScreenComposable(
    backStack: NavBackStack<NavKey>,
    routeInfo: NodeDetailScreen,
    crossinline getDestNum: (R) -> Int,
) {
    entry<R>(metadata = { ListDetailSceneStrategy.extraPane() }) { args ->
        val destNum = getDestNum(args)
        val metricsViewModel = koinViewModel<MetricsViewModel> { parametersOf(destNum) }
        metricsViewModel.setNodeId(destNum)

        routeInfo.screenComposable(metricsViewModel, dropUnlessResumed { backStack.removeLastOrNull() })
    }
}

/** Expect declaration for the platform-specific traceroute map screen. */
enum class NodeDetailScreen(
    val title: StringResource,
    val routeClass: KClass<out Route>,
    val icon: DrawableResource? = null,
    val screenComposable: @Composable (metricsViewModel: MetricsViewModel, onNavigateUp: () -> Unit) -> Unit,
) {
    DEVICE(
        Res.string.device,
        NodeDetailRoute.DeviceMetrics::class,
        Res.drawable.ic_router,
        { metricsVM, onNavigateUp -> DeviceMetricsScreen(metricsVM, onNavigateUp) },
    ),
    POSITION_LOG(
        Res.string.position_log,
        NodeDetailRoute.PositionLog::class,
        Res.drawable.ic_location_on,
        { metricsVM, onNavigateUp -> PositionLogScreen(metricsVM, onNavigateUp) },
    ),
    ENVIRONMENT(
        Res.string.environment,
        NodeDetailRoute.EnvironmentMetrics::class,
        Res.drawable.ic_light_mode,
        { metricsVM, onNavigateUp -> EnvironmentMetricsScreen(metricsVM, onNavigateUp) },
    ),
    SIGNAL(
        Res.string.signal,
        NodeDetailRoute.SignalMetrics::class,
        Res.drawable.ic_cell_tower,
        { metricsVM, onNavigateUp -> SignalMetricsScreen(metricsVM, onNavigateUp) },
    ),
    TRACEROUTE(
        Res.string.traceroute,
        NodeDetailRoute.TracerouteLog::class,
        Res.drawable.ic_perm_scan_wifi,
        { metricsVM, onNavigateUp -> TracerouteLogScreen(viewModel = metricsVM, onNavigateUp = onNavigateUp) },
    ),
    NEIGHBOR_INFO(
        Res.string.neighbor_info,
        NodeDetailRoute.NeighborInfoLog::class,
        Res.drawable.ic_groups,
        { metricsVM, onNavigateUp -> NeighborInfoLogScreen(viewModel = metricsVM, onNavigateUp = onNavigateUp) },
    ),
    POWER(
        Res.string.power,
        NodeDetailRoute.PowerMetrics::class,
        Res.drawable.ic_power,
        { metricsVM, onNavigateUp -> PowerMetricsScreen(metricsVM, onNavigateUp) },
    ),
    AIR_QUALITY(
        Res.string.air_quality,
        NodeDetailRoute.AirQualityMetrics::class,
        Res.drawable.ic_air,
        { metricsVM, onNavigateUp -> AirQualityMetricsScreen(metricsVM, onNavigateUp) },
    ),
    HOST(
        Res.string.host,
        NodeDetailRoute.HostMetricsLog::class,
        Res.drawable.ic_memory,
        { metricsVM, onNavigateUp -> HostMetricsLogScreen(metricsVM, onNavigateUp) },
    ),
    PAX(
        Res.string.pax,
        NodeDetailRoute.PaxMetrics::class,
        Res.drawable.ic_group,
        { metricsVM, onNavigateUp -> PaxMetricsScreen(metricsVM, onNavigateUp) },
    ),
}
