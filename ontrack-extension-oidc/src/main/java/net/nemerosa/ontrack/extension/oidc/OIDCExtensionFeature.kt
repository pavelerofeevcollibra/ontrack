package net.nemerosa.ontrack.extension.oidc

import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import net.nemerosa.ontrack.model.extension.ExtensionFeatureOptions
import org.springframework.stereotype.Component

@Component
class OIDCExtensionFeature : AbstractExtensionFeature(
        "oidc",
        "OIDC",
        "Support for OIDC authentication",
        ExtensionFeatureOptions.DEFAULT.withGui(true)
)