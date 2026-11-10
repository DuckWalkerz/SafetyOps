import Foundation
import UIKit

enum Config {
    static let baseURL          = URL(string: "http://74.50.77.70:9090")!
    static let deviceId: String = UIDevice.current.identifierForVendor?.uuidString ?? "ios-device"
    static let locationInterval: TimeInterval = 3.0
    static let frameInterval:    TimeInterval = 0.25
    static let jpegQuality:      CGFloat = 0.45
    static let targetWidth:      CGFloat = 640
    static let uploadTimeout:    TimeInterval = 5.0
}
