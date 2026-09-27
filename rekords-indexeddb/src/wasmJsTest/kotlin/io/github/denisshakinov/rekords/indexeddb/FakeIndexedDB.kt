package io.github.denisshakinov.rekords.indexeddb

@JsModule("fake-indexeddb")
private external object FakeIndexedDB : JsAny

actual fun installFakeIndexedDB() = install(FakeIndexedDB)
