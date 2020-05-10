package net.nemerosa.ontrack.extension.indicators.ui.graphql

import graphql.Scalars.GraphQLInt
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.extension.indicators.model.IndicatorStatus
import net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorStats
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.intField
import org.springframework.stereotype.Component
import kotlin.reflect.KProperty1

@Component
class GQLTypeIndicatorStats(
        private val scaleValues: GQLTypeScaleValues
) : GQLType {

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
            GraphQLObjectType.newObject()
                    .name(typeName)
                    .description("Aggregation of statuses over several items.")
                    .intField(IndicatorStats::total, "Total number of items used for this stat")
                    .intField(IndicatorStats::count, "Number of items having an actual usable value for stat computation")
                    .statField(IndicatorStats::min, "Minimal value (undefined if no stat available)")
                    .statField(IndicatorStats::avg, "Average value (undefined if no stat available)")
                    .statField(IndicatorStats::max, "Maximal value (undefined if no stat available)")
                    .intField(IndicatorStats::minCount, "Number of items having the minimum value")
                    .intField(IndicatorStats::maxCount, "Number of items having the maximum value")
                    .scaleValues(scaleValues, IndicatorStats::min, "Scales for the min value")
                    .scaleValues(scaleValues, IndicatorStats::avg, "Scales for the min value")
                    .scaleValues(scaleValues, IndicatorStats::max, "Scales for the min value")
                    .build()

    override fun getTypeName(): String = IndicatorStats::class.java.simpleName

    private fun GraphQLObjectType.Builder.statField(property: KProperty1<IndicatorStats, IndicatorStatus?>, description: String): GraphQLObjectType.Builder =
            field {
                it.name(property.name)
                        .description(description)
                        .type(GraphQLInt)
                        .dataFetcher { env ->
                            val stats = env.getSource<IndicatorStats>()
                            property.get(stats)?.value
                        }
            }

    fun GraphQLObjectType.Builder.scaleValues(scaleValues: GQLTypeScaleValues, property: KProperty1<IndicatorStats, IndicatorStatus?>, description: String): GraphQLObjectType.Builder =
            field {
                it.name("${property.name}Scales")
                        .description(description)
                        .type(scaleValues.typeRef)
                        .dataFetcher { env ->
                            val stats = env.getSource<IndicatorStats>()
                            property.get(stats)
                        }
            }

}
