/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import AVFoundation
import MeshLinkKit
import UIKit
import VisionKit

private final class ScannerReticleView: UIView {
    private let dimLayer = CAShapeLayer()
    private let cornerLayer = CAShapeLayer()

    var onReticleFrameChange: ((CGRect) -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        backgroundColor = .clear

        dimLayer.fillColor = UIColor.black.withAlphaComponent(0.6).cgColor
        dimLayer.fillRule = .evenOdd
        layer.addSublayer(dimLayer)

        cornerLayer.fillColor = UIColor.clear.cgColor
        cornerLayer.strokeColor = UIColor.white.cgColor
        cornerLayer.lineWidth = 3
        layer.addSublayer(cornerLayer)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()

        dimLayer.frame = bounds
        cornerLayer.frame = bounds

        let reticleSize = min(bounds.width, bounds.height) * 0.7
        let reticleRect = CGRect(
            x: (bounds.width - reticleSize) / 2,
            y: (bounds.height - reticleSize) / 2,
            width: reticleSize,
            height: reticleSize
        )
        onReticleFrameChange?(reticleRect)

        let dimPath = UIBezierPath(rect: bounds)
        dimPath.append(UIBezierPath(rect: reticleRect))
        dimLayer.path = dimPath.cgPath

        let cornerLength: CGFloat = 40
        let corners = UIBezierPath()
        corners.move(to: CGPoint(x: reticleRect.minX, y: reticleRect.minY + cornerLength))
        corners.addLine(to: CGPoint(x: reticleRect.minX, y: reticleRect.minY))
        corners.addLine(to: CGPoint(x: reticleRect.minX + cornerLength, y: reticleRect.minY))

        corners.move(to: CGPoint(x: reticleRect.maxX - cornerLength, y: reticleRect.minY))
        corners.addLine(to: CGPoint(x: reticleRect.maxX, y: reticleRect.minY))
        corners.addLine(to: CGPoint(x: reticleRect.maxX, y: reticleRect.minY + cornerLength))

        corners.move(to: CGPoint(x: reticleRect.maxX, y: reticleRect.maxY - cornerLength))
        corners.addLine(to: CGPoint(x: reticleRect.maxX, y: reticleRect.maxY))
        corners.addLine(to: CGPoint(x: reticleRect.maxX - cornerLength, y: reticleRect.maxY))

        corners.move(to: CGPoint(x: reticleRect.minX + cornerLength, y: reticleRect.maxY))
        corners.addLine(to: CGPoint(x: reticleRect.minX, y: reticleRect.maxY))
        corners.addLine(to: CGPoint(x: reticleRect.minX, y: reticleRect.maxY - cornerLength))
        cornerLayer.path = corners.cgPath
    }
}

final class BarcodeScannerHost: NSObject, IosBarcodeScannerHost, DataScannerViewControllerDelegate {
    weak var presentingViewController: UIViewController?

    private var activeScanner: DataScannerViewController?
    private var activeRequestId: Int64?
    private var isCompleting = false

    override init() {
        super.init()
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(cancelForBackground),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    var isSupported: Bool {
        DataScannerViewController.isSupported
    }

    func startScan(requestId: Int64) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            guard self.activeRequestId == nil else {
                MeshLinkRuntime.shared.handleBarcodeScanResult(requestId: requestId, contents: nil)
                return
            }
            self.activeRequestId = requestId
            self.requestCameraAndPresent(requestId: requestId)
        }
    }

    func invalidate() {
        presentingViewController = nil
        if activeScanner != nil {
            finishScanning(with: nil, animated: false)
        } else if let requestId = activeRequestId {
            activeRequestId = nil
            MeshLinkRuntime.shared.handleBarcodeScanResult(requestId: requestId, contents: nil)
        }
    }

    func dataScanner(
        _ dataScanner: DataScannerViewController,
        didAdd addedItems: [RecognizedItem],
        allItems: [RecognizedItem]
    ) {
        guard let payload = addedItems.compactMap(Self.barcodePayload).first else { return }
        finishScanning(with: payload)
    }

    func dataScanner(
        _ dataScanner: DataScannerViewController,
        becameUnavailableWithError error: DataScannerViewController.ScanningUnavailable
    ) {
        guard activeScanner === dataScanner else { return }
        finishScanning(with: nil) { [weak self] in
            self?.showScannerUnavailableAlert()
        }
    }

    private static func barcodePayload(from item: RecognizedItem) -> String? {
        guard case let .barcode(barcode) = item else { return nil }
        return barcode.payloadStringValue
    }

    private func requestCameraAndPresent(requestId: Int64) {
        guard activeRequestId == requestId, activeScanner == nil else { return }

        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            presentScanner(requestId: requestId)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    guard self?.activeRequestId == requestId else { return }
                    if granted {
                        self?.presentScanner(requestId: requestId)
                    } else {
                        self?.completeWithoutResult(requestId: requestId)
                        self?.showCameraPermissionAlert()
                    }
                }
            }
        case .denied:
            completeWithoutResult(requestId: requestId)
            showCameraPermissionAlert()
        case .restricted:
            completeWithoutResult(requestId: requestId)
            showScannerUnavailableAlert()
        @unknown default:
            completeWithoutResult(requestId: requestId)
            showScannerUnavailableAlert()
        }
    }

    private func presentScanner(requestId: Int64) {
        guard activeRequestId == requestId else { return }
        guard DataScannerViewController.isAvailable else {
            completeWithoutResult(requestId: requestId)
            showScannerUnavailableAlert()
            return
        }
        guard let presenter = topViewController(from: presentingViewController) else {
            completeWithoutResult(requestId: requestId)
            return
        }

        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .accurate,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: false,
            isHighlightingEnabled: false
        )
        scanner.delegate = self
        scanner.modalPresentationStyle = .fullScreen
        installAndroidParityOverlay(on: scanner)

        activeScanner = scanner
        isCompleting = false

        presenter.present(scanner, animated: true) { [weak self, weak scanner] in
            guard let self, let scanner, self.activeScanner === scanner else { return }
            do {
                try scanner.startScanning()
            } catch {
                self.finishScanning(with: nil) { [weak self] in
                    self?.showScannerUnavailableAlert()
                }
            }
        }
    }

    private func installAndroidParityOverlay(on scanner: DataScannerViewController) {
        let container = scanner.overlayContainerView

        let reticle = ScannerReticleView(frame: container.bounds)
        reticle.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        reticle.onReticleFrameChange = { [weak scanner, weak reticle] frame in
            guard let scanner, let reticle else { return }
            let regionOfInterest = reticle.convert(frame, to: scanner.view)
            if scanner.regionOfInterest != regionOfInterest {
                scanner.regionOfInterest = regionOfInterest
            }
        }
        container.addSubview(reticle)

        let closeButton = UIButton(type: .system)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.tintColor = .white
        closeButton.setImage(
            UIImage(systemName: "xmark", withConfiguration: UIImage.SymbolConfiguration(pointSize: 20)),
            for: .normal
        )
        closeButton.accessibilityLabel = scannerString("NTSocialScannerCancel", fallback: "Cancel")
        closeButton.addTarget(self, action: #selector(cancelScanning), for: .touchUpInside)
        scanner.view.addSubview(closeButton)

        NSLayoutConstraint.activate([
            closeButton.leadingAnchor.constraint(equalTo: scanner.view.safeAreaLayoutGuide.leadingAnchor, constant: 16),
            closeButton.topAnchor.constraint(equalTo: scanner.view.safeAreaLayoutGuide.topAnchor, constant: 16),
            closeButton.widthAnchor.constraint(equalToConstant: 48),
            closeButton.heightAnchor.constraint(equalToConstant: 48),
        ])
    }

    @objc private func cancelScanning() {
        finishScanning(with: nil)
    }

    @objc private func cancelForBackground() {
        guard activeScanner != nil else { return }
        finishScanning(with: nil, animated: false)
    }

    private func finishScanning(
        with contents: String?,
        animated: Bool = true,
        completion: (() -> Void)? = nil
    ) {
        guard let scanner = activeScanner, let requestId = activeRequestId, !isCompleting else { return }
        isCompleting = true
        scanner.stopScanning()
        scanner.delegate = nil
        scanner.dismiss(animated: animated) { [weak self] in
            self?.activeScanner = nil
            self?.activeRequestId = nil
            self?.isCompleting = false
            MeshLinkRuntime.shared.handleBarcodeScanResult(requestId: requestId, contents: contents)
            completion?()
        }
    }

    private func completeWithoutResult(requestId: Int64) {
        guard activeRequestId == requestId else { return }
        activeRequestId = nil
        MeshLinkRuntime.shared.handleBarcodeScanResult(requestId: requestId, contents: nil)
    }

    private func showCameraPermissionAlert() {
        guard let presenter = topViewController(from: presentingViewController) else { return }
        let alert = UIAlertController(
            title: scannerString("NTSocialScannerPermissionTitle", fallback: "Camera Access Needed"),
            message: scannerString(
                "NTSocialScannerPermissionMessage",
                fallback: "Allow camera access in Settings to scan Meshtastic QR codes."
            ),
            preferredStyle: .alert
        )
        alert.addAction(
            UIAlertAction(
                title: scannerString("NTSocialScannerCancel", fallback: "Cancel"),
                style: .cancel
            )
        )
        alert.addAction(
            UIAlertAction(
                title: scannerString("NTSocialScannerOpenSettings", fallback: "Open Settings"),
                style: .default
            ) { _ in
                guard let settingsUrl = URL(string: UIApplication.openSettingsURLString) else { return }
                UIApplication.shared.open(settingsUrl)
            }
        )
        presenter.present(alert, animated: true)
    }

    private func showScannerUnavailableAlert() {
        guard let presenter = topViewController(from: presentingViewController) else { return }
        let alert = UIAlertController(
            title: scannerString("NTSocialScannerUnavailableTitle", fallback: "Scanner Unavailable"),
            message: scannerString(
                "NTSocialScannerUnavailableMessage",
                fallback: "The camera is unavailable. Close other camera apps and try again."
            ),
            preferredStyle: .alert
        )
        alert.addAction(
            UIAlertAction(
                title: scannerString("NTSocialScannerOkay", fallback: "OK"),
                style: .default
            )
        )
        presenter.present(alert, animated: true)
    }

    private func topViewController(from root: UIViewController?) -> UIViewController? {
        if let presented = root?.presentedViewController {
            return topViewController(from: presented)
        }
        if let navigation = root as? UINavigationController {
            return topViewController(from: navigation.visibleViewController)
        }
        if let tab = root as? UITabBarController {
            return topViewController(from: tab.selectedViewController)
        }
        return root
    }

    private func scannerString(_ key: String, fallback: String) -> String {
        Bundle.main.localizedString(forKey: key, value: fallback, table: "InfoPlist")
    }
}
