package io.github.krosstack.kscope.model

import kotlinx.serialization.Serializable

/** Stable per repository; never derived from the checkout location. */
@Serializable
@JvmInline
public value class RepositoryId(public val value: String)

/** Format: `<repositoryId>//<repository-relative directory>`, with an empty directory for the repository root. */
@Serializable
@JvmInline
public value class BuildRootId(public val value: String)

/** Format: `<buildRootId>:<gradle path>`, for example `repo//:core:model`. */
@Serializable
@JvmInline
public value class ModuleId(public val value: String)

/** Format: `<moduleId>@<name>`, for example `repo//:core:model@commonMain`. */
@Serializable
@JvmInline
public value class SourceSetId(public val value: String)

/** Format: `<moduleId>#target/<name>`. */
@Serializable
@JvmInline
public value class TargetId(public val value: String)

/** Format: `<moduleId>#variant/<name>`. */
@Serializable
@JvmInline
public value class VariantId(public val value: String)

/** Lowercase hexadecimal SHA-256 of the content. */
@Serializable
@JvmInline
public value class ContentHash(public val value: String)

/** Hash of every input of an ownership unit. */
@Serializable
@JvmInline
public value class Fingerprint(public val value: String)
