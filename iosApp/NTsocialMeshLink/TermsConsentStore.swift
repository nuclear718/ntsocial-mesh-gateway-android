/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import Foundation

enum TermsLanguage: String, CaseIterable, Identifiable {
    case english = "en"
    case traditionalChinese = "zh-Hant"
    case japanese = "ja"

    var id: String { rawValue }

    var websiteAnchor: String {
        switch self {
        case .english: "english"
        case .traditionalChinese: "traditional-chinese"
        case .japanese: "japanese"
        }
    }

    static func preferred(from languageTags: [String]) -> TermsLanguage {
        for tag in languageTags {
            let language = tag.lowercased().replacingOccurrences(of: "_", with: "-")
            if language == "zh" || language.hasPrefix("zh-") { return .traditionalChinese }
            if language == "ja" || language.hasPrefix("ja-") { return .japanese }
            if language == "en" || language.hasPrefix("en-") { return .english }
        }
        return .english
    }
}

/// App-private consent, deliberately separate from parent identity and the shared Gateway mailbox.
struct TermsConsentStore {
    static let currentVersion = "2026-10-02"
    static let acceptanceKey = "com.ntsocial.meshlink.ios.termsAcceptance"

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var hasAcceptedCurrentTerms: Bool {
        guard
            let record = defaults.dictionary(forKey: Self.acceptanceKey),
            record["version"] as? String == Self.currentVersion,
            let language = record["language"] as? String,
            TermsLanguage(rawValue: language) != nil,
            let acceptedAt = record["acceptedAt"] as? Date,
            acceptedAt.timeIntervalSince1970 > 0,
            acceptedAt <= Date()
        else { return false }
        return true
    }

    var suggestedLanguage: TermsLanguage {
        TermsLanguage.preferred(
            from: defaults.stringArray(forKey: "AppleLanguages") ?? Locale.preferredLanguages
        )
    }

    /// Only an explicit, unchecked-by-default acknowledgement may create a consent record.
    @discardableResult
    func accept(language: TermsLanguage, acknowledged: Bool) -> Bool {
        guard acknowledged else { return false }
        let record: [String: Any] = [
            "version": Self.currentVersion, "language": language.rawValue, "acceptedAt": Date(),
        ]
        defaults.set(record, forKey: Self.acceptanceKey)
        return hasAcceptedCurrentTerms
    }
}
