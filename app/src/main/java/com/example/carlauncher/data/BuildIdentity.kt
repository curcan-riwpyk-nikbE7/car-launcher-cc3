package com.example.carlauncher.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Кто мы такие — определяется по факту, а не по зашитой строке.
 *
 * Зачем понадобилось. В релизе лежат несколько сборок с РАЗНЫМИ
 * подписями: обычная своим ключом, TESTKEY и PIP ключом AOSP.
 * Android ставит обновление поверх только если подпись совпадает
 * байт в байт, иначе выдаёт «Приложение не установлено» без всяких
 * пояснений.
 *
 * Раньше нужный файл выбирался по имени: сначала по слову «system»,
 * потом через BuildConfig. Оба способа хрупкие — стоит переименовать
 * файл в релизе, и обновление молча ломается. Именно так и вышло.
 *
 * Здесь наоборот: приложение смотрит на СЕБЯ и ищет в релизе файл
 * с такими же приметами. Переименование больше ничего не ломает.
 *
 * Важно, что одной подписи мало. У TESTKEY и PIP она одинаковая —
 * обе подписаны ключом AOSP. Различаются они системным пользователем:
 * у PIP в манифесте sharedUserId="android.uid.system", у TESTKEY нет.
 * Поэтому примета составная: подпись + системный uid.
 */
object BuildIdentity {

    private const val TAG = "BuildIdentity"

    /**
     * Приметы сборки.
     *
     * @param certSha256 отпечаток сертификата, полный
     * @param systemUid работает ли под системным пользователем
     */
    data class Identity(
        val certSha256: String,
        val systemUid: Boolean
    ) {
        /** Короткий вид для показа на экране диагностики. */
        val short: String get() = certSha256.take(8)

        /**
         * Человеческое имя. Только для подписи в интерфейсе —
         * решения по нему не принимаются, чтобы не гадать
         * по названиям.
         */
        val title: String
            get() = when {
                certSha256.startsWith(AOSP_TESTKEY) && systemUid -> "PIP (testkey + системный uid)"
                certSha256.startsWith(AOSP_TESTKEY) -> "TESTKEY (ключ AOSP)"
                certSha256.isEmpty() -> "неизвестно"
                else -> "обычная (свой ключ)"
            }
    }

    /** Отпечаток стандартного ключа AOSP testkey — им подписана прошивка ГУ. */
    const val AOSP_TESTKEY = "a40da80a"

    private var cached: Identity? = null

    /** Приметы установленного лаунчера. */
    fun current(context: Context): Identity {
        cached?.let { return it }

        val cert = ownCertSha256(context)
        // Системный пользователь — это uid 1000. Тот же признак
        // проверяет система, решая, пускать ли к привилегированным API.
        val sysUid = Process.myUid() == 1000

        return Identity(cert, sysUid).also { cached = it }
    }

    /** SHA-256 сертификата, которым подписан установленный APK. */
    private fun ownCertSha256(context: Context): String = runCatching {
        val pm = context.packageManager
        val bytes = if (Build.VERSION.SDK_INT >= 28) {
            val info = pm.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val sig = info.signingInfo ?: return@runCatching ""
            // apkContentsSigners отдаёт текущий ключ. signingCertificateHistory
            // нужен только при ротации ключа, у нас её нет.
            sig.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        } ?: return@runCatching ""

        sha256(bytes)
    }.getOrElse {
        Log.w(TAG, "Не удалось прочитать свою подпись", it)
        ""
    }

    /**
     * Приметы скачанного файла — до того, как отдать его установщику.
     *
     * Читаем APK как обычный zip: сертификат лежит в META-INF, а флаг
     * системного пользователя — в манифесте. Полноценный разбор
     * двоичного манифеста здесь не нужен, достаточно найти строку
     * android.uid.system: в таблице строк она хранится открытым
     * текстом, и её присутствие однозначно говорит о sharedUserId.
     *
     * @return null если файл не читается или это вообще не APK
     */
    fun ofFile(context: Context, apk: File): Identity? {
        // Подпись достаём средствами системы: она умеет проверять
        // все схемы, включая v2 и v3, где сертификата в META-INF нет.
        val cert = runCatching {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            }
            val info = pm.getPackageArchiveInfo(apk.absolutePath, flags)
                ?: return@runCatching ""

            val bytes = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return@runCatching ""

            sha256(bytes)
        }.getOrElse {
            Log.w(TAG, "Подпись файла не прочиталась", it)
            ""
        }

        if (cert.isEmpty()) return null

        val sysUid = hasSharedUserId(apk)
        return Identity(cert, sysUid)
    }

    /**
     * Есть ли в APK sharedUserId="android.uid.system".
     *
     * Ищем подстроку в двоичном манифесте. Это не строгий разбор,
     * но строка android.uid.system попадает в манифест только одним
     * способом — через атрибут sharedUserId, так что ложных
     * срабатываний не будет.
     */
    private fun hasSharedUserId(apk: File): Boolean = runCatching {
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml") ?: return false
            val bytes = zip.getInputStream(entry).use { it.readBytes() }

            // В двоичном манифесте строки лежат в UTF-16
            val needle = "android.uid.system".toByteArray(Charsets.UTF_16LE)
            indexOf(bytes, needle) >= 0
        }
    }.getOrDefault(false)

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * Подходит ли скачанный файл для установки поверх нас.
     *
     * Подпись должна совпасть точно — это требование Android.
     * Системный пользователь тоже: переход с обычной сборки на
     * версию с sharedUserId и обратно система считает несовместимым
     * изменением и отклоняет.
     */
    fun isCompatible(mine: Identity, theirs: Identity): Boolean =
        mine.certSha256.isNotEmpty() &&
            mine.certSha256 == theirs.certSha256 &&
            mine.systemUid == theirs.systemUid

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
