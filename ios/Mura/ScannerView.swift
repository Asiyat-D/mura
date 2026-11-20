import SwiftUI
import AVFoundation

struct ScannerView: UIViewControllerRepresentable {
    @EnvironmentObject var app: AppState
    let onCode: (String) -> Void
    func makeUIViewController(context: Context) -> ScannerController {
        let controller = ScannerController(); controller.onCode = onCode; controller.cameraError = app.s[.CameraUnavailable]; controller.permissionError = app.s[.EnableCamera]; controller.closeTitle = app.s[.Close]; return controller
    }
    func updateUIViewController(_ controller: ScannerController, context: Context) {}
}
/// Session mutations are serialized on a dedicated queue; preview layer only reads the session.
private final class CaptureBox: @unchecked Sendable {
    let session = AVCaptureSession()
    let queue = DispatchQueue(label: "kz.mura.camera")
}
@MainActor final class ScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onCode: ((String) -> Void)?
    var cameraError = "Camera unavailable", permissionError = "Allow camera access", closeTitle = "Close"
    private let capture = CaptureBox()
    private var preview: AVCaptureVideoPreviewLayer?
    private var delivered = false
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor = .black
        let close = UIButton(type: .system); close.setTitle(closeTitle, for: .normal); close.tintColor = .white; close.backgroundColor = .black.withAlphaComponent(0.7); close.layer.cornerRadius = 18; close.translatesAutoresizingMaskIntoConstraints = false
        close.addTarget(self, action: #selector(closeScanner), for: .touchUpInside); view.addSubview(close)
        NSLayoutConstraint.activate([close.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20), close.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -20), close.widthAnchor.constraint(greaterThanOrEqualToConstant: 100), close.heightAnchor.constraint(equalToConstant: 48)])
        Task {
            let allowed = await AVCaptureDevice.requestAccess(for: .video)
            guard allowed else { showError(permissionError); return }
            configure()
        }
    }
    private func configure() {
        guard let device = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: device) else { showError(cameraError); return }
        let output = AVCaptureMetadataOutput()
        capture.session.beginConfiguration()
        guard capture.session.canAddInput(input), capture.session.canAddOutput(output) else { capture.session.commitConfiguration(); showError(cameraError); return }
        capture.session.addInput(input); capture.session.addOutput(output); output.setMetadataObjectsDelegate(self, queue: .main); output.metadataObjectTypes = [.qr]; capture.session.commitConfiguration()
        let layer = AVCaptureVideoPreviewLayer(session: capture.session); layer.videoGravity = .resizeAspectFill; layer.frame = view.bounds; view.layer.insertSublayer(layer, at: 0); preview = layer
        let box = capture; box.queue.async { box.session.startRunning() }
    }
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); preview?.frame = view.bounds }
    override func viewDidDisappear(_ animated: Bool) { super.viewDidDisappear(animated); let box = capture; box.queue.async { box.session.stopRunning() } }
    private func showError(_ message: String) {
        let label = UILabel(); label.text = message; label.textColor = .white; label.numberOfLines = 0; label.textAlignment = .center; label.translatesAutoresizingMaskIntoConstraints = false; view.addSubview(label)
        NSLayoutConstraint.activate([label.centerXAnchor.constraint(equalTo: view.centerXAnchor), label.centerYAnchor.constraint(equalTo: view.centerYAnchor), label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 30), label.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -30)])
    }
    @objc private func closeScanner() { dismiss(animated: true) }
    nonisolated func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        let text = (metadataObjects.first as? AVMetadataMachineReadableCodeObject)?.stringValue
        Task { @MainActor [weak self] in guard let self, !self.delivered, let text else { return }; self.delivered = true; self.onCode?(text) }
    }
}
