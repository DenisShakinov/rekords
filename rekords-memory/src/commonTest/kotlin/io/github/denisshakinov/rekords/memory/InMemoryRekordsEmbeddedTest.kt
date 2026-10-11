package io.github.denisshakinov.rekords.memory

import io.github.denisshakinov.rekords.test.RekordsEmbeddedTest
import kotlin.test.Test

/** Runs the shared suite of embedded classes against the in-memory editor. */
class InMemoryRekordsEmbeddedTest : RekordsEmbeddedTest(InMemoryRekordsEditor()) {

    @Test
    override fun check_embedded_classes_are_read_back_as_written() = super.check_embedded_classes_are_read_back_as_written()

    @Test
    override fun check_rekord_written_again_under_the_same_embedded_id_is_updated() = super.check_rekord_written_again_under_the_same_embedded_id_is_updated()

    @Test
    override fun check_rekord_is_deleted_by_its_embedded_id() = super.check_rekord_is_deleted_by_its_embedded_id()

    @Test
    override fun check_filter_looking_into_an_embedded_class_selects_by_its_fields() = super.check_filter_looking_into_an_embedded_class_selects_by_its_fields()

    @Test
    override fun check_filter_naming_the_fields_of_an_embedded_class_selects_by_them() = super.check_filter_naming_the_fields_of_an_embedded_class_selects_by_them()

    @Test
    override fun check_rekords_are_ordered_by_the_fields_of_an_embedded_class() = super.check_rekords_are_ordered_by_the_fields_of_an_embedded_class()
}
