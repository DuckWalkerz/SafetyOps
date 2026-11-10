import Foundation

enum Config {
    static let baseURL          = URL(string: "http://127.0.0.1:9090")!
    static let deviceId: String = UIDevice.current.identifierForVendor?.uuidString ?? "ios"
    static let locationInterval: TimeInterval = 3.0
    static let frameInterval:    TimeInterval = 0.25    // max ~4 fps
    static let jpegQuality:      CGFloat = 0.45
    static let targetWidth:      CGFloat = 640
    static let uploadTimeout:    TimeInterval = 5.0
}
