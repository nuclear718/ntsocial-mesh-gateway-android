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

/**
 * Certificate-pinned caller authorization for the external NTsocial application.
 *
 * `signature|knownSigner` permissions are declared for Android 12+, but they cannot be the only defence because the
 * release client still supports API 26. This verifier is therefore authoritative for Provider access and for issuing
 * short-lived command capabilities. It never trusts a package name supplied by the caller without confirming that the
 * UID owns it and that its signing certificate matches the pinned digest.
 */
@Single
internal class NtsocialGatewayCallerVerifier(private val context: Context) {
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
        if (isTrustedPackage(RELEASE_PACKAGE)) add(RELEASE_PACKAGE)
        if (isDebugHost && isTrustedPackage(DEBUG_PACKAGE)) add(DEBUG_PACKAGE)
    }

    private val isDebugHost: Boolean
        get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun isTrustedPackage(packageName: String): Boolean = when (packageName) {
        RELEASE_PACKAGE -> hasCertificate(packageName, RELEASE_CERTIFICATE_SHA256)
        DEBUG_PACKAGE -> isDebugHost && hasCertificate(packageName, DEBUG_CERTIFICATE_SHA256)
        else -> false
    }

    @Suppress("DEPRECATION")
    private fun hasCertificate(packageName: String, expectedDigest: String): Boolean = try {
        val packageInfo =
            context.packageManager.getPackageInfo(
                packageName,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    PackageManager.GET_SIGNATURES
                },
            )
        packageInfo.signingCertificateBytes().any { certificateBytes ->
            certificateBytes.sha256Hex() == expectedDigest
        }
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signingCertificateBytes(): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            signatures?.map { it.toByteArray() }.orEmpty()
        }

    private fun ByteArray.sha256Hex(): String = buildString(size * BYTES_TO_HEX_CHARACTERS) {
        MessageDigest.getInstance("SHA-256").digest(this@sha256Hex).forEach { byte ->
            val value = byte.toInt() and BYTE_MASK
            append(HEX_DIGITS[value ushr NIBBLE_BITS])
            append(HEX_DIGITS[value and NIBBLE_MASK])
        }
    }

    private companion object {
        const val RELEASE_PACKAGE = "com.ntsocial.android"
        const val RELEASE_CERTIFICATE_SHA256 = "29EF6EF5F0BE97EF1B8F2B405CEE99643FECFF11B71AC3B54D637EE01D0AE646"

        const val DEBUG_PACKAGE = "com.ntsocial.android.debug"
        const val DEBUG_CERTIFICATE_SHA256 = "B578F8445925AEA570F7E916C335172559773D7B6EC92DB0D76355E0E8F3FF8D"

        const val BYTE_MASK = 0xFF
        const val NIBBLE_BITS = 4
        const val NIBBLE_MASK = 0x0F
        const val BYTES_TO_HEX_CHARACTERS = 2
        const val HEX_DIGITS = "0123456789ABCDEF"
    }
}
