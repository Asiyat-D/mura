import SwiftUI

@main struct MuraApp: App {
    @StateObject private var app = AppState()
    var body: some Scene {
        WindowGroup {
            ZStack {
                if app.biometric && app.locked { LockView() } else { MainTabs() }
                if !app.sceneActive { Palette.background.ignoresSafeArea(); Image(systemName: "lock.shield").font(.system(size: 44)).foregroundStyle(Palette.blue) }
            }
            .environmentObject(app)
            .preferredColorScheme(app.theme == "light" ? .light : app.theme == "dark" ? .dark : nil)
            .tint(Palette.blue)
            .privacySensitive()
            .alert(app.s[.Error], isPresented: Binding(get: { app.failure != nil }, set: { if !$0 { app.failure = nil } })) {
                Button(app.s[.Close]) { app.failure = nil }
            } message: { if let error = app.failure { Text(app.s.failure(error)) } }
            .overlay(alignment: .bottom) {
                if let notice = app.notice { Text(notice).font(.subheadline.weight(.medium)).padding(14).background(.regularMaterial, in: Capsule()).padding(.bottom, 80).allowsHitTesting(false) }
            }
            .onAppear { app.sceneActive = UIApplication.shared.applicationState == .active }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)) { _ in app.sceneActive = false }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in app.sceneActive = true; app.prune() }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in app.locked = true; app.password = "" }
            .task { await app.runNetwork() }
            .alert(app.s.n("Подключение"), isPresented: Binding(get: { app.networkFailure != nil }, set: { if !$0 { app.networkFailure = nil } })) { Button(app.s[.Close]) { app.networkFailure = nil } } message: { Text(app.s.n(app.networkFailure ?? "")) }
            .task {
                while !Task.isCancelled {
                    try? await Task.sleep(for: .seconds(60))
                    if !Task.isCancelled && app.sceneActive { app.prune() }
                }
            }
        }
    }
}
struct MainTabs: View {
    @EnvironmentObject var app: AppState
    var body: some View {
        TabView(selection: $app.selectedTab) {
            NavigationStack { ConverterView() }.tabItem { Label(app.s[.Cipher], systemImage: "number") }.tag(0)
            ChatsView().tabItem { Label(app.s[.Chats], systemImage: "bubble.left.and.bubble.right") }.tag(1)
            NavigationStack { HistoryView() }.tabItem { Label(app.s[.History], systemImage: "clock.arrow.circlepath") }.tag(2)
            NavigationStack { SettingsView() }.tabItem { Label(app.s[.Settings], systemImage: "slider.horizontal.3") }.tag(3)
        }
    }
}
enum Palette {
    static let blue = Color(red: 0.157, green: 0.345, blue: 0.91)
    static let navy = Color(red: 0.063, green: 0.118, blue: 0.24)
    static let cyan = Color(red: 0.54, green: 0.8, blue: 1)
    static let background = Color(uiColor: .systemGroupedBackground)
    static let card = Color(uiColor: .secondarySystemGroupedBackground)
}
struct Brand: View {
    @EnvironmentObject var app: AppState
    var body: some View {
        HStack(spacing: 10) {
            Text("m").font(.system(size: 29, weight: .heavy, design: .rounded)).foregroundStyle(.white).frame(width: 39, height: 39).background(Palette.blue, in: RoundedRectangle(cornerRadius: 13))
            Text("mura").font(.title2.weight(.bold))
            Spacer()
            Label { Text(app.s.n(app.networkConnected ? "Онлайн" : "Нет связи")).font(.caption) } icon: { Circle().fill(app.networkConnected ? .mint : .gray).frame(width: 6, height: 6) }.foregroundStyle(.secondary).padding(.horizontal, 12).padding(.vertical, 9).background(Palette.card, in: Capsule())
        }.padding(.vertical, 12)
    }
}
struct NoticeView: View {
    let text: String
    var icon = "info.circle"
    var body: some View { HStack(alignment: .top, spacing: 10) { Image(systemName: icon).foregroundStyle(Palette.blue); Text(text).font(.caption).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true) }.frame(maxWidth: .infinity, alignment: .leading).padding(15).background(Palette.blue.opacity(0.07), in: RoundedRectangle(cornerRadius: 17)) }
}
struct EmptyViewCard: View {
    let icon: String
    let title: String
    let detail: String
    var body: some View { VStack(spacing: 12) { Image(systemName: icon).font(.system(size: 29)).foregroundStyle(Palette.blue).frame(width: 66, height: 66).background(Palette.blue.opacity(0.1), in: RoundedRectangle(cornerRadius: 23)); Text(title).font(.headline); Text(detail).font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 280) }.frame(maxWidth: .infinity).padding(.vertical, 35) }
}
struct LockView: View {
    @EnvironmentObject var app: AppState
    var body: some View { VStack(spacing: 25) { EmptyViewCard(icon: "lock.shield", title: app.s[.Locked], detail: app.s[.BiometricDetail]); Button { app.authenticate() } label: { Label(app.s[.Unlock], systemImage: "faceid").frame(maxWidth: .infinity).padding(.vertical, 10) }.buttonStyle(.borderedProminent).disabled(app.authenticating) }.padding(28).frame(maxWidth: .infinity, maxHeight: .infinity).background(Palette.background) }
}
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
