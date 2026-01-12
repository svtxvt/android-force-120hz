# Add project specific ProGuard rules here.
# Keep the service and receiver
-keep class com.forcehz.app.ForceHzAccessibilityService { *; }
-keep class com.forcehz.app.BootReceiver { *; }
-keep class com.forcehz.app.MainActivity { *; }

# Keep Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
