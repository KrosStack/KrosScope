package sample.viewdroid.app;

import sample.viewdroid.app.databinding.ActivityMainBinding;

public class Legacy {
    public Object title(ActivityMainBinding binding) {
        return binding.title;
    }

    public boolean verbose() {
        return BuildConfig.VERBOSE && BuildConfig.DEBUG;
    }
}
