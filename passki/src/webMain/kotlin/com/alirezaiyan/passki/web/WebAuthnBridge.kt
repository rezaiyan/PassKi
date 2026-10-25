package com.alirezaiyan.passki.web

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** How a browser WebAuthn call ended: the JSON it produced, or the `DOMException` (or other error) name and message. */
internal sealed interface BridgeOutcome {
    class Success(val json: String) : BridgeOutcome
    class Failure(val name: String, val message: String) : BridgeOutcome
}

/**
 * Calls `navigator.credentials` and the `PublicKeyCredential` statics. Only strings cross the JS boundary (the
 * JavaScript side converts base64url to and from `ArrayBuffer`s), so the same code works on Kotlin/JS and Kotlin/Wasm.
 * Cancelling the calling coroutine aborts the browser request through its `AbortSignal`.
 */
internal object WebAuthnBridge {
    private val bridge: JsAny by lazy { createBridge() }

    /** The capabilities snapshot, as the JSON that [WebCapabilities.parse] reads. */
    suspend fun capabilities(): String = suspendCancellableCoroutine { cont ->
        jsCapabilities(bridge) { cont.resume(it) }
    }

    /** `navigator.credentials.create`; [mediation] is empty for the default (modal). */
    suspend fun create(optionsJson: String, mediation: String, onStart: (JsAny?) -> Unit = {}): BridgeOutcome =
        call(onStart) { onSuccess, onError -> jsCreate(bridge, optionsJson, mediation, onSuccess, onError) }

    /** `navigator.credentials.get`; [mediation] is empty for the default (modal). */
    suspend fun get(optionsJson: String, mediation: String, onStart: (JsAny?) -> Unit = {}): BridgeOutcome =
        call(onStart) { onSuccess, onError -> jsGet(bridge, optionsJson, mediation, onSuccess, onError) }

    /** `PublicKeyCredential.<method>(body)` from the WebAuthn Signal API. */
    suspend fun signal(method: String, bodyJson: String): BridgeOutcome =
        call({}) { onSuccess, onError -> jsSignal(bridge, method, bodyJson, onSuccess, onError) }

    /** Aborts a request started by [create] or [get], given the handle passed to its `onStart`. */
    fun abort(handle: JsAny?) {
        if (handle != null) jsAbort(handle)
    }

    private suspend fun call(
        onStart: (JsAny?) -> Unit,
        start: (onSuccess: (String) -> Unit, onError: (String, String) -> Unit) -> JsAny?,
    ): BridgeOutcome = suspendCancellableCoroutine { cont ->
        // A request aborted by cancellation still settles in JS; resuming a cancelled continuation is a no-op.
        val handle = start(
            { json -> cont.resume(BridgeOutcome.Success(json)) },
            { name, message -> cont.resume(BridgeOutcome.Failure(name, message)) },
        )
        onStart(handle)
        cont.invokeOnCancellation { abort(handle) }
    }
}

private fun jsCapabilities(bridge: JsAny, onDone: (String) -> Unit): Unit = js("bridge.capabilities(onDone)")

private fun jsCreate(
    bridge: JsAny,
    optionsJson: String,
    mediation: String,
    onSuccess: (String) -> Unit,
    onError: (String, String) -> Unit,
): JsAny? = js("bridge.create(optionsJson, mediation, onSuccess, onError)")

private fun jsGet(
    bridge: JsAny,
    optionsJson: String,
    mediation: String,
    onSuccess: (String) -> Unit,
    onError: (String, String) -> Unit,
): JsAny? = js("bridge.get(optionsJson, mediation, onSuccess, onError)")

private fun jsSignal(
    bridge: JsAny,
    method: String,
    bodyJson: String,
    onSuccess: (String) -> Unit,
    onError: (String, String) -> Unit,
): JsAny? = js("bridge.signal(method, bodyJson, onSuccess, onError)")

private fun jsAbort(handle: JsAny): Unit = js("handle.abort()")

/**
 * The JavaScript half of the bridge. Options arrive as canonical WebAuthn JSON (validated by WebAuthnOptions), so
 * binary members are unpadded base64url. Responses leave as RegistrationResponseJSON / AuthenticationResponseJSON.
 * Written without `toJSON()` / `parse*OptionsFromJSON()` so older browsers that lack them work the same.
 */
private fun createBridge(): JsAny = js(
    """(function () {
  function b64uToBuf(s) {
    s = String(s).replace(/-/g, '+').replace(/_/g, '/');
    while (s.length % 4) s += '=';
    var bin = atob(s), out = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out.buffer;
  }
  function bufToB64u(buf) {
    var bytes = buf instanceof ArrayBuffer ? new Uint8Array(buf) : new Uint8Array(buf.buffer, buf.byteOffset, buf.byteLength);
    var bin = '';
    for (var i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
    return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
  function isBinary(v) { return v instanceof ArrayBuffer || ArrayBuffer.isView(v); }
  function toJsonValue(v) {
    if (isBinary(v)) return bufToB64u(v);
    if (Array.isArray(v)) return v.map(toJsonValue);
    if (v && typeof v === 'object') { var o = {}; for (var k in v) o[k] = toJsonValue(v[k]); return o; }
    return v;
  }
  // WebIDL turns a null number into 0 (a timeout of 0 ms), so drop nulls rather than pass them on.
  function stripNulls(v) {
    if (Array.isArray(v)) return v.map(stripNulls);
    if (v && typeof v === 'object') {
      var o = {};
      for (var k in v) if (v[k] !== null) o[k] = stripNulls(v[k]);
      return o;
    }
    return v;
  }
  function descriptors(list) {
    return list.map(function (d) { var c = Object.assign({}, d); c.id = b64uToBuf(d.id); return c; });
  }
  function prfValues(v) {
    var o = { first: b64uToBuf(v.first) };
    if (v.second !== undefined) o.second = b64uToBuf(v.second);
    return o;
  }
  function extensions(ext) {
    var out = Object.assign({}, ext);
    if (ext.prf) {
      var prf = {};
      if (ext.prf.eval) prf.eval = prfValues(ext.prf.eval);
      if (ext.prf.evalByCredential) {
        prf.evalByCredential = {};
        for (var id in ext.prf.evalByCredential) prf.evalByCredential[id] = prfValues(ext.prf.evalByCredential[id]);
      }
      out.prf = prf;
    }
    if (ext.largeBlob && ext.largeBlob.write !== undefined) {
      out.largeBlob = Object.assign({}, ext.largeBlob, { write: b64uToBuf(ext.largeBlob.write) });
    }
    return out;
  }
  function creationOptions(json) {
    var o = stripNulls(JSON.parse(json));
    o.challenge = b64uToBuf(o.challenge);
    o.user = Object.assign({}, o.user, { id: b64uToBuf(o.user.id) });
    if (o.excludeCredentials) o.excludeCredentials = descriptors(o.excludeCredentials);
    if (o.extensions) o.extensions = extensions(o.extensions);
    return o;
  }
  function requestOptions(json) {
    var o = stripNulls(JSON.parse(json));
    o.challenge = b64uToBuf(o.challenge);
    if (o.allowCredentials) o.allowCredentials = descriptors(o.allowCredentials);
    if (o.extensions) o.extensions = extensions(o.extensions);
    return o;
  }
  function attempt(f) { try { return f(); } catch (e) { return undefined; } }
  function registrationJson(c) {
    var r = c.response;
    var response = {
      clientDataJSON: bufToB64u(r.clientDataJSON),
      attestationObject: bufToB64u(r.attestationObject),
      transports: (r.getTransports && attempt(function () { return r.getTransports(); })) || []
    };
    var authData = r.getAuthenticatorData && attempt(function () { return r.getAuthenticatorData(); });
    if (authData) response.authenticatorData = bufToB64u(authData);
    var publicKey = r.getPublicKey && attempt(function () { return r.getPublicKey(); });
    if (publicKey) response.publicKey = bufToB64u(publicKey);
    var alg = r.getPublicKeyAlgorithm && attempt(function () { return r.getPublicKeyAlgorithm(); });
    if (typeof alg === 'number') response.publicKeyAlgorithm = alg;
    return credentialJson(c, response);
  }
  function assertionJson(c) {
    var r = c.response;
    var response = {
      clientDataJSON: bufToB64u(r.clientDataJSON),
      authenticatorData: bufToB64u(r.authenticatorData),
      signature: bufToB64u(r.signature)
    };
    if (r.userHandle) response.userHandle = bufToB64u(r.userHandle);
    return credentialJson(c, response);
  }
  function credentialJson(c, response) {
    var id = bufToB64u(c.rawId);
    var out = { id: id, rawId: id, type: c.type, response: response, clientExtensionResults: {} };
    if (c.authenticatorAttachment) out.authenticatorAttachment = c.authenticatorAttachment;
    var ext = c.getClientExtensionResults && attempt(function () { return c.getClientExtensionResults(); });
    if (ext) out.clientExtensionResults = toJsonValue(ext);
    return JSON.stringify(out);
  }
  function supported() {
    return typeof PublicKeyCredential === 'function' && typeof navigator !== 'undefined' && !!navigator.credentials;
  }
  function unsupported() {
    var e = new Error('This browser does not support WebAuthn (no PublicKeyCredential), or the page is not a secure context (HTTPS or localhost).');
    e.name = 'NotSupportedError';
    return e;
  }
  function run(start, onSuccess, onError) {
    var controller = typeof AbortController === 'function' ? new AbortController() : null;
    function fail(e) {
      onError(String((e && e.name) || 'Error'), String((e && e.message) || e));
    }
    try {
      Promise.resolve(start(controller ? controller.signal : undefined)).then(function (json) { onSuccess(json); }, fail);
    } catch (e) {
      fail(e);
    }
    return controller;
  }
  function request(kind, json, mediation, signal) {
    if (!supported()) throw unsupported();
    var init = { publicKey: kind === 'create' ? creationOptions(json) : requestOptions(json) };
    if (mediation) init.mediation = mediation;
    if (signal) init.signal = signal;
    return navigator.credentials[kind](init);
  }
  function emptyResult() {
    var e = new Error('The browser returned no credential.');
    e.name = 'NotAllowedError';
    return e;
  }
  return {
    capabilities: function (onDone) {
      var caps = { supported: supported() };
      if (!caps.supported) { onDone(JSON.stringify(caps)); return; }
      ['signalUnknownCredential', 'signalAllAcceptedCredentials', 'signalCurrentUserDetails'].forEach(function (m) {
        caps[m] = typeof PublicKeyCredential[m] === 'function';
      });
      function call(name, fallback) {
        try {
          return typeof PublicKeyCredential[name] === 'function'
            ? Promise.resolve(PublicKeyCredential[name]()).catch(function () { return fallback; })
            : Promise.resolve(fallback);
        } catch (e) {
          return Promise.resolve(fallback);
        }
      }
      // Dictionary members are read in order, so mediation is checked before the throwing publicKey getter runs:
      // the sentinel comes back only when the browser accepts the value. No request is started either way.
      function mediationAccepted(value) {
        return new Promise(function (resolve) {
          var sentinel = new Error('passki-probe');
          try {
            var init = { mediation: value };
            Object.defineProperty(init, 'publicKey', { enumerable: true, get: function () { throw sentinel; } });
            Promise.resolve(navigator.credentials.get(init)).then(function () { resolve(false); }, function (e) { resolve(e === sentinel); });
          } catch (e) {
            resolve(e === sentinel);
          }
        });
      }
      Promise.all([
        call('getClientCapabilities', {}),
        call('isConditionalMediationAvailable', false),
        mediationAccepted('immediate')
      ]).then(function (r) {
        caps.client = r[0] || {};
        caps.conditionalMediation = !!r[1];
        caps.immediateMediation = !!r[2];
        onDone(JSON.stringify(caps));
      });
    },
    create: function (json, mediation, onSuccess, onError) {
      return run(function (signal) {
        return request('create', json, mediation, signal).then(function (c) {
          if (!c) throw emptyResult();
          return registrationJson(c);
        });
      }, onSuccess, onError);
    },
    get: function (json, mediation, onSuccess, onError) {
      return run(function (signal) {
        return request('get', json, mediation, signal).then(function (c) {
          if (!c) throw emptyResult();
          return assertionJson(c);
        });
      }, onSuccess, onError);
    },
    signal: function (method, json, onSuccess, onError) {
      run(function () {
        if (!supported() || typeof PublicKeyCredential[method] !== 'function') {
          var e = new Error('This browser does not support PublicKeyCredential.' + method + ' (WebAuthn Signal API).');
          e.name = 'NotSupportedError';
          throw e;
        }
        return Promise.resolve(PublicKeyCredential[method](JSON.parse(json))).then(function () { return ''; });
      }, onSuccess, onError);
      return null;
    }
  };
})()""",
)
