import Foundation
import UIKit
import CoreLocation

final class LocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let mgr = CLLocationManager()
    @Published var last: String = "requesting location…"
    private var lastSentAt = Date.distantPast

    override init() {
        super.init()
        mgr.delegate = self
        mgr.desiredAccuracy = kCLLocationAccuracyBest
        mgr.distanceFilter = 1
        if #available(iOS 11.0, *) {
            mgr.showsBackgroundLocationIndicator = true
        }
    }

    func requestPermissions() {
        mgr.requestAlwaysAuthorization()
        mgr.allowsBackgroundLocationUpdates = true
        mgr.pausesLocationUpdatesAutomatically = false
        mgr.startUpdatingLocation()
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedAlways || manager.authorizationStatus == .authorizedWhenInUse {
            manager.startUpdatingLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let loc = locations.last else { return }
        last = String(format: "%.5f, %.5f ±%.0fm", loc.coordinate.latitude, loc.coordinate.longitude, loc.horizontalAccuracy)
        guard Date().timeIntervalSince(lastSentAt) >= Config.locationInterval else { return }
        lastSentAt = Date()
        send(loc)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        last = "location error: \(error.localizedDescription)"
    }

    private func send(_ loc: CLLocation) {
        var url = Config.baseURL.appendingPathComponent("location")
        var comps = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        comps.queryItems = [URLQueryItem(name: "device", value: Config.deviceId)]
        url = comps.url!

        var req = URLRequest(url: url, timeoutInterval: Config.uploadTimeout)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let speedMps = max(0.0, loc.speed)
        let bearing = loc.course >= 0 ? loc.course : 0.0
        let alt = loc.altitude
        let batt = batteryPct()
        
        let body = """
        {"lat":\(loc.coordinate.latitude),"lon":\(loc.coordinate.longitude),"acc":\(loc.horizontalAccuracy),"battery":\(batt),"platform":"ios","speed":\(speedMps),"bearing":\(bearing),"alt":\(alt)}
        """
        req.httpBody = body.data(using: .utf8)
        URLSession.shared.dataTask(with: req).resume()
    }

    private func batteryPct() -> Int {
        UIDevice.current.isBatteryMonitoringEnabled = true
        let lvl = UIDevice.current.batteryLevel
        return lvl < 0 ? -1 : Int((lvl * 100).rounded())
    }
}
