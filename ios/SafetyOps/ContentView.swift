import SwiftUI

struct ContentView: View {
    @EnvironmentObject var location: LocationManager
    @EnvironmentObject var camera: CameraStreamer

    var body: some View {
        ZStack {
            Color(red: 0.03, green: 0.05, blue: 0.08).ignoresSafeArea()
            VStack(spacing: 24) {
                Text("MOBILE ESCORT")
                    .font(.system(size: 20, weight: .bold, design: .default))
                    .tracking(4)
                    .foregroundColor(.blue)
                
                Circle()
                    .fill(camera.active ? Color.green : Color.gray.opacity(0.3))
                    .frame(width: 14, height: 14)

                Text(location.last)
                    .font(.system(.caption, design: .monospaced))
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)

                Text(camera.statusLine)
                    .font(.system(.caption2, design: .monospaced))
                    .foregroundColor(.white.opacity(0.5))

                Button {
                    if camera.active { camera.stop() } else { camera.start() }
                } label: {
                    Text(camera.active ? "STOP CAMERA" : "START CAMERA")
                        .font(.system(size: 14, weight: .semibold))
                        .padding(.horizontal, 28)
                        .padding(.vertical, 14)
                        .background(camera.active ? Color.red.opacity(0.75) : Color.blue)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(.top, 40)
            }
        }
        .onAppear { location.requestPermissions() }
    }
}
