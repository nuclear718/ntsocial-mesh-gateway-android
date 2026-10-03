/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import Foundation

/// Run with Tools/run_terms_consent_tests.sh; tests the production Foundation store without a radio or KMP.
@main
enum TermsConsentTests {
    static func main() throws {
        let suiteName = "com.ntsocial.meshlink.tests.terms.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = TermsConsentStore(defaults: defaults)

        try require(!store.hasAcceptedCurrentTerms, "Fresh installation must require consent")
        try require(!store.accept(language: .english, acknowledged: false), "Unchecked consent must be refused")
        try require(defaults.object(forKey: TermsConsentStore.acceptanceKey) == nil, "Refusal must not write consent")

        for language in TermsLanguage.allCases {
            try require(store.accept(language: language, acknowledged: true), "Explicit consent must be saved")
            let record = defaults.dictionary(forKey: TermsConsentStore.acceptanceKey)!
            try require(record["version"] as? String == "2026-10-02", "Record must capture the presented version")
            try require(record["language"] as? String == language.rawValue, "Record must capture the chosen language")
            try require(record["acceptedAt"] is Date, "Record must capture acceptance time")
            try require(
                TermsConsentStore(defaults: UserDefaults(suiteName: suiteName)!).hasAcceptedCurrentTerms,
                "Consent must survive store recreation"
            )
        }

        let validRecord: [String: Any] = [
            "version": TermsConsentStore.currentVersion,
            "language": TermsLanguage.english.rawValue,
            "acceptedAt": Date(timeIntervalSinceNow: -60),
        ]
        for field in ["version", "language", "acceptedAt"] {
            var incomplete = validRecord
            incomplete.removeValue(forKey: field)
            defaults.set(incomplete, forKey: TermsConsentStore.acceptanceKey)
            try require(!store.hasAcceptedCurrentTerms, "Missing \(field) must fail closed")
        }

        for invalidVersion in ["2026-09-01", "", "2099-01-01"] {
            var outdated = validRecord
            outdated["version"] = invalidVersion
            defaults.set(outdated, forKey: TermsConsentStore.acceptanceKey)
            try require(!store.hasAcceptedCurrentTerms, "Different versions must require renewed consent")
        }
        for invalidLanguage in ["", "de", "zh-TW"] {
            var malformed = validRecord
            malformed["language"] = invalidLanguage
            defaults.set(malformed, forKey: TermsConsentStore.acceptanceKey)
            try require(!store.hasAcceptedCurrentTerms, "Unknown record language must fail closed")
        }
        let invalidDates: [Any] = ["2026-10-02", true, Date(timeIntervalSince1970: 0), Date.distantFuture]
        for invalidDate in invalidDates {
            var malformed = validRecord
            malformed["acceptedAt"] = invalidDate
            defaults.set(malformed, forKey: TermsConsentStore.acceptanceKey)
            try require(!store.hasAcceptedCurrentTerms, "Invalid acceptance time must fail closed")
        }
        defaults.set(true, forKey: TermsConsentStore.acceptanceKey)
        try require(!store.hasAcceptedCurrentTerms, "Legacy Boolean must not count as explicit versioned consent")

        let languageCases: [([String], TermsLanguage)] = [
            (["zh-TW"], .traditionalChinese), (["zh_Hant_TW"], .traditionalChinese),
            (["zh-Hans"], .traditionalChinese), (["ja-JP"], .japanese),
            (["en-US"], .english), (["de", "ja"], .japanese), (["de"], .english), ([], .english),
        ]
        for (tags, expected) in languageCases {
            try require(TermsLanguage.preferred(from: tags) == expected, "Language fallback failed for \(tags)")
        }
        defaults.set(["ja-JP"], forKey: "AppleLanguages")
        try require(store.suggestedLanguage == .japanese, "App language must take priority over device language")
        try require(TermsLanguage.english.websiteAnchor == "english", "English website anchor")
        try require(TermsLanguage.traditionalChinese.websiteAnchor == "traditional-chinese", "Chinese website anchor")
        try require(TermsLanguage.japanese.websiteAnchor == "japanese", "Japanese website anchor")
        print("TermsConsentTests passed")
    }

    private static func require(_ condition: Bool, _ message: String) throws {
        if !condition { throw TestFailure(message: message) }
    }

    private struct TestFailure: Error, CustomStringConvertible {
        let message: String
        var description: String { message }
    }
}
