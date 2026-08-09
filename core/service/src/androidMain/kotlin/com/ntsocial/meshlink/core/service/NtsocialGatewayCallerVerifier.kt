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
package com.ntsocial.meshlink.core.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import org.koin.core.annotation.Single
import java.security.MessageDigest

/** A caller whose UID, package ownership, and signing certificate have all been verified. */
internal data class NtsocialGatewayCaller(val uid: Int, val packageName: String)

/** Current signers and the complete trusted signing lineage reported for an installed package. */
internal data class GatewayPackageSigningIdentity(
    val currentSignerDigests: Set<String>,
    val signingHistoryDigests: Set<String> = currentSignerDigests,
)

/**
 * Exact NTsocial client package and signer policy shared by debug and release MeshLink builds.
 *
 * The fixed release, team-debug, and retained development-debug pins remain valid for every MeshLink build. A
 * debuggable MeshLink host may also trust the exact debug client package when both Apps have the same complete nonempty
 * current-signer set. A non-debuggable release host never takes that local same-signer path.
 */
internal object NtsocialGatewayClientTrust {
    const val RELEASE_PACKAGE = "com.ntsocial.android"
    const val DEBUG_PACKAGE = "com.ntsocial.android.debug"

    const val RELEASE_CERTIFICATE_SHA256 = "29EF6EF5F0BE97EF1B8F2B405CEE99643FECFF11B71AC3B54D637EE01D0AE646"
    const val TEAM_DEBUG_CERTIFICATE_SHA256 = "C67E44DEE96374FC9E44FCE30B97CEA190FCD3124B266E05D0A9944D4E74FD61"
    const val DEVELOPMENT_DEBUG_CERTIFICATE_SHA256 = "B578F8445925AEA570F7E916C335172559773D7B6EC92DB0D76355E0E8F3FF8"

    private val pinnedSigners =
        mapOf(
            RELEASE_PACKAGE to setOf(RELEASE_CERTIFICATE_SHA256),
            DEBUG_PACKAGE to setOf(TEAM_DEBUG_CERTIFICATE_SHA256, DEVELOPMENT_DEBUG_CERTIFICATE_SHA256),
        )

    fun isTrusted(
        packageName: String,
        clientSigningIdentity: GatewayPackageSigningIdentity,
        hostSigningIdentity: GatewayPackageSigningIdentity,
        hostIsDebuggable: Boolean,
    ): Boolean {
        val pinnedSignerMatches =
            pinnedSigners[packageName]?.let { approvedPinnedSigners ->
                clientSigningIdentity.signingHistoryDigests.any(approvedPinnedSigners::contains)
            } ?: false
        val debugHostSignerMatches =
            hostIsDebuggable &&
                packageName == DEBUG_PACKAGE &&
                clientSigningIdentity.currentSignerDigests.isNotEmpty() &&
                clientSigningIdentity.currentSignerDigests == hostSigningIdentity.currentSignerDigests
        return pinnedSignerMatches || debugHostSignerMatches
    }
}

/**
 * Certificate-pinned caller authorization for the external NTsocial application.
 *
 * `signature|knownSigner` permissions are declared for Android 12+, but they cannot be the only defence because the
 * release client still supports API 26. This verifier is therefore authoritative for Provider access and for issuing
 * short-lived command capabilities. It never trusts a package name supplied by the caller without confirming that the
 * UID owns it and that its signing certificate satisfies the approved signer policy.
 */
@Single
internal class NtsocialGatewayCallerVerifier(private val context: Context) {
    private val hostSigningIdentity by lazy { signingIdentity(context.packageName) }
    private val hostIsDebuggable: Boolean
        get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    fun trustedCaller(uid: Int, requestedPackage: String? = null): NtsocialGatewayCaller? {
        if (uid == Process.myUid()) return NtsocialGatewayCaller(uid = uid, packageName = context.packageName)

        val packages = context.packageManager.getPackagesForUid(uid)?.asList().orEmpty()
        return packages
            .asSequence()
            .filter { requestedPackage == null || it == requestedPackage }
            .firstNotNullOfOrNull { packageName ->
                packageName.takeIf(::isTrustedPackage)?.let { NtsocialGatewayCaller(uid = uid, packageName = it) }
            }
    }

    fun requireTrustedCaller(uid: Int, requestedPackage: String? = null): NtsocialGatewayCaller =
        trustedCaller(uid, requestedPackage) ?: throw SecurityException("Untrusted NTsocial Gateway caller")

    /** Returns installed allowed client packages for explicit, metadata-only event delivery. */
    fun installedTrustedClientPackages(): Set<String> = buildSet {
        if (isTrustedPackage(NtsocialGatewayClientTrust.RELEASE_PACKAGE)) {
            add(NtsocialGatewayClientTrust.RELEASE_PACKAGE)
        }
        if (isTrustedPackage(NtsocialGatewayClientTrust.DEBUG_PACKAGE)) {
            add(NtsocialGatewayClientTrust.DEBUG_PACKAGE)
        }
    }

    private fun isTrustedPackage(packageName: String): Boolean = NtsocialGatewayClientTrust.isTrusted(
        packageName = packageName,
        clientSigningIdentity = signingIdentity(packageName),
        hostSigningIdentity = hostSigningIdentity,
        hostIsDebuggable = hostIsDebuggable,
    )

    @Suppress("DEPRECATION")
    private fun signingIdentity(packageName: String): GatewayPackageSigningIdentity = try {
        val packageInfo =
            context.packageManager.getPackageInfo(
                packageName,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                },
            )
        packageInfo.signingIdentity()
    } catch (_: PackageManager.NameNotFoundException) {
        GatewayPackageSigningIdentity(emptySet())
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signingIdentity(): GatewayPackageSigningIdentity {
        val identity =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                GatewayPackageSigningIdentity(currentSignerDigests = signatures.toDigestSet())
            } else {
                signingInfo?.let { info ->
                    val current = info.apkContentsSigners.toDigestSet()
                    val history =
                        if (info.hasMultipleSigners()) {
                            current
                        } else {
                            current + info.signingCertificateHistory.toDigestSet()
                        }
                    GatewayPackageSigningIdentity(currentSignerDigests = current, signingHistoryDigests = history)
                } ?: GatewayPackageSigningIdentity(emptySet())
            }
        return identity
    }

    private fun Array<android.content.pm.Signature>?.toDigestSet(): Set<String> =
        this?.mapTo(mutableSetOf()) { signature -> signature.toByteArray().sha256Hex() }.orEmpty()

    private fun ByteArray.sha256Hex(): String = buildString(size * BYTES_TO_HEX_CHARACTERS) {
        MessageDigest.getInstance("SHA-256").digest(this@sha256Hex).forEach { byte ->
            val value = byte.toInt() and BYTE_MASK
            append(HEX_DIGITS[value ushr NIBBLE_BITS])
            append(HEX_DIGITS[value and NIBBLE_MASK])
        }
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val NIBBLE_BITS = 4
        const val NIBBLE_MASK = 0x0F
        const val BYTES_TO_HEX_CHARACTERS = 2
        const val HEX_DIGITS = "0123456789ABCDEF"
    }
}
