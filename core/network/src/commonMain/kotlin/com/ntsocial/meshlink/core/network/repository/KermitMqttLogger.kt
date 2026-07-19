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
package com.ntsocial.meshlink.core.network.repository

import co.touchlab.kermit.Logger
import org.meshtastic.mqtt.MqttLogLevel
import org.meshtastic.mqtt.MqttLogger

/**
 * Adapter that implements [MqttLogger] to send MQTT client logs to Kermit's [Logger].
 *
 * This allows MQTTastic library messages to use the app's local structured logging infrastructure.
 *
 * The library's [tag] (e.g. "MqttClient", "MqttConnection") is forwarded as a structured Kermit tag so developers can
 * filter local diagnostic output by component.
 *
 * Note: The production log level should be set to [MqttLogLevel.WARN] (not INFO) to prevent the library's own
 * INFO-level messages (which include endpoint addresses and topic strings) from reaching remote analytics sinks.
 */
class KermitMqttLogger : MqttLogger {
    override fun log(level: MqttLogLevel, tag: String, message: String, throwable: Throwable?) {
        val logger = Logger.withTag(tag)
        when (level) {
            MqttLogLevel.TRACE -> logger.v(throwable) { message }
            MqttLogLevel.DEBUG -> logger.d(throwable) { message }
            MqttLogLevel.INFO -> logger.i(throwable) { message }
            MqttLogLevel.WARN -> logger.w(throwable) { message }
            MqttLogLevel.ERROR -> logger.e(throwable) { message }
            MqttLogLevel.NONE -> return
        }
    }
}
