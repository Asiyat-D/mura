import SwiftUI
import UniformTypeIdentifiers
import CoreImage.CIFilterBuiltins

struct ConverterView: View {
    @EnvironmentObject var app: AppState
    @State private var showingQR = false
    @State private var scanning = false
    @State private var importing = false
    @State private var fullResult = false
    @State private var sharing = false
    @State private var shareItems: [Any] = []
    @State private var showPassword = false
    @FocusState private var editing: Bool
    var body: some View {
        ScrollViewReader { scroll in
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    Brand()
                    Text(app.s[.Tagline]).font(.system(size: 38, weight: .bold)).tracking(-1.4).fixedSize(horizontal: false, vertical: true)
                    Text(app.s[.Intro]).font(.subheadline).foregroundStyle(.secondary)
                    Picker("", selection: Binding(get: { app.mode.secure }, set: { app.changeMode($0 ? .encrypt : .encode) })) {
                        Text(app.s[.Encoding]).tag(false); Text(app.s[.Protection]).tag(true)
                    }.pickerStyle(.segmented).padding(.top, 8).disabled(app.busy)
                    Picker("", selection: Binding(get: { app.mode.reverse }, set: { reverse in
                        if reverse && !app.output.isEmpty { app.importCode(app.output) }
                        else { app.changeMode(app.mode.secure ? (reverse ? .decrypt : .encrypt) : (reverse ? .decode : .encode)) }
                    })) { Text(app.s[app.mode.secure ? .Encrypt : .Encode]).tag(false); Text(app.s[app.mode.secure ? .Decrypt : .Decode]).tag(true) }.pickerStyle(.segmented).disabled(app.busy)
                    inputCard
                    if app.mode.secure { passwordField }
                    Button {
                        editing = false; app.convert()
                    } label: {
                        HStack { if app.busy { ProgressView().tint(.white) } else { Image(systemName: app.mode.secure ? "lock" : "sparkles") }; Text(app.s[app.busy ? .Processing : app.mode.reverse ? .Decrypt : app.mode.secure ? .Encrypt : .Convert]); Spacer(); Image(systemName: "arrow.right") }.font(.headline).padding(.vertical, 10)
                    }.buttonStyle(.borderedProminent).controlSize(.large).disabled(app.busy).accessibilityIdentifier("action_convert")
                    if !app.output.isEmpty { resultCard.id("result") }
                    NoticeView(text: app.s[app.mode.secure ? .SecureNotice : .DemoNotice], icon: app.mode.secure ? "lock.shield" : "info.circle")
                    HStack {
                        Button { scanning = true } label: { Label(app.s[.Scan], systemImage: "qrcode.viewfinder") }
                        Spacer()
                        Button { importing = true } label: { Image(systemName: "doc.badge.plus").accessibilityLabel(app.s[.ImportFile]) }
                    }.font(.subheadline).padding(.vertical, 6)
                    HStack { Text(app.s[.Recent]).font(.headline); Spacer(); Button(app.s[.AllHistory]) { app.selectedTab = 2 }.font(.subheadline) }
                    if app.entries.isEmpty { EmptyViewCard(icon: "clock.arrow.circlepath", title: app.s[.NoHistory], detail: app.s[.NoHistoryDetail]) }
                    else { ForEach(app.entries.prefix(2)) { EntryCard(entry: $0) } }
                }.padding(.horizontal, 23).padding(.bottom, 25).frame(maxWidth: 760).frame(maxWidth: .infinity)
            }.background(Palette.background).toolbar(.hidden, for: .navigationBar)
                .onChange(of: app.output) { _, value in if !value.isEmpty { withAnimation { scroll.scrollTo("result", anchor: .center) } } }
        }
        .sheet(isPresented: $showingQR) { QRView(code: app.output) }
        .sheet(isPresented: $scanning) { ScannerView { value in scanning = false; app.importCode(value) }.ignoresSafeArea() }
        .sheet(isPresented: $sharing) { ShareSheet(items: shareItems) }
        .sheet(isPresented: $fullResult) { NavigationStack { ScrollView { Text(app.output).font(app.mode.reverse ? .body : .system(.body, design: .monospaced)).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading).padding() }.navigationTitle(app.s[.Result]).toolbar { ToolbarItem(placement: .confirmationAction) { Button(app.s[.Close]) { fullResult = false } }; ToolbarItem(placement: .bottomBar) { Button(app.s[.Copy]) { app.copy(app.output) } } } } }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.plainText, .text]) { result in
            do {
                let url = try result.get(); let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                guard size <= 1_048_576 else { throw MuraError.tooLong }
                let text = try String(contentsOf: url, encoding: .utf8); app.importCode(text)
            } catch { app.failure = (error as? MuraError) ?? .format }
        }
    }
    private var inputCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack { Text(app.s[app.mode.reverse ? .CodeInput : .Input]).font(.subheadline.weight(.semibold)); Spacer(); Button { if let text = UIPasteboard.general.string { app.setInput(text) } else { app.notice = app.s[.ClipboardEmpty] } } label: { Label(app.s[.Paste], systemImage: "doc.on.clipboard").font(.caption.weight(.medium)) }.disabled(app.busy) }
            ZStack(alignment: .topLeading) {
                if app.input.isEmpty { Text(app.s[app.mode.reverse ? .CodeHint : .InputHint]).foregroundStyle(.tertiary).padding(.top, 8).padding(.leading, 5).allowsHitTesting(false) }
                TextEditor(text: Binding(get: { app.input }, set: app.setInput)).scrollContentBackground(.hidden).frame(minHeight: 120, maxHeight: 180).focused($editing).autocorrectionDisabled().textInputAutocapitalization(.never).disabled(app.busy).accessibilityLabel(app.s[.Input]).accessibilityIdentifier("converter_input")
            }
            HStack { Text("\(app.input.unicodeScalars.count) \(app.s[.Characters])").font(.caption).foregroundStyle(.secondary); Spacer(); Button { app.clear() } label: { Image(systemName: "trash").padding(5) }.accessibilityLabel(app.s[.Clear]).disabled(app.busy || app.input.isEmpty) }
        }.padding(18).background(Palette.card, in: RoundedRectangle(cornerRadius: 22))
    }
    private var passwordField: some View {
        HStack { Group { if showPassword { TextField(app.s[.Password], text: $app.password) } else { SecureField(app.s[.Password], text: $app.password) } }.textContentType(.password).autocorrectionDisabled().textInputAutocapitalization(.never).accessibilityIdentifier("password"); Button { showPassword.toggle() } label: { Image(systemName: showPassword ? "eye.slash" : "eye") }.accessibilityLabel(app.s[showPassword ? .HidePassword : .ShowPassword]) }.padding(17).background(Palette.card, in: RoundedRectangle(cornerRadius: 17)).disabled(app.busy)
    }
    private var resultCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            Label(app.s[.Result], systemImage: "checkmark.circle").font(.caption.weight(.semibold)).tracking(1.4).foregroundStyle(Palette.cyan)
            Text(String(app.output.prefix(1000))).font(app.mode.reverse ? .body : .system(.body, design: .monospaced)).lineSpacing(5).lineLimit(5).foregroundStyle(.white).textSelection(.enabled).accessibilityIdentifier("converter_output")
            Button(app.s[.ShowCode]) { fullResult = true }.font(.caption).foregroundStyle(Palette.cyan)
            Divider().overlay(.white.opacity(0.1))
            HStack {
                Button { app.copy(app.output) } label: { Label(app.s[.Copy], systemImage: "doc.on.doc").font(.subheadline.weight(.semibold)).padding(10).background(.white.opacity(0.13), in: RoundedRectangle(cornerRadius: 12)) }.accessibilityIdentifier("copy_result")
                Spacer()
                Button { do { shareItems = try ShareFiles.payload(app.output); sharing = true } catch { app.failure = .storage } } label: { Image(systemName: "square.and.arrow.up").padding(8) }.accessibilityLabel(app.s[.Share])
                if !app.mode.reverse { Button { showingQR = true } label: { Image(systemName: "qrcode").padding(8) }.accessibilityLabel(app.s[.Qr]) }
            }.foregroundStyle(.white)
        }.padding(22).frame(maxWidth: .infinity, alignment: .leading).background(Palette.navy, in: RoundedRectangle(cornerRadius: 23))
    }
}
struct QRView: View {
    @EnvironmentObject var app: AppState
    let code: String
    @State private var image: UIImage?
    @State private var failed = false
    var body: some View {
        NavigationStack { VStack(spacing: 24) { if failed { Text(app.s[.QrError]).padding() } else if let image { Image(uiImage: image).interpolation(.none).resizable().scaledToFit().padding(20).background(.white).accessibilityIdentifier("qr_image") } else { ProgressView() } }.padding(22).navigationTitle(app.s[.Qr]).toolbar { Button(app.s[.Close]) { dismiss() } } }
            .task {
                let payload = code.filter { !$0.isWhitespace }
                guard payload.count <= 2000 else { failed = true; return }
                let filter = CIFilter.qrCodeGenerator(); filter.message = Data(payload.utf8); filter.correctionLevel = "M"
                if let output = filter.outputImage, let cg = CIContext().createCGImage(output.transformed(by: CGAffineTransform(scaleX: 8, y: 8)), from: output.extent.applying(CGAffineTransform(scaleX: 8, y: 8))) { image = UIImage(cgImage: cg) } else { failed = true }
            }
    }
    @Environment(\.dismiss) private var dismiss
}
