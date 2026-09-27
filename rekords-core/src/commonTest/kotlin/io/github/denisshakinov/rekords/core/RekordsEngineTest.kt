package io.github.denisshakinov.rekords.core

import io.github.denisshakinov.rekords.test.TestRekordsSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Which engine a store is created with when it is not told which. */
class RekordsEngineTest {

    private data object FirstEngine : RekordsEngine<RekordsEngineConfig> {
        override fun create(block: RekordsEngineConfig.() -> Unit): RekordsEditor = error("Not created")
    }

    private data object SecondEngine : RekordsEngine<RekordsEngineConfig> {
        override fun create(block: RekordsEngineConfig.() -> Unit): RekordsEditor = error("Not created")
    }

    @Test
    fun the_one_engine_available_is_the_one_selected() {
        assertSame(expected = FirstEngine, actual = selectEngine(listOf(FirstEngine)))
    }

    /** No engine is a missing dependency, which the message says how to add. */
    @Test
    fun no_engine_fails_telling_to_add_one() {
        val failure = assertFailsWith<IllegalStateException> { selectEngine(emptyList()) }
        assertTrue("rekords-sqlite" in failure.message.orEmpty(), failure.message)
    }

    /**
     * Several engines are not chosen between, which would leave the storage to the order they are
     * loaded in: the message names them, and how to settle on one.
     */
    @Test
    fun several_engines_fail_naming_them() {
        val failure = assertFailsWith<IllegalStateException> { selectEngine(listOf(FirstEngine, SecondEngine)) }
        val message = failure.message.orEmpty()
        assertTrue("FirstEngine, SecondEngine" in message, message)
        assertTrue("RekordsStore(schema, FirstEngine)" in message, message)
    }

    /** A target depending on no engine - as this module's tests do - has no store to create. */
    @Test
    fun store_without_an_engine_available_cannot_be_created() {
        val failure = assertFailsWith<IllegalStateException> { RekordsStore(TestRekordsSchema()) }
        assertEquals(expected = true, actual = failure.message?.startsWith("No rekords engine"))
    }
}
