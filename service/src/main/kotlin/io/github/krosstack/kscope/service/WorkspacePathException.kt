package io.github.krosstack.kscope.service

/**
 * A path that names a file of a repository without the directory of the repository, in a workspace of several
 * repositories. The message lists the workspace paths that the file can be named by.
 */
public class WorkspacePathException(message: String) : Exception(message)
