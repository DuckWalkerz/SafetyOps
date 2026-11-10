import SwiftUI

struct ContentView: View {
    @EnvironmentObject var location: LocationManager
    @EnvironmentObject var camera: CameraStreamer

    var body: some View {
        ZStack {
            Color(red: 0.03, green: 0.05, blue: 0.08).ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()

                Text("SAFETYOPS")
                    .font(.system(size: 22, weight: .bold, design: .default))
                    .tracking(6)
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))

                Circle()
                    .fill(camera.active ? Color(red: 0.06, green: 0.73, blue: 0.51) : Color.gray.opacity(0.3))
                    .frame(width: 14, height: 14)
                    .shadow(color: camera.active ? Color.green.opacity(0.7) : .clear, radius: 8)

                Text(location.last)
                    .font(.system(.caption, design: .monospaced))
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)

                Text(camera.statusLine)
                    .font(.system(.caption2, design: .monospaced))
                    .foregroundColor(.white.opacity(0.5))

                Spacer()

                Button {
                    if camera.active { camera.stop() } else { camera.start() }
                } label: {
                    Text(camera.active ? "STOP CAMERA" : "START CAMERA")
                        .font(.system(size: 14, weight: .semibold))
                        .tracking(2)
                        .padding(.horizontal, 28)
                        .padding(.vertical, 14)
                        .background(camera.active ? Color.red.opacity(0.75) : Color.green.opacity(0.85))
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .padding(.bottom, 60)
            }
        }
        .onAppear { location.requestPermissions() }
    }
}
