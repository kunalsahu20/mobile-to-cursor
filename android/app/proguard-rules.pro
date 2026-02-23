# Proguard rules for Mobile to Cursor
-keepattributes *Annotation*
-dontwarn javax.annotation.**

# Keep Compose
-keep class androidx.compose.** { *; }
