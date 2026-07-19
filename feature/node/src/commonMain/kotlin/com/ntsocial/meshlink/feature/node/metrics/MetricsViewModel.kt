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
package com.ntsocial.meshlink.feature.node.metrics

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.ntsocial.meshlink.core.common.util.CommonUri
import com.ntsocial.meshlink.core.common.util.formatString
import com.ntsocial.meshlink.core.common.util.nowSeconds
import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import com.ntsocial.meshlink.core.model.MeshLog
import com.ntsocial.meshlink.core.model.TelemetryType
import com.ntsocial.meshlink.core.model.util.GeoConstants
import com.ntsocial.meshlink.core.model.util.UnitConversions
import com.ntsocial.meshlink.core.repository.FileService
import com.ntsocial.meshlink.core.repository.MeshLogRepository
import com.ntsocial.meshlink.core.repository.NodeRepository
import com.ntsocial.meshlink.core.repository.ServiceRepository
import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.traceroute
import com.ntsocial.meshlink.core.ui.util.AlertManager
import com.ntsocial.meshlink.core.ui.viewmodel.safeLaunch
import com.ntsocial.meshlink.core.ui.viewmodel.stateInWhileSubscribed
import com.ntsocial.meshlink.feature.node.detail.NodeRequestActions
import com.ntsocial.meshlink.feature.node.domain.usecase.GetNodeDetailsUseCase
import com.ntsocial.meshlink.feature.node.model.MetricsState
import com.ntsocial.meshlink.feature.node.model.TimeFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.ByteString.Companion.decodeBase64
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Telemetry
import kotlin.time.Instant
import org.meshtastic.proto.Paxcount as ProtoPaxcount

/**
 * ViewModel responsible for managing and graphing metrics (telemetry, signal strength, paxcount) for a specific node.
 */
@KoinViewModel
@Suppress("LongParameterList", "TooManyFunctions")
open class MetricsViewModel(
    @InjectedParam val destNum: Int,
    protected val dispatchers: CoroutineDispatchers,
    private val meshLogRepository: MeshLogRepository,
    private val serviceRepository: ServiceRepository,
    private val nodeRepository: NodeRepository,
    private val nodeRequestActions: NodeRequestActions,
    private val alertManager: AlertManager,
    private val getNodeDetailsUseCase: GetNodeDetailsUseCase,
    private val fileService: FileService,
) : ViewModel() {

    private val nodeIdFromRoute: Int?
        get() = destNum

    private val manualNodeId = MutableStateFlow<Int?>(null)
    private val activeNodeId =
        combine(MutableStateFlow(nodeIdFromRoute), manualNodeId) { fromRoute, manual -> manual ?: fromRoute }

    val state: StateFlow<MetricsState> =
        activeNodeId
            .flatMapLatest { nodeId ->
                if (nodeId == null) return@flatMapLatest flowOf(MetricsState.Empty)
                getNodeDetailsUseCase(nodeId).map { it.metricsState }
            }
            .stateInWhileSubscribed(initialValue = MetricsState.Empty)

    private val environmentState: StateFlow<EnvironmentMetricsState> =
        activeNodeId
            .flatMapLatest { nodeId ->
                if (nodeId == null) return@flatMapLatest flowOf(EnvironmentMetricsState())
                getNodeDetailsUseCase(nodeId).map { it.environmentState }
            }
            .stateInWhileSubscribed(initialValue = EnvironmentMetricsState())

    private val _timeFrame = MutableStateFlow(TimeFrame.TWENTY_FOUR_HOURS)

    /** The active time window for filtering graphed data. */
    val timeFrame: StateFlow<TimeFrame> = _timeFrame

    /** Returns the list of time frames that are actually available based on the oldest data point. */
    val availableTimeFrames: StateFlow<List<TimeFrame>> =
        combine(state, environmentState) { currentState, envState ->
            val stateOldest = currentState.oldestTimestampSeconds()
            val envOldest =
                envState.environmentMetrics.minOfOrNull { it.time.toLong() }?.takeIf { it > 0 } ?: nowSeconds
            val oldest = listOfNotNull(stateOldest, envOldest).minOrNull() ?: nowSeconds
            TimeFrame.entries.filter { it.isAvailable(oldest) }
        }
            .stateInWhileSubscribed(TimeFrame.entries)

    fun setTimeFrame(timeFrame: TimeFrame) {
        _timeFrame.value = timeFrame
    }

    /** Exposes filtered and unit-converted environment metrics for the UI. */
    val filteredEnvironmentMetrics: StateFlow<List<Telemetry>> =
        combine(environmentState, _timeFrame, state) { envState, timeFrame, currentState ->
            val threshold = timeFrame.timeThreshold()
            val data = envState.environmentMetrics.filter { it.time.toLong() >= threshold }
            if (currentState.isFahrenheit) {
                data.map { telemetry ->
                    val em = telemetry.environment_metrics ?: return@map telemetry
                    telemetry.copy(
                        environment_metrics =
                        em.copy(
                            temperature = em.temperature?.let { UnitConversions.celsiusToFahrenheit(it) },
                            soil_temperature =
                            em.soil_temperature?.let { UnitConversions.celsiusToFahrenheit(it) },
                            one_wire_temperature =
                            em.one_wire_temperature.map { UnitConversions.celsiusToFahrenheit(it) },
                        ),
                    )
                }
            } else {
                data
            }
        }
            .stateInWhileSubscribed(emptyList())

    /** Exposes graphing data specifically for the filtered environment metrics. */
    val environmentGraphingData: StateFlow<EnvironmentGraphingData> =
        filteredEnvironmentMetrics
            .map { filtered -> EnvironmentMetricsState(filtered).environmentMetricsForGraphing(useFahrenheit = false) }
            .stateInWhileSubscribed(EnvironmentGraphingData(emptyList(), emptyList()))

    /** Exposes filtered and decoded pax metrics for the UI. */
    val filteredPaxMetrics: StateFlow<List<Pair<MeshLog, ProtoPaxcount>>> =
        combine(state, _timeFrame) { currentState, timeFrame ->
            val threshold = timeFrame.timeThreshold()
            currentState.paxMetrics
                .filter { (it.received_date / 1000) >= threshold }
                .mapNotNull { log -> decodePaxFromLog(log)?.let { log to it } }
        }
            .stateInWhileSubscribed(emptyList())

    val lastTraceRouteTime: StateFlow<Long?> = nodeRequestActions.lastTracerouteTime

    val lastRequestNeighborsTime: StateFlow<Long?> =
        combine(nodeRequestActions.lastRequestNeighborTimes, activeNodeId) { map, id -> id?.let { map[it] } }
            .stateInWhileSubscribed(null)

    fun getUser(nodeNum: Int) = nodeRepository.getUser(nodeNum)

    fun deleteLog(uuid: String) =
        safeLaunch(context = dispatchers.io, tag = "deleteLog") { meshLogRepository.deleteLog(uuid) }

    fun clearTracerouteResponse() = serviceRepository.clearTracerouteResponse()

    init {
        Logger.d { "MetricsViewModel created" }
    }

    fun clearPosition() = safeLaunch(context = dispatchers.io, tag = "clearPosition") {
        (manualNodeId.value ?: nodeIdFromRoute)?.let {
            meshLogRepository.deleteLogs(it, PortNum.POSITION_APP.value)
        }
    }

    fun requestPosition() {
        (manualNodeId.value ?: nodeIdFromRoute)?.let {
            nodeRequestActions.requestPosition(viewModelScope, it, state.value.node?.user?.long_name ?: "")
        }
    }

    fun requestTelemetry(type: TelemetryType) {
        (manualNodeId.value ?: nodeIdFromRoute)?.let {
            nodeRequestActions.requestTelemetry(viewModelScope, it, state.value.node?.user?.long_name ?: "", type)
        }
    }

    fun requestTraceroute() {
        (manualNodeId.value ?: nodeIdFromRoute)?.let {
            nodeRequestActions.requestTraceroute(viewModelScope, it, state.value.node?.user?.long_name ?: "")
        }
    }

    fun requestNeighborInfo() {
        (manualNodeId.value ?: nodeIdFromRoute)?.let {
            nodeRequestActions.requestNeighborInfo(viewModelScope, it, state.value.node?.user?.long_name ?: "")
        }
    }

    fun showLogDetail(titleRes: StringResource, annotatedMessage: AnnotatedString) {
        alertManager.showAlert(
            titleRes = titleRes,
            composableMessage = { SelectionContainer { Text(text = annotatedMessage) } },
        )
    }

    fun showTracerouteDetail(annotatedMessage: AnnotatedString) {
        showLogDetail(Res.string.traceroute, annotatedMessage)
    }

    fun setNodeId(id: Int) {
        if (manualNodeId.value != id) {
            manualNodeId.value = id
        }
    }

    override fun onCleared() {
        super.onCleared()
        Logger.d { "MetricsViewModel cleared" }
    }

    // region --- CSV Export ---

    /**
     * Shared CSV export helper. Writes [header] then iterates [rows], converting each item to a CSV line via
     * [rowMapper]. The mapper returns only the data columns; date and time columns are prepended automatically from the
     * epoch-seconds timestamp extracted by [epochSeconds].
     */
    private fun <T> exportCsv(
        uri: CommonUri,
        header: String,
        rows: List<T>,
        epochSeconds: (T) -> Long,
        rowMapper: (T) -> String,
    ) {
        safeLaunch(context = dispatchers.io, tag = "exportCsv") {
            fileService.write(uri) { sink ->
                sink.writeUtf8(header)
                rows.forEach { item ->
                    val dt =
                        Instant.fromEpochSeconds(epochSeconds(item)).toLocalDateTime(TimeZone.currentSystemDefault())
                    sink.writeUtf8("\"${dt.date}\",\"${dt.time}\",${rowMapper(item)}\n")
                }
            }
        }
    }

    fun savePositionCSV(uri: CommonUri, data: List<org.meshtastic.proto.Position>) {
        exportCsv(
            uri = uri,
            header = "\"date\",\"time\",\"latitude\",\"longitude\",\"altitude\",\"satsInView\",\"speed\",\"heading\"\n",
            rows = data,
            epochSeconds = { it.time.toLong() },
        ) { pos ->
            val lat = (pos.latitude_i ?: 0) * GeoConstants.DEG_D
            val lon = (pos.longitude_i ?: 0) * GeoConstants.DEG_D
            val heading = formatString("%.2f", (pos.ground_track ?: 0) * GeoConstants.HEADING_DEG)
            "\"$lat\",\"$lon\",\"${pos.altitude}\",\"${pos.sats_in_view}\",\"${pos.ground_speed}\",\"$heading\""
        }
    }

    fun saveDeviceMetricsCSV(uri: CommonUri, data: List<Telemetry>) {
        exportCsv(
            uri = uri,
            header =
            "\"date\",\"time\",\"batteryLevel\",\"voltage\",\"channelUtilization\"," +
                "\"airUtilTx\",\"uptimeSeconds\"\n",
            rows = data,
            epochSeconds = { it.time.toLong() },
        ) { t ->
            val dm = t.device_metrics
            "\"${dm?.battery_level ?: ""}\",\"${dm?.voltage ?: ""}\"," +
                "\"${dm?.channel_utilization ?: ""}\",\"${dm?.air_util_tx ?: ""}\"," +
                "\"${dm?.uptime_seconds ?: ""}\""
        }
    }

    fun saveEnvironmentMetricsCSV(uri: CommonUri, data: List<Telemetry>) {
        val oneWireHeaders = (1..ONE_WIRE_SENSOR_COUNT).joinToString(",") { "\"oneWireTemp$it\"" }
        exportCsv(
            uri = uri,
            header =
            "\"date\",\"time\",\"temperature\",\"relativeHumidity\",\"barometricPressure\"," +
                "\"gasResistance\",\"iaq\",\"windSpeed\",\"windDirection\",\"soilTemperature\"," +
                "\"soilMoisture\",$oneWireHeaders\n",
            rows = data,
            epochSeconds = { it.time.toLong() },
        ) { t ->
            val em = t.environment_metrics
            val owt = em?.one_wire_temperature ?: emptyList()
            val oneWireValues =
                (0 until ONE_WIRE_SENSOR_COUNT).joinToString(",") { i -> "\"${owt.getOrNull(i) ?: ""}\"" }
            "\"${em?.temperature ?: ""}\",\"${em?.relative_humidity ?: ""}\"," +
                "\"${em?.barometric_pressure ?: ""}\",\"${em?.gas_resistance ?: ""}\"," +
                "\"${em?.iaq ?: ""}\",\"${em?.wind_speed ?: ""}\"," +
                "\"${em?.wind_direction ?: ""}\",\"${em?.soil_temperature ?: ""}\"," +
                "\"${em?.soil_moisture ?: ""}\",$oneWireValues"
        }
    }

    fun saveSignalMetricsCSV(uri: CommonUri, data: List<MeshPacket>) {
        exportCsv(
            uri = uri,
            header = "\"date\",\"time\",\"rssi\",\"snr\"\n",
            rows = data,
            epochSeconds = { it.rx_time.toLong() },
        ) { p ->
            "\"${p.rx_rssi}\",\"${p.rx_snr}\""
        }
    }

    fun savePowerMetricsCSV(uri: CommonUri, data: List<Telemetry>) {
        exportCsv(
            uri = uri,
            header =
            "\"date\",\"time\",\"ch1Voltage\",\"ch1Current\",\"ch2Voltage\",\"ch2Current\"," +
                "\"ch3Voltage\",\"ch3Current\"\n",
            rows = data,
            epochSeconds = { it.time.toLong() },
        ) { t ->
            val pm = t.power_metrics
            "\"${pm?.ch1_voltage ?: ""}\",\"${pm?.ch1_current ?: ""}\"," +
                "\"${pm?.ch2_voltage ?: ""}\",\"${pm?.ch2_current ?: ""}\"," +
                "\"${pm?.ch3_voltage ?: ""}\",\"${pm?.ch3_current ?: ""}\""
        }
    }

    fun saveAirQualityMetricsCSV(uri: CommonUri, data: List<Telemetry>) {
        exportCsv(
            uri = uri,
            header = "\"date\",\"time\",\"pm1_0\",\"pm2_5\",\"pm10\",\"co2\"\n",
            rows = data,
            epochSeconds = { it.time.toLong() },
        ) { t ->
            val aq = t.air_quality_metrics
            "\"${aq?.pm10_standard ?: ""}\",\"${aq?.pm25_standard ?: ""}\"," +
                "\"${aq?.pm100_standard ?: ""}\",\"${aq?.co2 ?: ""}\""
        }
    }

    // endregion

    @Suppress("MagicNumber", "CyclomaticComplexMethod", "ReturnCount")
    fun decodePaxFromLog(log: MeshLog): ProtoPaxcount? {
        try {
            val packet = log.fromRadio.packet
            val decoded = packet?.decoded
            if (packet != null && decoded != null && decoded.portnum == PortNum.PAXCOUNTER_APP) {
                if (decoded.want_response == true) return null
                val pax = ProtoPaxcount.ADAPTER.decode(decoded.payload)
                if (pax.ble != 0 || pax.wifi != 0 || pax.uptime != 0) return pax
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to parse Paxcount from binary data" }
        }
        try {
            val base64 = log.raw_message.trim()
            if (base64.matches(Regex("^[A-Za-z0-9+/=\\r\\n]+$"))) {
                val bytes = decodeBase64(base64)
                return ProtoPaxcount.ADAPTER.decode(bytes)
            } else if (base64.matches(Regex("^[0-9a-fA-F]+$")) && base64.length % 2 == 0) {
                val bytes = base64.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                return ProtoPaxcount.ADAPTER.decode(bytes)
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to parse Paxcount from decoded data" }
        }
        return null
    }

    protected fun decodeBase64(base64: String): ByteArray = base64.decodeBase64()?.toByteArray() ?: ByteArray(0)

    companion object {
        private const val ONE_WIRE_SENSOR_COUNT = 8
    }
}
