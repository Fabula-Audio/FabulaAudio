-keep class com.arthenica.** { *; }
-dontwarn com.arthenica.**
-keep class com.fabula.audio.SbsmsTempo { *; }
-keep interface com.fabula.audio.SbsmsTempo$ProgressListener { *; }
-keepclassmembers class * implements com.fabula.audio.SbsmsTempo$ProgressListener {
    public void onProgress(long, long);
}
