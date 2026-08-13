package com.example.carlauncher.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты разбора голосовых команд.
 *
 * Смысл: Vosk на маленькой модели отдаёт грязный текст, и легко получить
 * ситуацию, когда «выключи музыку» включает музыку. В машине это
 * раздражает и отвлекает, поэтому спорные пары проверяем автоматически.
 */
class VoiceCommandsTest {

    private fun act(s: String) = VoiceCommands.parse(s)?.action

    @Test
    fun `экран выключается и включается`() {
        assertEquals(VoiceAction.ScreenOff, act("выключи экран"))
        assertEquals(VoiceAction.ScreenOff, act("погаси экран"))
        assertEquals(VoiceAction.ScreenOff, act("Выключи, экран!"))
        assertEquals(VoiceAction.ScreenOn, act("включи экран"))
    }

    @Test
    fun `выключение не путается с включением`() {
        // Самая опасная пара: «включи» содержится внутри «выключи»
        assertEquals(VoiceAction.Pause, act("выключи музыку"))
        assertEquals(VoiceAction.Play, act("включи музыку"))
        assertEquals(VoiceAction.Mute, act("выключи звук"))
        assertEquals(VoiceAction.Unmute, act("включи звук"))
    }

    @Test
    fun `громкость шагами`() {
        assertEquals(VoiceAction.VolumeUp(1), act("сделай громче"))
        assertEquals(VoiceAction.VolumeUp(3), act("сделай намного громче"))
        assertEquals(VoiceAction.VolumeDown(1), act("потише"))
        assertEquals(VoiceAction.VolumeDown(3), act("сильно тише"))
    }

    @Test
    fun `громкость числом`() {
        assertEquals(VoiceAction.VolumeSet(50), act("громкость пятьдесят"))
        assertEquals(VoiceAction.VolumeSet(30), act("громкость 30"))
        assertEquals(VoiceAction.VolumeSet(100), act("громкость на максимум"))
    }

    @Test
    fun `треки`() {
        assertEquals(VoiceAction.NextTrack, act("следующий трек"))
        assertEquals(VoiceAction.NextTrack, act("переключи дальше"))
        assertEquals(VoiceAction.PrevTrack, act("предыдущая песня"))
    }

    @Test
    fun `приложения`() {
        assertEquals(VoiceAction.OpenApp(AppKind.Maps), act("открой карты"))
        assertEquals(VoiceAction.OpenApp(AppKind.Maps), act("включи навигацию"))
        assertEquals(VoiceAction.OpenApp(AppKind.Video), act("открой ютуб"))
        assertEquals(VoiceAction.OpenApp(AppKind.Radio), act("включи радио"))
    }

    @Test
    fun `открой музыку запускает приложение а включи музыку играет`() {
        assertEquals(VoiceAction.OpenApp(AppKind.Music), act("открой музыку"))
        assertEquals(VoiceAction.Play, act("включи музыку"))
    }

    @Test
    fun `ночной режим не ломает экран`() {
        assertEquals(VoiceAction.NightOn, act("включи ночной режим"))
        assertEquals(VoiceAction.NightOff, act("выключи ночной режим"))
        assertEquals(VoiceAction.ScreenOff, act("выключи экран"))
    }

    @Test
    fun `главный экран это не подсветка`() {
        assertEquals(VoiceAction.GoHome, act("на главный экран"))
        assertEquals(VoiceAction.GoHome, act("домой"))
    }

    @Test
    fun `мусор не распознаётся`() {
        assertNull(act(""))
        assertNull(act("   "))
        assertNull(act("бла бла бла"))
        assertNull(act("[unk]"))
    }

    @Test
    fun `ё и регистр не мешают`() {
        assertEquals(VoiceAction.Cancel, act("ОТМЕНА"))
        assertTrue(VoiceCommands.parse("Всё, стоп") != null)
    }

    @Test
    fun `грамматика не пустая и без дублей`() {
        assertTrue(VoiceCommands.grammar.size > 50)
        assertEquals(
            VoiceCommands.grammar.size,
            VoiceCommands.grammar.distinct().size
        )
    }

    @Test
    fun `все фразы из грамматики распознаются`() {
        // Если фраза попала в словарь Vosk, но парсер её не понимает —
        // пользователь скажет ровно её и получит «не расслышал».
        val broken = VoiceCommands.grammar.filter { VoiceCommands.parse(it) == null }
        assertTrue("Не разобраны: $broken", broken.isEmpty())
    }
}
