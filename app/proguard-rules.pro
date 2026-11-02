# Knock Me release R8/ProGuard rules.

# ---- kotlinx.serialization ----
# https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro (summarized)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.joyel.tripguard.**$$serializer { *; }
-keepclassmembers class com.joyel.tripguard.** {
    *** Companion;
}
-keepclasseswithmembers class com.joyel.tripguard.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.joyel.tripguard.** { *; }

# ---- Room ----
# Generated _Impl classes and entities/DAOs are referenced via reflection/KSP-generated code.
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ---- OkHttp / Okio ----
# https://github.com/square/okhttp/blob/master/okhttp/src/jvmMain/resources/META-INF/proguard/okhttp3.pro
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---- osmdroid ----
# Uses reflection for tile source config and some XML (de)serialization.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
-dontwarn org.apache.http.**

# ---- Play services location ----
-dontwarn com.google.android.gms.**

# ---- App data classes passed through DataStore/Room/kotlinx.serialization ----
-keep class com.joyel.tripguard.trip.** { *; }
-keep class com.joyel.tripguard.history.** { *; }
-keep class com.joyel.tripguard.geo.LatLon { *; }
-keep class com.joyel.tripguard.search.PlaceResult { *; }
-keep class com.joyel.tripguard.share.ParsedDestination { *; }
