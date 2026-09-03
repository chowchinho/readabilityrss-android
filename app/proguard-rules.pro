# ProGuard & R8 configuration rules for Readability Reader

# -----------------------------------------------------------------------------
# Room Database
# Preserve Room entity models and generated DAO implementations
# -----------------------------------------------------------------------------
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract <methods>;
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# Gson & Remote DTOs (FEVER and Jotty)
# Field names are the wire contract -- obfuscating them breaks JSON parsing
# -----------------------------------------------------------------------------
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class co.chinho.readabilityreader.data.remote.dto.** { *; }
-keep class co.chinho.readabilityreader.data.remote.jotty.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# -----------------------------------------------------------------------------
# Retrofit 2
# Preserve service interfaces and HTTP annotations
# -----------------------------------------------------------------------------
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions, InnerClasses, EnclosingMethod
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

# -----------------------------------------------------------------------------
# Dagger / Hilt
# Preserve dependency injection entrypoints and workers
# -----------------------------------------------------------------------------
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-keepclasseswithmembers class * {
    @androidx.hilt.work.HiltWorker <init>(...);
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# -----------------------------------------------------------------------------
# Coil & OkHttp
# Consumer rules supplement
# -----------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
