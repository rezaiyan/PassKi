package com.alirezaiyan.passki.web

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The browser's WebAuthn API, behind an interface so [WebPasskeys] is testable without an authenticator. */
internal interface WebAuthnBridge {
    /**
     * Runs [op] and returns its JSON result, or throws [WebAuthnException]. Ops: `create` and `get` (WebAuthn JSON
     * options in, response JSON out; [mediation] is the credential `mediation`, null for the default), `capabilities`,
     * and the Signal API methods of `PublicKeyCredential` by name (their JSON argument in, `{}` out).
     */
    suspend fun call(op: String, json: String, mediation: String?): String
}

/** A rejected WebAuthn promise: the DOMException [name] (e.g. `NotAllowedError`) and its message. */
internal class WebAuthnException(val name: String, message: String) : Exception(message)

/** [WebAuthnBridge] on `navigator.credentials`. Cancelling the calling coroutine aborts the browser request. */
internal object BrowserWebAuthn : WebAuthnBridge {
    private var nextId = 0

    override suspend fun call(op: String, json: String, mediation: String?): String =
        suspendCancellableCoroutine { continuation ->
            val id = ++nextId
            continuation.invokeOnCancellation { passkiAbort(id) }
            passkiWebAuthn(
                op, json, mediation.orEmpty(), id,
                { result -> if (continuation.isActive) continuation.resume(result) },
                { name, message -> if (continuation.isActive) continuation.resumeWithException(WebAuthnException(name, message)) },
            )
        }
}

/** Aborts the request [id] of [passkiWebAuthn], if it is still pending. Runs [ABORT_JS]. */
internal expect fun passkiAbort(id: Int)

/**
 * Starts [op] (see [WebAuthnBridge.call]) and later calls [ok] or [fail] with the error's name and message. Runs
 * [WEBAUTHN_JS]: `js()` doesn't resolve in shared web code, so jsMain and wasmJsMain each pass it to `js()`.
 */
internal expect fun passkiWebAuthn(
    op: String,
    json: String,
    mediation: String,
    id: Int,
    ok: (String) -> Unit,
    fail: (String, String) -> Unit,
)

internal const val ABORT_JS: String =
    "(function () { var c = globalThis.__passkiAborts && globalThis.__passkiAborts[id]; if (c) c.abort(); })()"

/**
 * The only JavaScript in PassKi. WebAuthn JSON options are turned into the browser's ArrayBuffer form by hand (not
 * `parseCreationOptionsFromJSON`, which older browsers lack), and credentials back into response JSON, so js and wasm
 * see plain strings. Binary fields: challenge, user.id, credential descriptor ids, PRF salts and the large blob.
 */
internal const val WEBAUTHN_JS: String =
    """(function () {
  var g = globalThis;
  var P = g.PublicKeyCredential;
  var aborts = g.__passkiAborts || (g.__passkiAborts = {});
  function b64(buf) {
    var bytes = buf instanceof ArrayBuffer ? new Uint8Array(buf) : new Uint8Array(buf.buffer, buf.byteOffset, buf.byteLength);
    var s = '';
    for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
    return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  }
  function bin(s) {
    s = s.replace(/-/g, '+').replace(/_/g, '/');
    while (s.length % 4) s += '=';
    var raw = atob(s), out = new Uint8Array(raw.length);
    for (var i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
    return out.buffer;
  }
  function plain(v) {
    if (v instanceof ArrayBuffer || ArrayBuffer.isView(v)) return b64(v);
    if (Array.isArray(v)) return v.map(plain);
    if (v && typeof v === 'object') { var o = {}; for (var k in v) o[k] = plain(v[k]); return o; }
    return v;
  }
  function salts(v) { if (v) { if (v.first) v.first = bin(v.first); if (v.second) v.second = bin(v.second); } }
  function extensions(e) {
    if (!e) return;
    if (e.prf) { salts(e.prf.eval); var by = e.prf.evalByCredential; if (by) for (var k in by) salts(by[k]); }
    if (e.largeBlob && e.largeBlob.write) e.largeBlob.write = bin(e.largeBlob.write);
  }
  function descriptors(list) {
    return (list || []).map(function (d) { var c = Object.assign({}, d); c.id = bin(d.id); return c; });
  }
  function options(o) {
    o.challenge = bin(o.challenge);
    if (o.user) { o.user.id = bin(o.user.id); o.excludeCredentials = descriptors(o.excludeCredentials); }
    else o.allowCredentials = descriptors(o.allowCredentials);
    extensions(o.extensions);
    return o;
  }
  function response(c) {
    var r = c.response;
    var out = {
      id: c.id, rawId: b64(c.rawId), type: c.type,
      clientExtensionResults: plain(c.getClientExtensionResults ? c.getClientExtensionResults() : {}),
      response: { clientDataJSON: b64(r.clientDataJSON) }
    };
    if (c.authenticatorAttachment) out.authenticatorAttachment = c.authenticatorAttachment;
    if (r.attestationObject) {
      out.response.attestationObject = b64(r.attestationObject);
      out.response.transports = r.getTransports ? r.getTransports() : [];
    } else {
      out.response.authenticatorData = b64(r.authenticatorData);
      out.response.signature = b64(r.signature);
      if (r.userHandle && r.userHandle.byteLength) out.response.userHandle = b64(r.userHandle);
    }
    return JSON.stringify(out);
  }
  function capabilities() {
    var caps = { webauthn: !!(P && g.navigator && g.navigator.credentials), secureContext: !!g.isSecureContext };
    if (!P) return Promise.resolve(JSON.stringify(caps));
    var client = typeof P.getClientCapabilities === 'function' ? P.getClientCapabilities().catch(function () { return {}; }) : Promise.resolve({});
    var conditional = typeof P.isConditionalMediationAvailable === 'function' ? P.isConditionalMediationAvailable().catch(function () { return false; }) : Promise.resolve(false);
    return Promise.all([client, conditional]).then(function (r) {
      var c = r[0] || {};
      caps.conditionalGet = !!(c.conditionalGet || r[1]);
      caps.conditionalCreate = !!c.conditionalCreate;
      caps.immediateGet = !!c.immediateGet;
      ['signalUnknownCredential', 'signalAllAcceptedCredentials', 'signalCurrentUserDetails'].forEach(function (m) { caps[m] = typeof P[m] === 'function'; });
      return JSON.stringify(caps);
    });
  }
  function failed(e) { delete aborts[id]; fail(String((e && e.name) || 'Error'), String((e && e.message) || e)); }
  var promise;
  try {
    if (op === 'capabilities') {
      promise = capabilities();
    } else if (op === 'create' || op === 'get') {
      var controller = typeof AbortController === 'function' ? new AbortController() : null;
      if (controller) aborts[id] = controller;
      var request = { publicKey: options(JSON.parse(json)) };
      if (mediation) request.mediation = mediation;
      if (controller) request.signal = controller.signal;
      var credentials = g.navigator.credentials;
      promise = (op === 'create' ? credentials.create(request) : credentials.get(request)).then(function (c) {
        if (!c) throw { name: 'UnknownError', message: 'The browser returned no credential.' };
        return response(c);
      });
    } else {
      if (!P || typeof P[op] !== 'function') throw { name: 'NotSupportedError', message: 'PublicKeyCredential.' + op + ' is not available in this browser.' };
      promise = P[op](JSON.parse(json)).then(function () { return '{}'; });
    }
  } catch (e) {
    failed(e);
    return;
  }
  promise.then(function (result) { delete aborts[id]; ok(result); }, failed);
})()"""
