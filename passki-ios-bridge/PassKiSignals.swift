// Spike A: C entry points over ASCredentialUpdater (Swift-only, iOS 26) for Kotlin/Native cinterop.
import AuthenticationServices
import Foundation

public typealias PasskiCallback = @convention(c) (Int32, UnsafePointer<CChar>?, UnsafeMutableRawPointer?) -> Void

private let statusOk: Int32 = 0
private let statusNotAvailable: Int32 = 1
private let statusError: Int32 = 2

private struct Box: @unchecked Sendable {
    let callback: PasskiCallback
    let ctx: UnsafeMutableRawPointer?
    func done(_ status: Int32, _ message: String?) {
        if let message { message.withCString { callback(status, $0, ctx) } } else { callback(status, nil, ctx) }
    }
}

private func data(_ bytes: UnsafePointer<UInt8>?, _ length: Int32) -> Data {
    guard let bytes, length > 0 else { return Data() }
    return Data(bytes: bytes, count: Int(length))
}

private func run(_ box: Box, _ body: @escaping @Sendable () async throws -> Void) {
    Task {
        do {
            try await body()
            box.done(statusOk, nil)
        } catch {
            let ns = error as NSError
            box.done(statusError, "\(ns.domain) \(ns.code): \(ns.localizedDescription)")
        }
    }
}

@_cdecl("passki_report_unknown")
public func passkiReportUnknown(
    _ rpId: UnsafePointer<CChar>, _ credentialId: UnsafePointer<UInt8>?, _ credentialIdLength: Int32,
    _ callback: PasskiCallback, _ ctx: UnsafeMutableRawPointer?
) {
    let box = Box(callback: callback, ctx: ctx)
    guard #available(iOS 26.0, *) else { return box.done(statusNotAvailable, "Requires iOS 26.") }
    let rp = String(cString: rpId)
    let id = data(credentialId, credentialIdLength)
    run(box) { try await ASCredentialUpdater().reportUnknownPublicKeyCredential(relyingPartyIdentifier: rp, credentialID: id) }
}

@_cdecl("passki_report_all_accepted")
public func passkiReportAllAccepted(
    _ rpId: UnsafePointer<CChar>, _ userHandle: UnsafePointer<UInt8>?, _ userHandleLength: Int32,
    _ ids: UnsafePointer<UnsafePointer<UInt8>?>?, _ idLengths: UnsafePointer<Int32>?, _ count: Int32,
    _ callback: PasskiCallback, _ ctx: UnsafeMutableRawPointer?
) {
    let box = Box(callback: callback, ctx: ctx)
    guard #available(iOS 26.0, *) else { return box.done(statusNotAvailable, "Requires iOS 26.") }
    let rp = String(cString: rpId)
    let user = data(userHandle, userHandleLength)
    var accepted: [Data] = []
    if let ids, let idLengths {
        for i in 0..<Int(count) { accepted.append(data(ids[i], idLengths[i])) }
    }
    let list = accepted
    run(box) {
        try await ASCredentialUpdater().reportAllAcceptedPublicKeyCredentials(
            relyingPartyIdentifier: rp, userHandle: user, acceptedCredentialIDs: list)
    }
}

@_cdecl("passki_report_details")
public func passkiReportDetails(
    _ rpId: UnsafePointer<CChar>, _ userHandle: UnsafePointer<UInt8>?, _ userHandleLength: Int32,
    _ newName: UnsafePointer<CChar>,
    _ callback: PasskiCallback, _ ctx: UnsafeMutableRawPointer?
) {
    let box = Box(callback: callback, ctx: ctx)
    guard #available(iOS 26.0, *) else { return box.done(statusNotAvailable, "Requires iOS 26.") }
    let rp = String(cString: rpId)
    let user = data(userHandle, userHandleLength)
    let name = String(cString: newName)
    run(box) { try await ASCredentialUpdater().reportPublicKeyCredentialUpdate(relyingPartyIdentifier: rp, userHandle: user, newName: name) }
}
