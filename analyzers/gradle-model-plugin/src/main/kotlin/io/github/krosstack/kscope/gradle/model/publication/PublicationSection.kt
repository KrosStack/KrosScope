package io.github.krosstack.kscope.gradle.model.publication

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

/**
 * What the project publishes: its `group`, `name` and `version`, and the coordinates of each Maven publication of
 * the publishing extension. Null for a project that does not publish to Maven.
 */
internal object PublicationSection {
    fun describe(project: Project): Map<String, Any?>? = mavenPublicationsOf(project)
        ?.takeIf { it.isNotEmpty() || project.plugins.hasPlugin("maven-publish") }
        ?.let { publications ->
            linkedMapOf(
                "group" to project.group.toString(),
                "name" to project.name,
                "version" to project.version.toString(),
                "publications" to publications,
            )
        }

    private fun mavenPublicationsOf(project: Project): List<Map<String, String?>>? =
        project.extensions.findByType(PublishingExtension::class.java)
            ?.publications
            ?.withType(MavenPublication::class.java)
            ?.map { publication ->
                linkedMapOf(
                    "groupId" to publication.groupId,
                    "artifactId" to publication.artifactId,
                    "version" to publication.version,
                )
            }
            ?.sortedBy { "${it["groupId"]}:${it["artifactId"]}:${it["version"]}" }
}
