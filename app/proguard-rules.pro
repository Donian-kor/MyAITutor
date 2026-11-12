# LiteRT-LM runtime uses native/JNI bindings. Keep native-facing classes intact.
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
