# Android reflectively creates these components; Room and serialization ship their own rules.
-keep class dev.bgcheck.network.data.service.** { *; }
-keep class dev.bgcheck.network.ui.** extends android.app.Activity { *; }
-keep class dev.bgcheck.network.data.worker.** { *; }
