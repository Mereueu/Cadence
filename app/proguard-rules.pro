# Media3 and Compose ship their own consumer rules.
# Keep the playback service, which the system starts by name.
-keep class com.cadence.player.playback.PlaybackService { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn sun.misc.**
-dontwarn java.lang.reflect.AnnotatedType
-ignorewarnings
