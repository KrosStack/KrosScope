package rel.imports

import java.util.*
import kotlin.collections.*
import rel.imports.helpers.*
import rel.imports.helpers.Constants.*
import rel.imports.helpers.help as assist
import rel.imports.nowhere.*

class Consumer {
    fun use(): Int = assist() + assist("x") + SIZE + make().hashCode() + Tool().hashCode() + ArrayList<Int>().size
}
