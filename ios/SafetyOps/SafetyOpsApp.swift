import SwiftUI

@main
struct SafetyOpsApp: App {
    @StateObject private var location = LocationManager()
    @StateObject private var camera   = CameraStreamer()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(location)
                .environmentObject(camera)
                .preferredColorScheme(.dark)
        }
    }
}
