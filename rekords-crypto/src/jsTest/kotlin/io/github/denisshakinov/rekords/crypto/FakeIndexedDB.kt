package io.github.denisshakinov.rekords.crypto

@JsModule("fake-indexeddb")
@JsNonModule
private external object FakeIndexedDB : JsAny

actual fun installFakeIndexedDB() = install(FakeIndexedDB)
