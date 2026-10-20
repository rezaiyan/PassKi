package com.alirezaiyan.passki.internal

import com.alirezaiyan.passki.InternalPasskiApi
import kotlin.io.encoding.Base64

/**
 * base64url without padding, as WebAuthn JSON requires.
 *
 * Decoding is lenient: it accepts the base64url alphabet, the standard alphabet (`+` `/`),
 * a mix of both, and any number of trailing `=` padding characters.
 */
@InternalPasskiApi
public object Base64Url {
    private val codec = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    public fun encode(bytes: ByteArray): String = codec.encode(bytes)

    public fun decode(value: String): ByteArray =
        codec.decode(value.replace('+', '-').replace('/', '_').trimEnd('='))
}
