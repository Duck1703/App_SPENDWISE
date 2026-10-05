# Spendwise M0 foundation — project-specific R8/ProGuard rules.
# Retrofit, OkHttp, and kotlinx.serialization ship their own consumer rules;
# add app rules here when DTO/models land in later milestones (M1+).

# ---------------------------------------------------------------------------
# M7 hardening — kotlinx.serialization under R8 full mode.
#
# Retrofit's kotlinx converter resolves `serializer(Type)` reflectively for
# every service return/parameter type. That runtime lookup finds the
# compiler-generated `<Dto>$$serializer` class (and the `Companion`
# serializer() member) by NAME — obfuscated names break deserialization in
# release builds even though `assembleRelease` compiles cleanly.
#
# The canonical keep set, scoped to the app's wire surface only:
#   - @Serializable classes keep their Companion member + serializer()
#   - generated $$serializer classes keep their names + members
# DTO packages: data/remote/**, core/network (ProblemDetailDto),
# navigation (typed-route @Serializable objects).
# ---------------------------------------------------------------------------

-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class com.spendwise.android.** {
    *** Companion;
}
-keepclasseswithmembers @kotlinx.serialization.Serializable class com.spendwise.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.spendwise.android.**$$serializer { *; }

# kotlinx-serialization's own Json* companions are resolved the same way.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
