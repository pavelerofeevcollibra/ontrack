package net.nemerosa.ontrack.kdsl.connector.client.impl

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.kdsl.connector.client.GraphQLResponse
import net.nemerosa.ontrack.kdsl.connector.client.OntrackConnector
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.util.UriComponentsBuilder

class HttpOntrackConnector(
        override val url: String,
        username: String,
        password: String
) : OntrackConnector {

    private val restTemplate = RestTemplateBuilder()
            .rootUri(url)
            .basicAuthentication(username, password)
            .build()

    override fun download(path: String): ByteArray {
        return restTemplate.getForObject(
                "/$path",
                ByteArray::class.java
        ) ?: throw RuntimeException("Cannot download anything at $path")
    }

    override fun get(path: String): JsonNode? {
        return try {
            restTemplate.getForObject(
                    "/$path",
                    JsonNode::class.java
            )
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND) {
                null
            } else {
                throw ex
            }
        }
    }

    override fun get(path: String, query: Map<String, Any>): JsonNode? {
        return try {
            val builder = query.entries.fold(UriComponentsBuilder.fromPath(path)) { r, t ->
                r.queryParam(t.key, t.value)
            }
            restTemplate.getForObject(
                    "/${builder.build().toUriString()}",
                    JsonNode::class.java
            )
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND) {
                null
            } else {
                throw ex
            }
        }
    }

    override fun post(path: String, payload: Any?): JsonNode? =
            restTemplate.postForObject(
                    "/$path",
                    payload,
                    JsonNode::class.java
            )

    override fun put(path: String, payload: Any) {
        restTemplate.put(
                "/$path",
                payload,
                JsonNode::class.java
        )
    }

    override fun delete(path: String) {
        restTemplate.delete(
                "/$path"
        )
    }

    override fun graphQL(query: String, variables: Map<String, Any?>): GraphQLResponse =
            restTemplate.postForObject(
                    "/graphql",
                    mapOf(
                            "query" to query,
                            "variables" to variables
                    ),
                    GraphQLResponse::class.java
            ) ?: throw RuntimeException("Cannot get any response from GraphQL end point")
}