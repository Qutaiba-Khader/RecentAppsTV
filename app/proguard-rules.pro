# Shizuku: Shizuku.newProcess is called by reflection; the provider is named in the manifest.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
# Hidden-API bypass ships its own rules; keep it whole anyway (it uses reflection internally).
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**
# Framework stubs referenced only by reflection.
-dontwarn android.app.IActivityTaskManager**
-dontwarn android.app.IActivityManager**
