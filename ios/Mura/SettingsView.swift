import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var app: AppState
    @State private var erasing = false
    var body: some View {
        Form {
            Section { VStack(alignment: .leading, spacing: 9) { Text("mura").font(.largeTitle.bold()); Text(app.s[.Personalize]).font(.subheadline).foregroundStyle(.secondary) }.padding(.vertical, 10) }
            NetworkSettingsSection()
            Section(app.s[.Appearance]) {
                Picker(app.s[.Theme], selection: $app.theme) { Text(app.s[.System]).tag("system"); Text(app.s[.Light]).tag("light"); Text(app.s[.Dark]).tag("dark") }
                Picker(app.s[.Language], selection: $app.language) { Text("Русский · RU").tag("ru"); Text("Қазақша · KZ").tag("kk"); Text("English · EN").tag("en") }.accessibilityIdentifier("language")
            }
            Section { Toggle(isOn: Binding(get: { app.biometric }, set: { if $0 { app.authenticate(enable: true) } else { app.biometric = false } })) { Label(app.s[.Biometric], systemImage: "faceid") }.disabled(app.authenticating); Picker(app.s[.Retention], selection: $app.retention) { Text(app.s[.Never]).tag(0); Text(app.s[.Day]).tag(1); Text(app.s[.Week]).tag(7); Text(app.s[.Month]).tag(30) } } header: { Text(app.s[.Privacy]) } footer: { Text(app.s[.RetentionDetail]) }
            Section { Label(app.s[.VaultIOS], systemImage: "lock.shield").font(.footnote).foregroundStyle(.secondary) }
            Section { Button(app.s[.Erase], role: .destructive) { erasing = true } }
            Section { Text(app.s[.About]).font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity) }
        }.navigationTitle(app.s[.Settings]).alert(app.s[.Erase], isPresented: $erasing) { Button(app.s[.Delete], role: .destructive) { app.eraseAll() }; Button(app.s[.Cancel], role: .cancel) {} } message: { Text(app.s[.EraseConfirm]) }
    }
}
