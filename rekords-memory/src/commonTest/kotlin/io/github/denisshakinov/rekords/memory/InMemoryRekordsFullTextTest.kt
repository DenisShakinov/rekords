package io.github.denisshakinov.rekords.memory

import io.github.denisshakinov.rekords.test.RekordsFullTextTest
import kotlin.test.Test

/** Runs the shared full text suite against the in-memory editor. */
class InMemoryRekordsFullTextTest : RekordsFullTextTest(InMemoryRekordsEditor()) {

    @Test
    override fun check_full_text_finds_every_word_as_a_prefix_in_any_order() = super.check_full_text_finds_every_word_as_a_prefix_in_any_order()

    @Test
    override fun check_full_text_looks_into_every_field_given() = super.check_full_text_looks_into_every_field_given()

    @Test
    override fun check_full_text_combines_with_other_filters() = super.check_full_text_combines_with_other_filters()

    @Test
    override fun check_full_text_follows_the_rekords_written() = super.check_full_text_follows_the_rekords_written()

    @Test
    override fun check_full_text_on_a_field_not_marked_fails() = super.check_full_text_on_a_field_not_marked_fails()

    @Test
    override fun check_full_text_is_kept_by_migrations() = super.check_full_text_is_kept_by_migrations()
}
