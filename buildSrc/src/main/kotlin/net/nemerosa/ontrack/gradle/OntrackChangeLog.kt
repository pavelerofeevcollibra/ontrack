package net.nemerosa.ontrack.gradle

import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

open class OntrackChangeLog : AbstractOntrackTask() {

    @Input
    var ontrackProject: String = "ontrack"

    @Input
    var ontrackReleasePromotionLevel: String = "RELEASE"

    @Input
    var ontrackReleaseBranch: String = ""

    @Input
    var ontrackReleaseFilter: String = ""

    var changeLog: String = ""

    @TaskAction
    fun run() {
        if (ontrackReleaseBranch.isBlank()) throw GradleException("Missing ontrackReleaseBranch property")
        logger.info("Getting the Ontrack log for $ontrackProject since last $ontrackReleasePromotionLevel on branch $ontrackReleaseBranch")
        // Gets the Ontrack client
        val ontrack = getOntrackClient()
        // Gest the Ontrack project
        val project = ontrack.project(ontrackProject)
        // Gets the last build on the branch to release
        logger.info("ontrackReleaseBranch = $ontrackReleaseBranch")
        val lastBuild = project.search(mapOf("branchName" to ontrackReleaseBranch))[0]
        // Gets the last release
        val lastRelease = project.search(mapOf(
                "branchName" to ontrackReleaseFilter,
                "promotionName" to ontrackReleasePromotionLevel))[0]
        // Gets the change log
        val changeLog = lastBuild.getChangeLog(lastRelease)
        // Exports the issues
        this.changeLog = changeLog.exportIssues(mapOf(
                "format" to "text",
                "groups" to mapOf(
                        "Features" to listOf("type: feature"),
                        "Enhancements" to listOf("type: enhancement"),
                        "Bugs" to listOf("type: bug")
                ),
                "exclude" to emptyList<String>()
        ))
    }

}
