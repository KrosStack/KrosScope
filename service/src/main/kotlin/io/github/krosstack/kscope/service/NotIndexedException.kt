package io.github.krosstack.kscope.service

/**
 * A query was made on a workspace without a current index that has files in it. Nothing was created or read; a scan
 * builds the index.
 */
public class NotIndexedException(public val indexLocation: String) :
    IllegalStateException("The workspace has no index at $indexLocation")
