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

# -----------------------------------------------------------------------------
# Tink / EncryptedSharedPreferences
# androidx.security pulls in Tink, which references Error Prone annotations that
# are compile-time only and absent at runtime. They are never loaded, so the
# references are safe to ignore -- without this R8 fails the build outright.
# -----------------------------------------------------------------------------
-dontwarn com.google.errorprone.annotations.**

# Tink also carries optional GCP/AWS KMS integrations this app never uses. Do not
# add a blanket -keep for com.google.crypto.tink.**: that retains those classes and
# drags in Google API client and Joda-Time, which are not on the classpath.
# androidx.security ships its own consumer rules for what Tink genuinely needs.
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
