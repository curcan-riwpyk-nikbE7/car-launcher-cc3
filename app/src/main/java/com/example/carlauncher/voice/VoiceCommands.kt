package com.example.carlauncher.voice

/**
 * Разбор распознанной фразы в команду.
 *
 * Почему не просто `text.contains("громче")`: Vosk с маленькой моделью
 * регулярно путает похожие слова и теряет окончания. Поэтому:
 *  - работаем по корням слов, а не по точным формам;
 *  - отрицание («выключи», «убери») проверяем ДО положительной формы,
 *    иначе «выключи музыку» поймается правилом «включи музыку»;
 *  - специфичные правила идут раньше общих.
 *
 * Класс намеренно без зависимостей от Android — гоняется JVM-тестами.
 */

sealed class VoiceAction {
    object ScreenOff : VoiceAction()
    object ScreenOn : VoiceAction()

    object Play : VoiceAction()
    object Pause : VoiceAction()
    object NextTrack : VoiceAction()
    object PrevTrack : VoiceAction()

    data class VolumeUp(val steps: Int = 1) : VoiceAction()
    data class VolumeDown(val steps: Int = 1) : VoiceAction()
    data class VolumeSet(val percent: Int) : VoiceAction()
    object Mute : VoiceAction()
    object Unmute : VoiceAction()

    data class OpenApp(val kind: AppKind) : VoiceAction()

    object GoHome : VoiceAction()

    object NightOn : VoiceAction()
    object NightOff : VoiceAction()

    object SaySpeed : VoiceAction()

    object Cancel : VoiceAction()
}

enum class AppKind { Maps, Music, Radio, Video, Phone, Settings, Bluetooth }

data class VoiceResult(val action: VoiceAction, val reply: String)

object VoiceCommands {

    /**
     * Список фраз для грамматики Vosk. Ограниченный словарь поднимает
     * точность в разы: движок выбирает только из этих слов, а не из
     * двухсот тысяч слов русского языка. Критично в шумной машине.
     */
    val grammar: List<String> = listOf(
        "выключи экран", "погаси экран", "выключить экран", "экран выключи",
        "включи экран", "зажги экран", "включить экран",

        "включи музыку", "играй музыку", "музыка", "продолжи", "плей",
        "выключи музыку", "останови музыку", "стоп", "пауза", "поставь на паузу",
        "следующий трек", "следующая песня", "дальше", "переключи",
        "предыдущий трек", "предыдущая песня", "назад трек", "верни трек",

        "громче", "сделай громче", "прибавь громкость", "увеличь громкость",
        "тише", "сделай тише", "убавь громкость", "уменьши громкость",
        "громкость на максимум", "максимальная громкость",
        "громкость на минимум", "минимальная громкость",
        "громкость двадцать", "громкость тридцать", "громкость сорок",
        "громкость пятьдесят", "громкость шестьдесят", "громкость семьдесят",
        "громкость восемьдесят", "громкость девяносто", "громкость сто",
        "выключи звук", "отключи звук", "тишина",
        "включи звук", "верни звук",

        "открой карты", "включи навигацию", "запусти навигатор", "карты", "навигация",
        "открой музыку", "запусти яндекс музыку", "яндекс музыка",
        "включи радио", "открой радио", "радио",
        "открой ютуб", "запусти ютуб", "ютуб", "включи видео",
        "открой телефон", "позвонить", "телефон",
        "открой настройки", "настройки",
        "открой блютуз", "блютуз",

        "домой", "на главный экран", "главный экран",
        "ночной режим", "включи ночной режим", "выключи ночной режим", "дневной режим",
        "какая скорость", "скорость", "сколько едем",
        "отмена", "стой", "ничего", "забудь"
    )

    /** Возвращает null, если ничего не поняли — тогда UI скажет «не расслышал». */
    fun parse(raw: String): VoiceResult? {
        val t = normalize(raw)
        if (t.isBlank()) return null

        // «стой» — отмена помощника, а не пауза. Сравниваем точно,
        // чтобы не задеть «останови».
        if (t == "стой" || has(t, "отмена", "забудь", "ничего не надо", "ничего")) {
            return VoiceResult(VoiceAction.Cancel, "Отменил")
        }

        // Ночной режим — до общего «выключи», иначе съест правило экрана
        if (has(t, "ночной режим", "ночной")) {
            return if (isNegative(t))
                VoiceResult(VoiceAction.NightOff, "Дневной режим")
            else VoiceResult(VoiceAction.NightOn, "Ночной режим")
        }
        if (has(t, "дневной режим", "дневной")) {
            return VoiceResult(VoiceAction.NightOff, "Дневной режим")
        }

        if (has(t, "экран", "экрана", "подсветк")) {
            // «главный экран» — это не про подсветку
            if (has(t, "главн", "домой")) {
                return VoiceResult(VoiceAction.GoHome, "Главный экран")
            }
            return if (isNegative(t) || has(t, "погас", "потуш"))
                VoiceResult(VoiceAction.ScreenOff, "Выключаю экран")
            else VoiceResult(VoiceAction.ScreenOn, "Включаю экран")
        }

        if (has(t, "скорост", "сколько едем", "как быстро")) {
            return VoiceResult(VoiceAction.SaySpeed, "")
        }

        if (has(t, "звук", "тишина", "заглуши")) {
            return if (isNegative(t) || has(t, "тишина", "заглуши"))
                VoiceResult(VoiceAction.Mute, "Звук выключен")
            else VoiceResult(VoiceAction.Unmute, "Звук включён")
        }

        numberIn(t)?.let { n ->
            if (has(t, "громкост", "громк")) {
                val p = n.coerceIn(0, 100)
                return VoiceResult(VoiceAction.VolumeSet(p), "Громкость $p")
            }
        }
        if (has(t, "громкость на максимум", "максимальн", "на всю")) {
            return VoiceResult(VoiceAction.VolumeSet(100), "Максимальная громкость")
        }
        if (has(t, "громкость на минимум", "минимальн")) {
            return VoiceResult(VoiceAction.VolumeSet(10), "Минимальная громкость")
        }

        if (has(t, "громче", "прибав", "увелич", "погромче")) {
            val steps = if (has(t, "намного", "сильно", "побольше")) 3 else 1
            return VoiceResult(VoiceAction.VolumeUp(steps), "Громче")
        }
        if (has(t, "тише", "убав", "уменьш", "потише")) {
            val steps = if (has(t, "намного", "сильно", "поменьше")) 3 else 1
            return VoiceResult(VoiceAction.VolumeDown(steps), "Тише")
        }

        if (has(t, "следующ", "дальше", "переключ", "друг")) {
            return VoiceResult(VoiceAction.NextTrack, "Следующий трек")
        }
        if (has(t, "предыдущ", "прошл", "верни трек", "назад трек", "обратно")) {
            return VoiceResult(VoiceAction.PrevTrack, "Предыдущий трек")
        }

        appKind(t)?.let { kind ->
            // «включи музыку» — это play, а «открой музыку» — запуск приложения
            val wantsLaunch = has(t, "открой", "открыть", "запусти", "запустить", "покажи")
            if (kind == AppKind.Music && !wantsLaunch) {
                // проваливаемся ниже, в play/pause
            } else {
                return VoiceResult(VoiceAction.OpenApp(kind), "Открываю ${appName(kind)}")
            }
        }

        if (has(t, "пауза", "паузу", "останов", "стоп", "хватит")) {
            return VoiceResult(VoiceAction.Pause, "Пауза")
        }
        if (has(t, "музык", "играй", "плей", "продолж", "воспроизв")) {
            return if (isNegative(t))
                VoiceResult(VoiceAction.Pause, "Пауза")
            else VoiceResult(VoiceAction.Play, "Играю")
        }

        if (has(t, "домой", "главн", "лаунчер")) {
            return VoiceResult(VoiceAction.GoHome, "Главный экран")
        }

        return null
    }

    private fun appKind(t: String): AppKind? = when {
        has(t, "карт", "навигац", "навигатор", "маршрут", "куда ехать") -> AppKind.Maps
        has(t, "ютуб", "youtube", "видео", "ролик") -> AppKind.Video
        has(t, "радио", "фм", "волн") -> AppKind.Radio
        has(t, "телефон", "позвон", "звонок", "набер") -> AppKind.Phone
        has(t, "настройк", "параметр") -> AppKind.Settings
        has(t, "блютуз", "bluetooth", "блютус") -> AppKind.Bluetooth
        has(t, "музык", "яндекс музык", "плеер", "трек") -> AppKind.Music
        else -> null
    }

    private fun appName(kind: AppKind): String = when (kind) {
        AppKind.Maps -> "карты"
        AppKind.Music -> "музыку"
        AppKind.Radio -> "радио"
        AppKind.Video -> "видео"
        AppKind.Phone -> "телефон"
        AppKind.Settings -> "настройки"
        AppKind.Bluetooth -> "блютуз"
    }

    private fun isNegative(t: String): Boolean =
        has(t, "выключ", "отключ", "убер", "погас", "потуш", "останов", "прекрат", "не надо")

    private fun has(t: String, vararg roots: String): Boolean =
        roots.any { t.contains(it) }

    /** Числа словами и цифрами. Нужно для «громкость пятьдесят». */
    private fun numberIn(t: String): Int? {
        Regex("\\d+").find(t)?.let { return it.value.toIntOrNull() }
        val words = mapOf(
            "ноль" to 0, "десять" to 10, "двадцать" to 20, "тридцать" to 30,
            "сорок" to 40, "пятьдесят" to 50, "шестьдесят" to 60,
            "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90,
            "сто" to 100, "половин" to 50, "максимум" to 100, "минимум" to 10
        )
        for ((w, v) in words) if (t.contains(w)) return v
        return null
    }

    private fun normalize(raw: String): String = raw
        .lowercase()
        .replace('ё', 'е')
        .replace("[unk]", " ")
        .replace(Regex("[^а-яa-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
