/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import MeshLinkKit
import SwiftUI

@main
struct NTsocialMeshLinkApp: App {
    var body: some Scene {
        WindowGroup {
            MeshLinkHostView()
        }
    }
}

private struct MeshLinkHostView: View {
    private let consentStore = TermsConsentStore()
    @State private var hasAcceptedTerms = TermsConsentStore().hasAcceptedCurrentTerms
    @State private var pendingURL: URL?

    var body: some View {
        Group {
            if hasAcceptedTerms {
                AcceptedMeshLinkHostView(pendingURL: $pendingURL)
            } else {
                TermsConsentView(store: consentStore) {
                    hasAcceptedTerms = consentStore.hasAcceptedCurrentTerms
                }
            }
        }
        .onOpenURL { url in
            if hasAcceptedTerms {
                MeshLinkRuntime.shared.handleOpenUrl(url: url.absoluteString)
            } else {
                // Retain the handoff, but do not create the runtime or process commands before consent.
                pendingURL = url
            }
        }
    }
}

private struct AcceptedMeshLinkHostView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Binding var pendingURL: URL?

    var body: some View {
        ComposeRootView()
            .ignoresSafeArea()
            .onAppear {
                if let url = pendingURL {
                    pendingURL = nil
                    MeshLinkRuntime.shared.handleOpenUrl(url: url.absoluteString)
                }
            }
            .onChange(of: scenePhase, initial: true) { _, phase in
                if phase == .active {
                    AppleGatewayBootstrap.retryConfigurationIfNeeded()
                }
                MeshLinkRuntime.shared.setHostActive(active: phase == .active)
            }
    }
}
