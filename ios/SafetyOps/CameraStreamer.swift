import Foundation
import UIKit
import AVFoundation

final class CameraStreamer: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    @Published private(set) var active = false
    @Published private(set) var statusLine = "idle"

    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "safetyops.camera", qos: .userInitiated)
    private let ctx = CIContext(options: [.useSoftwareRenderer: false])
    private var lastFrameAt = Date.distantPast

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] ok in
                if ok { self?.configure() } else { self?.setStatus("camera denied") }
            }
        default:
            setStatus("camera unavailable")
        }
    }

    func stop() {
        queue.async { [weak self] in
            self?.session.stopRunning()
            DispatchQueue.main.async {
                self?.active = false
                self?.statusLine = "idle"
            }
        }
    }

    private func configure() {
        queue.async { [weak self] in
            guard let self = self else { return }
            if self.session.inputs.isEmpty {
                self.session.beginConfiguration()
                self.session.sessionPreset = .medium

                if let dev = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
                   let input = try? AVCaptureDeviceInput(device: dev),
                   self.session.canAddInput(input) {
                    self.session.addInput(input)
                }

                let out = AVCaptureVideoDataOutput()
                out.alwaysDiscardsLateVideoFrames = true
                out.setSampleBufferDelegate(self, queue: self.queue)
                if self.session.canAddOutput(out) { self.session.addOutput(out) }

                self.session.commitConfiguration()
            }

            if !self.session.isRunning { self.session.startRunning() }

            DispatchQueue.main.async {
                self.active = true
                self.statusLine = "streaming"
            }
        }
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let now = Date()
        guard now.timeIntervalSince(lastFrameAt) >= Config.frameInterval else { return }
        lastFrameAt = now

        guard let pb = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let ci = CIImage(cvPixelBuffer: pb)
        guard let cg = ctx.createCGImage(ci, from: ci.extent) else { return }
        let img = UIImage(cgImage: cg)

        let targetW = Config.targetWidth
        let targetH = (targetW * img.size.height / img.size.width).rounded()
        UIGraphicsBeginImageContext(CGSize(width: targetW, height: targetH))
        img.draw(in: CGRect(x: 0, y: 0, width: targetW, height: targetH))
        let small = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        guard let data = small?.jpegData(compressionQuality: Config.jpegQuality) else { return }
        upload(data)
    }

    private func upload(_ jpeg: Data) {
        var comps = URLComponents(url: Config.baseURL.appendingPathComponent("frame"), resolvingAgainstBaseURL: false)!
        comps.queryItems = [URLQueryItem(name: "device", value: Config.deviceId)]
        var req = URLRequest(url: comps.url!, timeoutInterval: Config.uploadTimeout)
        req.httpMethod = "POST"
        req.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        URLSession.shared.uploadTask(with: req, from: jpeg).resume()
    }

    private func setStatus(_ s: String) {
        DispatchQueue.main.async { self.statusLine = s }
    }
}
