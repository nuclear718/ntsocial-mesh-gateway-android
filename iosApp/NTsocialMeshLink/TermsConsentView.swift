/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import SwiftUI

/// The first interactive screen. No Compose/radio/Gateway owner is created behind this view.
struct TermsConsentView: View {
    let store: TermsConsentStore
    let onAccepted: () -> Void

    @State private var language: TermsLanguage
    @State private var acknowledged = false
    @State private var declined = false
    @State private var saveFailed = false

    init(store: TermsConsentStore, onAccepted: @escaping () -> Void) {
        self.store = store
        self.onAccepted = onAccepted
        _language = State(initialValue: store.suggestedLanguage)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("NTsocial MeshLink")
                    .font(.title2.bold())
                Picker(text("language"), selection: $language) {
                    ForEach(TermsLanguage.allCases) { choice in
                        Text(localized("language_name", language: choice)).tag(choice)
                    }
                }
                .pickerStyle(.menu)
                .accessibilityIdentifier("terms.language")

                Group {
                    Text(text("title"))
                        .font(.largeTitle.bold())
                        .accessibilityAddTraits(.isHeader)
                    Text(text("version") + " " + TermsConsentStore.currentVersion)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text(text("intro"))
                    Text(text("zero_tolerance"))
                        .font(.headline)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12))
                    Text(text("response"))
                    Text(text("license"))
                }

                Group {
                    Link(text("full_terms"), destination: policyURL("terms"))
                        .accessibilityIdentifier("terms.fullTerms")
                    Link(text("community"), destination: policyURL("community-standards"))
                    Link(text("privacy"), destination: policyURL("privacy"))
                    Link(
                        text("apple_eula"),
                        destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!
                    )
                    Text(text("contact"))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Link("contact@liber-ant.com", destination: URL(string: "mailto:contact@liber-ant.com")!)
                }
                Divider()
                Toggle(text("acknowledgement"), isOn: $acknowledged)
                    .accessibilityIdentifier("terms.acknowledgement")
                if declined || saveFailed {
                    Text(text(saveFailed ? "save_failed" : "declined"))
                        .font(.footnote)
                        .accessibilityIdentifier("terms.notice")
                }
                Button(text("accept")) {
                    if store.accept(language: language, acknowledged: acknowledged) {
                        onAccepted()
                    } else {
                        saveFailed = true
                    }
                }
                .controlSize(.large)
                .buttonStyle(.borderedProminent)
                .disabled(!acknowledged)
                .accessibilityIdentifier("terms.accept")
                Button(text("decline")) {
                    acknowledged = false
                    declined = true
                    saveFailed = false
                }
                .accessibilityIdentifier("terms.decline")
            }
            .frame(maxWidth: 640, alignment: .leading)
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .onChange(of: language) { _, _ in
            acknowledged = false
            declined = false
            saveFailed = false
        }
    }

    private func text(_ key: String) -> String {
        localized(key, language: language)
    }

    private func localized(_ key: String, language: TermsLanguage) -> String {
        let bundle = Bundle.main.path(forResource: language.rawValue, ofType: "lproj")
            .flatMap { Bundle(path: $0) } ?? .main
        return bundle.localizedString(forKey: key, value: nil, table: "Terms")
    }

    private func policyURL(_ path: String) -> URL {
        URL(string: "https://www.liber-ant.com/\(path)/#\(language.websiteAnchor)")!
    }
}
