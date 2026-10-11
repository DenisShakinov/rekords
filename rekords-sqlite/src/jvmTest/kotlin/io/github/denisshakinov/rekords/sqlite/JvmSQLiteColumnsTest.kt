package io.github.denisshakinov.rekords.sqlite

import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.test.TestCollidingColumnsRekord
import io.github.denisshakinov.rekords.test.TestCollidingColumnsSchema
import io.github.denisshakinov.rekords.test.TestGameStateRekord
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class JvmSQLiteColumnsTest {

    @Test
    fun fields_kept_in_the_same_column_are_named_in_the_failure() = runTest {
        val databaseFile = Files.createTempDirectory("rekords").toFile().also { it.deleteOnExit() }.resolve("columns.db")
        val store = RekordsStore(TestCollidingColumnsSchema(), SQLite.create { name = databaseFile.path })

        val failure: Throwable? = store.putRekord(
            TestCollidingColumnsRekord(id = 1, stateId = 3, state = TestGameStateRekord(id = 3, name = null))
        ).exceptionOrNull()

        assertIs<IllegalArgumentException>(failure)
        assertContains(failure.message.orEmpty(), "state_id and state of colliding_columns")
    }
}
