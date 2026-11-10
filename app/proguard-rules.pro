# --- R.SHMap ---
# Shizuku user service is instantiated by class name inside the Shizuku server process.
-keep class app.rshmap.shizuku.** { *; }
# kotlinx.serialization models (reflection-free, but keep generated serializers)
-keep,includedescriptorclasses class app.rshmap.core.**$$serializer { *; }
-keepclassmembers class app.rshmap.core.** {
    *** Companion;
}
-keepclasseswithmembers class app.rshmap.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class rikka.shizuku.** { *; }
# strip logging in release
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}
-dontwarn kotlinx.serialization.**
