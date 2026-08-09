# Kotlin
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Retrofit
-keep class retrofit2.** { *; }
-keepclasseswithmembers class retrofit2.** {
    <methods>;
}
-keepattributes Signature
-keepattributes Exceptions

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.* class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class ** extends dagger.hilt.** { *; }
-keep interface ** extends dagger.hilt.** { *; }
-keepclassmembers class * {
    @dagger.hilt.* <methods>;
}

# Androidx
-keep class androidx.** { *; }
-keepclasseswithmembers class androidx.** {
    <methods>;
}

# App classes
-keep class com.wovenledger.** { *; }
