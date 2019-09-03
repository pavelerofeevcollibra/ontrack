package net.nemerosa.ontrack.extension.sonarqube

import net.nemerosa.ontrack.extension.sonarqube.configuration.SonarQubeConfiguration
import net.nemerosa.ontrack.extension.sonarqube.configuration.SonarQubeConfigurationService
import net.nemerosa.ontrack.extension.sonarqube.property.SonarQubeProperty
import net.nemerosa.ontrack.extension.sonarqube.property.SonarQubePropertyType
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SonarQubeIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var sonarQubeConfigurationService: SonarQubeConfigurationService

    @Test
    fun `Adding a new SonarQube configuration`() {
        withDisabledConfigurationTest {
            val name = uid("S")
            asUserWith<GlobalSettings> {
                val saved = sonarQubeConfigurationService.newConfiguration(
                        SonarQubeConfiguration(
                                name,
                                "https://sonarqube.nemerosa.net",
                                "my-ultra-secret-token"
                        )
                )
                assertEquals(name, saved.name)
                assertEquals("https://sonarqube.nemerosa.net", saved.url)
                assertEquals("", saved.password)
                // Gets the list of configurations
                val configs = sonarQubeConfigurationService.configurations
                // Checks we find the one we just created
                assertNotNull(
                        configs.find {
                            it.name == name
                        }
                )
                // Getting it by name
                val found = sonarQubeConfigurationService.getConfiguration(name)
                assertEquals(name, found.name)
                assertEquals("https://sonarqube.nemerosa.net", found.url)
                assertEquals("my-ultra-secret-token", found.password)
            }
        }
    }

    @Test
    fun `Project SonarQube property`() {
        withDisabledConfigurationTest {
            // Creates a configuration
            val name = uid("S")
            val configuration = asUserWith<GlobalSettings, SonarQubeConfiguration> {
                sonarQubeConfigurationService.newConfiguration(
                        SonarQubeConfiguration(
                                name,
                                "https://sonarqube.nemerosa.net",
                                "my-ultra-secret-token"
                        )
                )
            }
            project {
                // Property
                setProperty(this, SonarQubePropertyType::class.java,
                        SonarQubeProperty(
                                configuration,
                                "my:key"
                        )
                )
                // Gets the property back
                val property: SonarQubeProperty? = getProperty(this, SonarQubePropertyType::class.java)
                assertNotNull(property) {
                    assertEquals(name, it.configuration.name)
                    assertEquals("https://sonarqube.nemerosa.net", it.configuration.url)
                    assertEquals("my-ultra-secret-token", it.configuration.password)
                    assertEquals("my:key", it.key)
                    assertEquals("https://sonarqube.nemerosa.net/dashboard?id=my%3Akey", it.projectUrl)
                }
            }
        }
    }

}