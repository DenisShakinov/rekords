package io.github.denisshakinov.rekords.crypto

@JsModule("@noble/ciphers/aes.js")
@JsNonModule
private external object NobleAes : JsAny

@JsModule("@noble/hashes/hmac.js")
@JsNonModule
private external object NobleHmac : JsAny

@JsModule("@noble/hashes/sha2.js")
@JsNonModule
private external object NobleSha2 : JsAny

internal actual val nobleAes: JsAny get() = NobleAes

internal actual val nobleHmac: JsAny get() = NobleHmac

internal actual val nobleSha2: JsAny get() = NobleSha2
