package net.nemerosa.ontrack.kdsl.model

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.kdsl.core.deepPath
import net.nemerosa.ontrack.kdsl.core.parseInto
import net.nemerosa.ontrack.kdsl.core.removeDeepPath
import net.nemerosa.ontrack.kdsl.core.support.DSLException
import net.nemerosa.ontrack.kdsl.core.toJson
import kotlin.reflect.KClass

/**
 * Entity resource linked to a project
 *
 * @property creation Creation time and author
 */
abstract class ProjectEntityResource(
        id: Int,
        val creation: Signature
) : EntityResource(id) {

    /**
     * Entity type
     */
    abstract val entityType: String

    /**
     * Project ID
     */
    abstract val projectId: Int

    /**
     * Gets the project associated with this entity
     */
    val project: Project by lazy {
        ontrack.getProjectByID(projectId)
    }

    /**
     * Sets a property on this entity
     */
    fun setProperty(type: String, value: Any) {
        ontrackConnector.put(
                "properties/$entityType/$id/$type/edit",
                value
        )
    }

    /**
     * Gets a property on this entity
     */
    inline fun <reified T : Any> getProperty(type: String): T? =
            getProperty(T::class, type)

    /**
     * Gets a property on this entity
     */
    fun <T : Any> getProperty(kClass: KClass<T>, type: String): T? {
        return ontrackConnector.get(
                "properties/$entityType/$id/$type/view"
        )?.get("value")?.parseInto(kClass)
    }

}

fun JsonNode?.adaptProjectId(projectPath: String = "project"): JsonNode? {
    return if (this is ObjectNode) {
        val projectNode = deepPath(projectPath)
        when {
            projectNode.isMissingNode || projectNode.isNull -> throw NoProjectIdException()
            else -> {
                val projectId = projectNode.get("id")
                if (projectId == null || projectId.isNull || !projectId.isInt) {
                    throw NoProjectIdException()
                } else {
                    val id = projectId.asInt()
                    this.removeDeepPath(projectPath)
                    this.set("projectId", IntNode(id))
                }
            }
        }
    } else {
        this
    }
}

fun JsonNode?.adaptSignature(): JsonNode? {
    return when (this) {
        is ObjectNode -> {
            val signatureNode = get("signature")
            when {
                signatureNode == null -> this
                signatureNode.isNull -> this
                else -> {
                    remove("signature")
                    val time = signatureNode.path("time").asText()
                    val user = signatureNode.path("user").path("name").asText()
                    set("creation", mapOf(
                            "time" to time,
                            "user" to user
                    ).toJson())
                }
            }
        }
        else -> this
    }
}

class NoProjectIdException : DSLException("No project.id found in JSON node")
