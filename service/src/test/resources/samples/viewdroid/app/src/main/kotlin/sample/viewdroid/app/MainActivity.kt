package sample.viewdroid.app

import sample.viewdroid.app.databinding.ActivityMainBinding

class MainActivity {
    private var binding: ActivityMainBinding? = null

    fun bind(): Any? {
        val bound = ActivityMainBinding.bind(null)
        binding = bound
        return bound.title
    }

    fun endpoint(): String = BuildConfig.ENDPOINT
}
