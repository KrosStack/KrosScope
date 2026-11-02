package io.github.krosstack.kscope.service.trust

/** The file that records trusted workspaces cannot be used, so trust was neither granted nor revoked. */
public class TrustStoreException(message: String) : IllegalStateException(message)
