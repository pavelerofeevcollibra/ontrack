package net.nemerosa.ontrack.kdsl.model

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.dsl.ProjectEntity
import net.nemerosa.ontrack.dsl.Signature
import net.nemerosa.ontrack.kdsl.client.OntrackConnector
import net.nemerosa.ontrack.kdsl.core.parseInto
import net.nemerosa.ontrack.kdsl.model.support.signature
import kotlin.reflect.KClass

abstract class KDSLProjectEntity(
        json: JsonNode,
        ontrackConnector: OntrackConnector
) : KDSLEntity(json, ontrackConnector), ProjectEntity {

    /**
     * Entity type
     */
    abstract val entityType: String

    override val signature: Signature by lazy { json.signature }

    override fun setProperty(type: String, value: Any) {
        ontrackConnector.put(
                "properties/$entityType/$id/$type/edit",
                value
        )
    }

    override fun <T : Any> getProperty(kClass: KClass<T>, type: String): T? =
            ontrackConnector.get(
                    "properties/$entityType/$id/$type/view"
            )?.get("value")?.parseInto(kClass)
}