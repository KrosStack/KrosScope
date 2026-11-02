package sample.tasktide.app.debug

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import sample.tasktide.app.BuildConfig
import sample.tasktide.app.R
import sample.tasktide.core.model.PlatformInfo

class DiagnosticsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            TextView(this).apply {
                text = getString(R.string.diagnostics_summary, BuildConfig.APPLICATION_ID, BuildConfig.VERSION_NAME, PlatformInfo.name)
            },
        )
    }
}
