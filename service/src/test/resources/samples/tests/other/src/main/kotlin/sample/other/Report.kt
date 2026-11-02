package sample.other

import sample.tests.Greeter

class Report {
    fun title(): String = Greeter().greet("report")
}
