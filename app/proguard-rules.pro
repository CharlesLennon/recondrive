# MapLibre Proguard Rules
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# Android Auto Car App Library
-keep class androidx.car.app.** { *; }
-dontwarn androidx.car.app.**

# Room DB
-keep class * extends androidx.room.RoomDatabase
