package net.nemerosa.ontrack.graphql.schema;

import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLTypeReference;
import net.nemerosa.ontrack.model.structure.ProjectEntityType;
import net.nemerosa.ontrack.model.structure.Signature;
import net.nemerosa.ontrack.model.structure.ValidationRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static graphql.Scalars.GraphQLInt;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static net.nemerosa.ontrack.graphql.support.GraphqlUtils.stdList;

@Component
public class GQLTypeValidationRun extends AbstractGQLProjectEntity<ValidationRun> {

    public static final String VALIDATION_RUN = "ValidationRun";

    private final GQLTypeValidationRunStatus validationRunStatus;
    private final GQLProjectEntityInterface projectEntityInterface;

    @Autowired
    public GQLTypeValidationRun(GQLTypeCreation creation,
                                GQLTypeValidationRunStatus validationRunStatus,
                                List<GQLProjectEntityFieldContributor> projectEntityFieldContributors,
                                GQLProjectEntityInterface projectEntityInterface
    ) {
        super(ValidationRun.class, ProjectEntityType.VALIDATION_RUN, projectEntityFieldContributors, creation);
        this.validationRunStatus = validationRunStatus;
        this.projectEntityInterface = projectEntityInterface;
    }

    @Override
    public GraphQLTypeReference getTypeRef() {
        return new GraphQLTypeReference(VALIDATION_RUN);
    }

    @Override
    public GraphQLObjectType createType(GQLTypeCache cache) {
        return newObject()
                .name(VALIDATION_RUN)
                .withInterface(projectEntityInterface.getTypeRef())
                .fields(projectEntityInterfaceFields())
                // Build
                .field(
                        newFieldDefinition()
                                .name("build")
                                .description("Associated build")
                                .type(new GraphQLNonNull(new GraphQLTypeReference(GQLTypeBuild.BUILD)))
                                .build()
                )
                // Promotion level
                .field(
                        newFieldDefinition()
                                .name("validationStamp")
                                .description("Associated validation stamp")
                                .type(new GraphQLNonNull(new GraphQLTypeReference(GQLTypeValidationStamp.Companion.getVALIDATION_STAMP())))
                                .build()
                )
                // Run order
                .field(
                        newFieldDefinition()
                                .name("runOrder")
                                .description("Run order")
                                .type(GraphQLInt)
                                .build()
                )
                // Validation statuses
                .field(
                        newFieldDefinition()
                                .name("validationRunStatuses")
                                .description("List of validation statuses")
                                .type(stdList(validationRunStatus.getTypeRef()))
                                .build()
                )
                // OK
                .build();

    }

    @Override
    protected Optional<Signature> getSignature(ValidationRun entity) {
        return Optional.ofNullable(entity.getLastStatus().getSignature());
    }
}
