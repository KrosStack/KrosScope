package io.github.krosstack.kscope.service.map.tree

/** The libraries that edges reach, one group per Maven group under one node; other coordinates go under `other`. */
internal class LibraryTree {
    fun attach(root: TreeNode, coordinates: Collection<String>): Map<String, TreeNode> {
        if (coordinates.isEmpty()) return emptyMap()
        val libraries = root.child("L:", "libraries", "Libraries")
        val groups = HashMap<String, TreeNode>()
        return coordinates.sorted().associateWith { coordinate ->
            val (group, label) = split(coordinate)
            groups.getOrPut(group) { libraries.child("g:$group", "library-group", group) }
                .child("x:$coordinate", "library", label)
        }
    }

    private fun split(coordinate: String): Pair<String, String> {
        val parts = coordinate.split(':')
        return if (parts[0] == MAVEN && parts.size >= MAVEN_PARTS) parts[1] to parts[2] else OTHER to coordinate
    }

    private companion object {
        const val MAVEN = "maven"
        const val MAVEN_PARTS = 3
        const val OTHER = "other"
    }
}
