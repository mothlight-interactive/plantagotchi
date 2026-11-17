package dev.mothlight.plantagotchi

import dev.mothlight.plantagotchi.audio.AudioAssets
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every asset the game plays must exist; run tools/generate_audio.py after adding one. */
class AudioFilesTest {
    @Test
    fun allAudioFilesExist() {
        val root = File("src/commonMain/composeResources/files")
        val missing = AudioAssets.all.filterNot { File(root, "$it.wav").isFile }
        assertTrue(missing.isEmpty(), "missing audio files: $missing")
    }
}
