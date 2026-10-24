# Regras do app FuelCompare.
# O build de release está com minify desativado nesta versão inicial.
# Se ativar R8, preserve os modelos serializados e as anotações do Room/Retrofit.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.fuelcompare.**$$serializer { *; }
-keepclassmembers class com.fuelcompare.** { *** Companion; }
-keepclasseswithmembers class com.fuelcompare.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
