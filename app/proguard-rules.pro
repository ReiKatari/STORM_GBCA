-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers enum * { *; }

# Keep JNI bridge and native DTOs intact
-keep class ru.stormgames.gbca.NativeCore { *; }
-keep class ru.stormgames.gbca.RomInfo { *; }
-keep class ru.stormgames.gbca.** { *; }

# Keep app models, preferences, RetroAchievements, and Dual-Mirror Updater DTOs
-keep class com.stormgbca.emulator.model.** { *; }
-keep class com.stormgbca.emulator.data.** { *; }
-keep class com.stormgbca.emulator.update.** { *; }

# Keep Parcelables & standard JSON/Coil integrations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keep class org.json.** { *; }
-keep class coil.** { *; }
-keep class * implements coil.fetch.Fetcher { *; }
-keep class * implements coil.fetch.Fetcher$Factory { *; }
