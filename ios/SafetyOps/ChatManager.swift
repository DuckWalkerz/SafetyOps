import Foundation
import UserNotifications

struct ChatMessage: Identifiable {
    let id = UUID()
    let sender: String
    let message: String
    let timestamp: Int
}

class ChatManager: ObservableObject {
    @Published var messages: [ChatMessage] = []
    private var lastSeenTimestamp = 0
    private var timer: Timer?
    
    init() {
        requestNotificationPermission()
        startPolling()
    }
    
    private func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted { print("Notification permission granted.") }
        }
    }
    
    func startPolling() {
        timer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            self?.fetchChat()
        }
    }
    
    func fetchChat() {
        let url = Config.baseURL.appendingPathComponent("api/chat")
        URLSession.shared.dataTask(with: url) { data, _, error in
            guard let data = data, error == nil else { return }
            if let json = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
                DispatchQueue.main.async {
                    var newMessages: [ChatMessage] = []
                    for dict in json {
                        let sender = dict["sender"] as? String ?? "unknown"
                        let msg = dict["message"] as? String ?? ""
                        let ts = dict["timestamp"] as? Int ?? 0
                        
                        newMessages.append(ChatMessage(sender: sender, message: msg, timestamp: ts))
                        
                        // Only notify for NEW system messages
                        if sender.lowercased() == "system" && ts > self.lastSeenTimestamp {
                            self.lastSeenTimestamp = ts
                            self.sendLocalNotification(text: msg)
                        }
                    }
                    self.messages = newMessages
                }
            }
        }.resume()
    }
    
    func sendMessage(text: String) {
        guard !text.isEmpty else { return }
        var request = URLRequest(url: Config.baseURL.appendingPathComponent("api/chat"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = ["deviceId": Config.deviceId, "from": "FIELD", "text": text]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        URLSession.shared.dataTask(with: request) { [weak self] _, _, _ in
            self?.fetchChat()
        }.resume()
    }
    
    func enrollDevice() {
        var request = URLRequest(url: Config.baseURL.appendingPathComponent("api/enroll"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = ["phone": Config.deviceId]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        URLSession.shared.dataTask(with: request).resume()
    }
    
    private func sendLocalNotification(text: String) {
        let content = UNMutableNotificationContent()
        content.title = "Furlough Program Alert"
        content.body = text
        content.sound = .default
        
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }
}
