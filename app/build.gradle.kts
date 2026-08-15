plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.carlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.carlauncher"
        minSdk = 23          // Android 6.0 — типовые китайские ГУ
        targetSdk = 30       // намеренно 30: на targetSdk 31+ старые ГУ ломают часть intent'ов
        versionCode = 13
        versionName = "2.2"

        ndk {
            // Головные устройства все на ARM. Библиотеки Vosk для x86
            // весят 20 МБ и на магнитоле бесполезны.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            // Тестовый ключ, лежит в репозитории. Для своей сборки
            // сгенерируйте свой: keytool -genkeypair -keystore my.jks ...
            storeFile = file("../carlauncher.jks")
            storePassword = "carlauncher"
            keyAlias = "carlauncher"
            keyPassword = "carlauncher"
        }
        // Публичный ключ platform из AOSP. Большинство китайских ГУ
        // (FYT, XYAUTO, TOPWAY, Unisoc/Spreadtrum) собраны с этим самым
        // ключом — производители не меняют дефолтный. Если совпало,
        // система считает наш APK «своим» и пускает в android.uid.system.
        create("platform") {
            storeFile = file("../platform.jks")
            storePassword = "android"
            keyAlias = "platform"
            keyPassword = "android"
        }
    }

    // Две сборки одного кода. Отличаются только подписью и манифестом:
    // system получает sharedUserId и права уровня signature|privileged,
    // без которых чужую активность на свой дисплей не пустить.
    flavorDimensions += "privilege"
    productFlavors {
        create("standard") {
            dimension = "privilege"
            signingConfig = signingConfigs.getByName("release")
        }
        create("system") {
            dimension = "privilege"
            signingConfig = signingConfigs.getByName("platform")
        }
        // Прошивка ГУ собрана с тегом test-keys (видно в AIDA64:
        // «alps/full_k62v1_64_bsp/...:user/test-keys»). Это НЕ ключ platform:
        // в AOSP их четыре разных, и подпись должна совпасть ровно.
        // Раньше мы подписывали platform — установка системной версии
        // не проходила именно поэтому.
        //
        // Здесь sharedUserId не запрашивается: если система не пустит
        // в android.uid.system, установка сорвётся целиком. Права
        // signature-уровня приходят и от одной совпавшей подписи.
        create("aosp") {
            dimension = "privilege"
            signingConfig = signingConfigs.getByName("release")
        }
        // То же самое, но с sharedUserId. Без него VirtualDisplay
        // не принимает чужую активность даже при выданном
        // ACTIVITY_EMBEDDING — на этом спотыкались все, кто делал PIP.
        // Отдельным вариантом, потому что несовпадение подписи
        // с sharedUserId срывает установку целиком.
        create("aospuid") {
            dimension = "privilege"
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildTypes {
        release {
            // Подпись задаётся во flavor: standard — своим ключом,
            // system — ключом платформы AOSP.
            // Минификация выключена намеренно. R8 вырезает классы Vosk/JNA,
            // которые ищутся рефлексией в рантайме, — release падал при
            // старте, хотя debug работал. Правила keep помогают не всегда,
            // а выигрыш в размере на фоне модели распознавания незаметен.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        // Приложение ставится сайдлоадом на магнитолу, а не через Google Play,
        // поэтому требование Play "targetSdk >= 33" здесь неприменимо.
        disable += "ExpiredTargetSdkVersion"
        abortOnError = true
        // lint vital при сборке release не влезает в память сборочной
        // машины вместе с R8. Проверяем линтом отдельной командой.
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Модель Vosk уже плотно упакована. Повторное сжатие 88 МБ
        // при каждой сборке съедает память и минуты времени, а выигрыш
        // почти нулевой. Заодно ускоряется распаковка на самом ГУ.
        jniLibs.useLegacyPackaging = false
    }

    // Модель Vosk сжимаем: без сжатия APK раздувается со 46 до 134 МБ.
    // Распаковка при первом запуске занимает пару секунд — приемлемая
    // плата за втрое меньший файл, который ещё возить на флешке.
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Офлайн-распознавание речи. Модель лежит в assets/model-ru,
    // интернет не нужен вообще — важно, в машине связи часто нет.
    implementation("com.alphacephei:vosk-android:0.3.75")

    testImplementation("junit:junit:4.13.2")
}
