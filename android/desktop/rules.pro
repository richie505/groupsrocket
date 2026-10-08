# ProGuard drops library code the app never calls. The app reads JSON as JsonElement (no reflection).
-keep class com.appsc.prep.desktop.MainKt { *; }
-dontwarn kotlinx.serialization.**
-dontwarn org.jetbrains.annotations.**
# ProGuard's method specialization breaks kotlinx.coroutines 1.9 (VerifyError in JobKt.invokeOnCompletion).
-optimizations !method/specialization/**
