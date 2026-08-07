/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import Foundation
import MeshLinkKit
import Security
import UIKit

private let appleGatewayCommandCallback: CFNotificationCallback = { _, _, _, _, _ in
    DispatchQueue.main.async {
        MeshLinkRuntime.shared.processGatewayCommands()
    }
}

enum AppleGatewayBootstrap {
    private static let appGroupIdentifierInfoKey = "NTSocialMeshLinkAppGroupIdentifier"
    private static let keychainService = "com.ntsocial.meshlink.gateway.hmac"
    private static let keychainAccount = "apple-gateway-v1"
    private static let keychainGroupInfoKey = "NTsocialGatewayKeychainAccessGroup"
    private static let keyLength = 32
    private static let commandAvailableNotification = "com.ntsocial.meshlink.gateway.command-available"
    private static var observerInstalled = false
    private static var protectedDataObserver: NSObjectProtocol?
    private static var runtimeConfigured = false

    /// Configures the Kotlin Gateway owner without logging or persisting the shared secret outside Keychain.
    static func configureRuntime() {
        if runtimeConfigured {
            installCommandObserverIfNeeded()
            MeshLinkRuntime.shared.processGatewayCommands()
            return
        }

        guard
            let appGroupIdentifier = resolvedAppGroupIdentifier(),
            let container = FileManager.default.containerURL(
                forSecurityApplicationGroupIdentifier: appGroupIdentifier
            ),
            let accessGroup = resolvedKeychainAccessGroup(),
            let key = loadOrCreateHmacKey(accessGroup: accessGroup)
        else {
            MeshLinkRuntime.shared.clearGatewayConfiguration()
            installProtectedDataObserverIfNeeded()
            return
        }

        MeshLinkRuntime.shared.configureGateway(
            sharedContainerPath: container.path,
            hmacKeyBase64: key.base64EncodedString()
        )
        runtimeConfigured = true
        removeProtectedDataObserver()
        installCommandObserverIfNeeded()
        MeshLinkRuntime.shared.processGatewayCommands()
    }

    /// Re-attempts a fail-closed bootstrap when the App becomes active or protected data becomes available.
    static func retryConfigurationIfNeeded() {
        configureRuntime()
    }

    private static func resolvedAppGroupIdentifier() -> String? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: appGroupIdentifierInfoKey) as? String else {
            return nil
        }
        let value = group.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, !value.contains("$(") else { return nil }
        return value
    }

    private static func installCommandObserverIfNeeded() {
        guard !observerInstalled else { return }
        observerInstalled = true
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            nil,
            appleGatewayCommandCallback,
            commandAvailableNotification as CFString,
            nil,
            .deliverImmediately
        )
    }

    private static func installProtectedDataObserverIfNeeded() {
        guard protectedDataObserver == nil else { return }
        protectedDataObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.protectedDataDidBecomeAvailableNotification,
            object: nil,
            queue: .main
        ) { _ in
            AppleGatewayBootstrap.configureRuntime()
        }
    }

    private static func removeProtectedDataObserver() {
        guard let observer = protectedDataObserver else { return }
        NotificationCenter.default.removeObserver(observer)
        protectedDataObserver = nil
    }

    private static func resolvedKeychainAccessGroup() -> String? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: keychainGroupInfoKey) as? String else { return nil }
        let value = group.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, !value.contains("$(") else { return nil }
        return value
    }

    private static func loadOrCreateHmacKey(accessGroup: String) -> Data? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrAccessGroup: accessGroup,
            kSecAttrService: keychainService,
            kSecAttrAccount: keychainAccount,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        let readStatus = SecItemCopyMatching(query as CFDictionary, &result)
        if readStatus == errSecSuccess, let data = result as? Data, data.count == keyLength {
            return data
        }
        guard readStatus == errSecItemNotFound else { return nil }

        var bytes = [UInt8](repeating: 0, count: keyLength)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { return nil }
        let data = Data(bytes)
        let add: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrAccessGroup: accessGroup,
            kSecAttrService: keychainService,
            kSecAttrAccount: keychainAccount,
            kSecAttrAccessible: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData: data,
        ]
        let addStatus = SecItemAdd(add as CFDictionary, nil)
        if addStatus == errSecSuccess { return data }
        if addStatus == errSecDuplicateItem {
            var duplicate: CFTypeRef?
            guard SecItemCopyMatching(query as CFDictionary, &duplicate) == errSecSuccess else { return nil }
            return (duplicate as? Data).flatMap { $0.count == keyLength ? $0 : nil }
        }
        return nil
    }
}
