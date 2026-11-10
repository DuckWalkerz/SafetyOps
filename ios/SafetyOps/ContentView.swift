import SwiftUI

struct ContentView: View {
    @EnvironmentObject var location: LocationManager
    @EnvironmentObject var camera: CameraStreamer
    @StateObject var chat = ChatManager()
    
    @State private var isTracking = false
    @State private var inputText = ""
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Header
                VStack(spacing: 8) {
                    Image(systemName: "shield.checkered")
                        .font(.system(size: 40))
                        .foregroundColor(Color(red: 0.07, green: 0.18, blue: 0.31))
                    Text("Assured Location Guideline Initiative")
                        .font(.headline)
                        .foregroundColor(Color(red: 0.07, green: 0.18, blue: 0.31))
                    Text("Offender Location Monitoring Program")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                .padding(.top, 20)
                
                // Directive Card
                VStack(alignment: .leading, spacing: 12) {
                    Text("Pursuant to 18 U.S.C. § 3603 and the authority vested in the U.S. Probation and Pretrial Services System, the Assured Location Guideline Initiative establishes mandatory location verification protocols for individuals under supervised release or home detention.")
                        .font(.system(size: 12))
                        .foregroundColor(.primary)
                }
                .padding()
                .background(Color.white)
                .cornerRadius(8)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.3), lineWidth: 1))
                .padding(.horizontal)
                
                if !isTracking {
                    Button(action: startTracking) {
                        Text("Complete Mandatory Device Registration")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color(red: 0.0, green: 0.37, blue: 0.63))
                            .cornerRadius(8)
                    }
                    .padding(.horizontal)
                } else {
                    // Status
                    HStack {
                        Text("Telemetry Pipeline:")
                            .font(.system(size: 12, weight: .bold))
                        Spacer()
                        Text("REGISTERED / LIVE")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.green)
                            .padding(6)
                            .background(Color.green.opacity(0.1))
                            .cornerRadius(4)
                    }
                    .padding()
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(8)
                    .padding(.horizontal)
                    
                    // Chat Box
                    VStack(spacing: 0) {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 8) {
                                ForEach(chat.messages) { msg in
                                    Text(msg.message)
                                        .font(.system(size: 12, design: .monospaced))
                                        .padding(10)
                                        .background(msg.sender.lowercased() == "system" ? Color.gray.opacity(0.1) : Color(red: 0.07, green: 0.18, blue: 0.31))
                                        .foregroundColor(msg.sender.lowercased() == "system" ? .black : .white)
                                        .cornerRadius(8)
                                        .frame(maxWidth: .infinity, alignment: msg.sender.lowercased() == "system" ? .leading : .trailing)
                                }
                            }
                            .padding()
                        }
                        .frame(height: 300)
                        .background(Color.white)
                        
                        Divider()
                        
                        HStack {
                            TextField("Type secure message...", text: $inputText)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .font(.system(size: 12))
                            Button("Send") {
                                chat.sendMessage(text: inputText)
                                inputText = ""
                            }
                            .font(.system(size: 12, weight: .bold))
                        }
                        .padding()
                    }
                    .background(Color.white)
                    .cornerRadius(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.3), lineWidth: 1))
                    .padding(.horizontal)
                }
            }
        }
        .background(Color(red: 0.97, green: 0.98, blue: 0.99).ignoresSafeArea())
    }
    
    func startTracking() {
        isTracking = true
        camera.start()
        location.requestPermissions()
        chat.enrollDevice()
    }
}
