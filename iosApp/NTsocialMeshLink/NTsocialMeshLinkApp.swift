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
    init() {
        AppleGatewayBootstrap.configureRuntime()
    }

    var body: some Scene {
        WindowGroup {
            MeshLinkHostView()
        }
    }
}

private struct MeshLinkHostView: View {
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ComposeRootView()
            .ignoresSafeArea()
            .onOpenURL { url in
                MeshLinkRuntime.shared.handleOpenUrl(url: url.absoluteString)
            }
            .onChange(of: scenePhase, initial: true) { _, phase in
                if phase == .active {
                    AppleGatewayBootstrap.retryConfigurationIfNeeded()
                }
                MeshLinkRuntime.shared.setHostActive(active: phase == .active)
            }
    }
}
