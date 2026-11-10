package io.github.denisshakinov.rekords.sample

import io.github.denisshakinov.rekords.core.Field
import io.github.denisshakinov.rekords.core.Filter
import io.github.denisshakinov.rekords.core.Rekord
import io.github.denisshakinov.rekords.core.RekordsMigrationEditor
import io.github.denisshakinov.rekords.core.RekordsSchema
import io.github.denisshakinov.rekords.core.RekordsStore
import io.github.denisshakinov.rekords.core.delete
import kotlin.reflect.KClass

/** A project numbered as it is first put: `0` until it is. */
@Rekord(type = "project")
data class ProjectRekord(
    @Field(name = ID, id = true, generated = true)
    val id: Long = 0,
    @Field(name = NAME)
    val name: String,
) {
    override fun toString() = "#$id $name"

    companion object {
        const val ID = "id"
        const val NAME = "name"
    }
}

/** A task given a UUID as it is first put: `""` until it is. */
@Rekord(type = "task")
data class TaskRekord(
    @Field(name = ID, id = true, generated = true)
    val id: String = "",
    @Field(name = TITLE)
    val title: String,
    @Field(name = PROJECT)
    val project: ProjectRekord,
) {
    override fun toString() = "$id \"$title\" in $project"

    companion object {
        const val ID = "id"
        const val TITLE = "title"
        const val PROJECT = "project"
    }
}

class TasksSchema : RekordsSchema {

    override val version: Int = 1

    override val rekordTypes: List<KClass<*>> = listOf(ProjectRekord::class, TaskRekord::class)

    override suspend fun RekordsMigrationEditor.onUpgrade(oldVersion: Int, newVersion: Int) = Unit
}

/** Ids given to rekords as they are put, handed back with the rekords stored. */
suspend fun generatedIds() {
    section("Generated ids")
    val store = RekordsStore(TasksSchema()) {
        name = "tasks.db"
    }
    store.delete<TaskRekord>().getOrThrow()
    store.delete<ProjectRekord>().getOrThrow()

    val home: ProjectRekord = store.putRekord(ProjectRekord(name = "Home")).getOrThrow()
    show("Project numbered", home)

    // The project held is numbered along with the task holding it.
    val write: TaskRekord = store
        .putRekord(TaskRekord(title = "Write the report", project = ProjectRekord(name = "Work")))
        .getOrThrow()
    show("Task and its project", write)

    // A project already numbered is held as it is, not added again.
    val tasks: List<TaskRekord> = store
        .putRekords(listOf(TaskRekord(title = "Water the plants", project = home), TaskRekord(title = "Cook", project = home)))
        .getOrThrow()
    show("Tasks at home", tasks)
    show("Projects", store.count<ProjectRekord>().getOrThrow())

    // A rekord holding its id is updated as any other.
    store.putRekord(write.copy(title = "Send the report")).getOrThrow()
    show("Updated", store.getRekord<TaskRekord>(Filter.Equals(TaskRekord.ID, write.id)).getOrThrow())
}
