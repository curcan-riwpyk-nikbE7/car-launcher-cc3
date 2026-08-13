-dontwarn kotlinx.**
-keep class com.example.carlauncher.** { *; }
# NotificationListenerService должен быть виден системе по имени
-keep class com.example.carlauncher.data.MediaNotificationListener { *; }
-keepclassmembers class * extends android.service.notification.NotificationListenerService { *; }

# --- Vosk и JNA ---
# JNA связывает Java с нативным кодом через рефлексию: имена классов,
# полей и методов ищутся в рантайме по строкам. R8 их не видит и вырезает,
# из-за чего release-сборка падает при старте с NoClassDefFoundError,
# хотя debug работает. Поэтому оба пакета сохраняем целиком.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
# JNA собрана с оглядкой на десктопную Java, где есть AWT.
# На Android этих классов нет — просто глушим предупреждения.
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn com.sun.jna.**
