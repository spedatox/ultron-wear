# ═══════════════════════════════════════════════════════════════════════════
#  R8 rules. The build runs R8 in FULL mode (see gradle.properties), which is
#  more aggressive than the compat mode AGP defaults to — so anything reached
#  only by reflection has to be named here or it will be stripped.
# ═══════════════════════════════════════════════════════════════════════════

# ── kotlinx.serialization ───────────────────────────────────────────────────
# Serializers are generated as nested `$$serializer` classes and looked up
# reflectively at runtime. Losing these turns every schedule/ledger read into a
# SerializationException — and because both have try/catch fallbacks, the app
# would not crash, it would silently show an empty schedule. That failure mode
# is exactly why these rules matter more than they look like they do.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.spedatox.ultroncore.** {
    *** Companion;
}
-keepclasseswithmembers class com.spedatox.ultroncore.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.spedatox.ultroncore.**
-keep, allowobfuscation, allowoptimization class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class com.spedatox.ultroncore.**
-keep, allowobfuscation, allowoptimization class <1>$$serializer {
    *;
}

# ── WorkManager ─────────────────────────────────────────────────────────────
# Workers are instantiated by class name from the WorkManager database, so the
# default factory needs their two-arg constructors intact.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Firebase Messaging ──────────────────────────────────────────────────────
# The service is resolved from the manifest intent-filter, never from code.
-keep class com.spedatox.ultroncore.notification.AttendanceMessagingService { *; }
-dontwarn com.google.firebase.**

# ── Enums crossing an Intent boundary ───────────────────────────────────────
# AttendanceStatus round-trips through Intent extras via valueOf(), which is
# reflection R8 cannot see.
-keepclassmembers enum com.spedatox.ultroncore.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Wear surfaces, also manifest-resolved ───────────────────────────────────
-keep class com.spedatox.ultroncore.tile.MainTileService { *; }
-keep class com.spedatox.ultroncore.complication.MainComplicationService { *; }
-keep class com.spedatox.ultroncore.notification.AttendanceActionReceiver { *; }

# ── Noise ───────────────────────────────────────────────────────────────────
-dontwarn org.jetbrains.annotations.**
