# Add project specific ProGuard rules here.

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

-keepclassmembers class * {
    @javax.inject.* <fields>;
    @javax.inject.* <init>(...);
}

-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

-keep public class * implements coil.map.Mapper
-keep public class * implements coil.fetch.Fetcher

-keep class androidx.camera.** { *; }

-keep class com.google.android.exoplayer2.** { *; }

# Strips logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
