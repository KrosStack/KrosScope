package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.RelationKind

internal fun callFlowWorld(): SymbolWorld {
    val world = SymbolWorld()
    val checkout = world.type("Checkout")
    val ledger = world.type("Ledger")
    val pay = world.function("pay", checkout)
    val validate = world.function("validate", checkout)
    val log = world.function("log", checkout)
    val flush = world.function("flush", checkout)
    val record = world.function("record", ledger)
    val audit = world.function("audit", ledger)
    val main = world.function("main")
    val other = world.function("other")
    val entry = world.function("entry")
    world.call(main, pay, 10)
    world.call(pay, validate, 20)
    world.call(pay, record, 21)
    world.call(pay, validate, 22)
    world.unresolvedCall(pay, 23)
    world.notAnalysedCall(pay, 24)
    world.call(validate, log, 30)
    world.call(log, flush, 31)
    world.call(record, pay, 40)
    world.call(audit, validate, 50)
    world.call(other, main, 60)
    world.call(entry, other, 70)
    return world
}

internal fun typeFlowWorld(): SymbolWorld {
    val world = SymbolWorld()
    val base = world.type("Base")
    val payable = world.type("Payable", kind = "kotlin.interface")
    val checkout = world.type("Checkout")
    val pay = world.function("pay", checkout)
    val validate = world.function("validate", checkout)
    val total = world.property("total", checkout)
    val helper = world.function("helper", pay)
    val step = world.type("Step", checkout)
    val run = world.function("run", step)
    val ledger = world.type("Ledger")
    val record = world.function("record", ledger)
    val audit = world.type("Audit")
    val auditLog = world.function("log", audit)
    val report = world.type("Report")
    val render = world.function("render", report)
    val receipt = world.type("Receipt", module = "lib")
    val main = world.function("main")
    world.call(pay, record, 10)
    world.call(helper, record, 11)
    world.call(validate, pay, 12)
    world.call(render, pay, 13)
    world.call(run, pay, 14)
    world.call(main, pay, 15)
    world.call(record, auditLog, 16)
    world.unresolvedCall(pay, 17)
    world.relate(total, RelationKind.REFERENCES, receipt)
    world.relate(pay, RelationKind.TYPE_USES, receipt)
    world.relate(checkout, RelationKind.EXTENDS, base)
    world.relate(checkout, RelationKind.IMPLEMENTS, payable)
    return world
}

internal fun chainWorld(length: Int): SymbolWorld {
    val world = SymbolWorld()
    val functions = (0 until length).map { world.function("f${it.toString().padStart(4, '0')}") }
    functions.zipWithNext().forEachIndexed { index, (from, to) -> world.call(from, to, index + 1) }
    return world
}

internal fun hubWorld(sources: Int): SymbolWorld {
    val world = SymbolWorld()
    val hub = world.type("Hub")
    val members = (0 until 5).map { world.function("m$it", hub) }
    for (number in 0 until sources) {
        val source = world.type("S${number.toString().padStart(3, '0')}")
        world.call(world.function("run", source), members[number % members.size], number + 1)
    }
    return world
}

internal fun wideWorld(members: Int): SymbolWorld {
    val world = SymbolWorld()
    val wide = world.type("Wide")
    repeat(members) { number ->
        val name = number.toString().padStart(2, '0')
        val target = world.type("T$name")
        world.call(world.function("f$name", wide), world.function("run", target), number + 1)
    }
    return world
}

internal fun constructionWorld(): SymbolWorld {
    val world = SymbolWorld()
    val build = world.function("build", world.type("Factory"))
    val widget = world.type("Widget")
    world.call(build, widget, 10)
    world.call(build, world.property("size", widget), 11)
    world.call(build, world.function("make", widget), 12)
    return world
}
