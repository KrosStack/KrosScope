package io.github.krosstack.kscope.cli.serve.token

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.UserPrincipal
import java.util.EnumSet

/** The access control list of a file that only the account of this process may use. */
internal object AccountOnlyAcl {
    /** Replaces the access control list of [file] with one entry for the account of this process, and reads it back. */
    fun restrict(file: Path) {
        val view = Files.getFileAttributeView(file, AclFileAttributeView::class.java)
            ?: throw IOException("$file has no access control list.")
        val account = currentAccount(file)
        view.acl = listOf(fullControlFor(account))
        if (!grantsOnlyTo(view.acl, account)) {
            throw IOException("The access control list of $file still grants access to other accounts.")
        }
    }

    fun grantsOnlyTo(entries: List<AclEntry>, account: UserPrincipal): Boolean =
        entries.isNotEmpty() && entries.all { it.type() == AclEntryType.ALLOW && it.principal() == account }

    private fun fullControlFor(account: UserPrincipal): AclEntry = AclEntry.newBuilder()
        .setType(AclEntryType.ALLOW)
        .setPrincipal(account)
        .setPermissions(EnumSet.allOf(AclEntryPermission::class.java))
        .build()

    private fun currentAccount(file: Path): UserPrincipal {
        val name = ProcessHandle.current().info().user().orElseGet { System.getProperty("user.name") }
        return file.fileSystem.userPrincipalLookupService.lookupPrincipalByName(name)
    }
}
