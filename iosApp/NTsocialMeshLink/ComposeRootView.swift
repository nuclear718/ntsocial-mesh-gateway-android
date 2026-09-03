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
import UIKit

@MainActor
struct ComposeRootView: UIViewControllerRepresentable {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIViewController(context: Context) -> UIViewController {
        let scannerHost = BarcodeScannerHost()
        context.coordinator.scannerHost = scannerHost
        MeshLinkRuntime.shared.configureBarcodeScanner(host: scannerHost)
        let controller = MeshLinkRuntime.shared.makeRootViewController()
        scannerHost.presentingViewController = controller
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Compose owns the rendered state; SwiftUI owns only the host lifecycle.
    }

    static func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        guard let scannerHost = coordinator.scannerHost else { return }
        MeshLinkRuntime.shared.removeBarcodeScanner(host: scannerHost)
        scannerHost.invalidate()
        coordinator.scannerHost = nil
    }

    final class Coordinator {
        var scannerHost: BarcodeScannerHost?
    }
}
