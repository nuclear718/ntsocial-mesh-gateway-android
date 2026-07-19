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
package com.ntsocial.meshlink.core.model

import com.ntsocial.meshlink.core.resources.Res
import com.ntsocial.meshlink.core.resources.tak_role_forwardobserver
import com.ntsocial.meshlink.core.resources.tak_role_hq
import com.ntsocial.meshlink.core.resources.tak_role_k9
import com.ntsocial.meshlink.core.resources.tak_role_medic
import com.ntsocial.meshlink.core.resources.tak_role_rto
import com.ntsocial.meshlink.core.resources.tak_role_sniper
import com.ntsocial.meshlink.core.resources.tak_role_teamlead
import com.ntsocial.meshlink.core.resources.tak_role_teammember
import com.ntsocial.meshlink.core.resources.tak_role_unspecified
import com.ntsocial.meshlink.core.resources.tak_team_blue
import com.ntsocial.meshlink.core.resources.tak_team_brown
import com.ntsocial.meshlink.core.resources.tak_team_cyan
import com.ntsocial.meshlink.core.resources.tak_team_dark_blue
import com.ntsocial.meshlink.core.resources.tak_team_dark_green
import com.ntsocial.meshlink.core.resources.tak_team_green
import com.ntsocial.meshlink.core.resources.tak_team_magenta
import com.ntsocial.meshlink.core.resources.tak_team_maroon
import com.ntsocial.meshlink.core.resources.tak_team_orange
import com.ntsocial.meshlink.core.resources.tak_team_purple
import com.ntsocial.meshlink.core.resources.tak_team_red
import com.ntsocial.meshlink.core.resources.tak_team_teal
import com.ntsocial.meshlink.core.resources.tak_team_unspecified_color
import com.ntsocial.meshlink.core.resources.tak_team_white
import com.ntsocial.meshlink.core.resources.tak_team_yellow
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.Team

@Suppress("CyclomaticComplexMethod")
fun getStringResFrom(team: Team): StringResource = when (team) {
    Team.Unspecifed_Color -> Res.string.tak_team_unspecified_color
    Team.White -> Res.string.tak_team_white
    Team.Yellow -> Res.string.tak_team_yellow
    Team.Orange -> Res.string.tak_team_orange
    Team.Magenta -> Res.string.tak_team_magenta
    Team.Red -> Res.string.tak_team_red
    Team.Maroon -> Res.string.tak_team_maroon
    Team.Purple -> Res.string.tak_team_purple
    Team.Dark_Blue -> Res.string.tak_team_dark_blue
    Team.Blue -> Res.string.tak_team_blue
    Team.Cyan -> Res.string.tak_team_cyan
    Team.Teal -> Res.string.tak_team_teal
    Team.Green -> Res.string.tak_team_green
    Team.Dark_Green -> Res.string.tak_team_dark_green
    Team.Brown -> Res.string.tak_team_brown
}

fun getStringResFrom(role: MemberRole): StringResource = when (role) {
    MemberRole.Unspecifed -> Res.string.tak_role_unspecified
    MemberRole.TeamMember -> Res.string.tak_role_teammember
    MemberRole.TeamLead -> Res.string.tak_role_teamlead
    MemberRole.HQ -> Res.string.tak_role_hq
    MemberRole.Sniper -> Res.string.tak_role_sniper
    MemberRole.Medic -> Res.string.tak_role_medic
    MemberRole.ForwardObserver -> Res.string.tak_role_forwardobserver
    MemberRole.RTO -> Res.string.tak_role_rto
    MemberRole.K9 -> Res.string.tak_role_k9
}

@Suppress("CyclomaticComplexMethod", "MagicNumber")
fun getColorFrom(team: Team): Long = when (team) {
    Team.Unspecifed_Color -> 0xFF00FFFF

    // Default to Cyan
    Team.White -> 0xFFFFFFFF

    Team.Yellow -> 0xFFFFFF00

    Team.Orange -> 0xFFFFA500

    Team.Magenta -> 0xFFFF00FF

    Team.Red -> 0xFFFF0000

    Team.Maroon -> 0xFF800000

    Team.Purple -> 0xFF800080

    Team.Dark_Blue -> 0xFF00008B

    Team.Blue -> 0xFF0000FF

    Team.Cyan -> 0xFF00FFFF

    Team.Teal -> 0xFF008080

    Team.Green -> 0xFF00FF00

    Team.Dark_Green -> 0xFF006400

    Team.Brown -> 0xFFA52A2A
}
