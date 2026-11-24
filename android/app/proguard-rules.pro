# R8 rules for the release APK (v0.58.0). The libraries ship their own consumer rules (WorkManager, Health Connect, Tink
# behind androidx.security, Compose); these are the project's own, and the choices that keep a shrunk app readable.

# Shrink only: no renaming (a stack trace in Settings, Share diagnostics or an issue stays readable, the source is open
# anyway). AGP 9 requires the optimize default file; -dontoptimize is repeated here so the output stays as readable as before.
-dontobfuscate
-dontoptimize

# Line numbers and source files in stack traces.
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,EnclosingMethod

# kotlinx.serialization: the generated serializers and the companion lookups are reached by name, as the library's own
# documentation says to keep them. The JSON the app reads (Garmin, Oura, WHOOP, GitHub releases, AI answers, backups) all
# goes through these classes.
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class nl.tufein.openconnect.**$$serializer { *; }
-keepclassmembers class nl.tufein.openconnect.** { *** Companion; }
-keepclasseswithmembers class nl.tufein.openconnect.** { kotlinx.serialization.KSerializer serializer(...); }

# Warnings from annotation-only classes that Tink and other libraries mention but do not need at run time.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
