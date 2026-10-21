package dev.ffmpegkit.kokoro

enum class Gender { FEMALE, MALE }

/** Kokoro voice quality grade (A best → D). */
enum class Grade { A, B, C, D }

/**
 * A Kokoro voice. Each voice has a style embedding ("voicepack", a `<id>.bin` file)
 * and an espeak language used for phonemization.
 *
 * @property id voicepack id, e.g. "af_heart", "am_adam", "bf_emma".
 * @property name human name, e.g. "Heart (American Female)".
 * @property language BCP-47-ish tag, e.g. "en-US".
 * @property espeakLang the espeak voice for G2P, e.g. "en-us".
 * @property gender voice gender.
 * @property grade quality grade.
 */
data class KokoroVoice(
    val id: String,
    val name: String,
    val language: String,
    val espeakLang: String,
    val gender: Gender,
    val grade: Grade,
) {
    companion object {
        /** The single voice bundled in the Free tier (American English female). */
        val AF_HEART = KokoroVoice(
            "af_heart", "Heart (American Female)", "en-US", "en-us", Gender.FEMALE, Grade.A,
        )

        /** Voices bundled in this build. Pro ships all 54; Free ships [AF_HEART]. */
        val BUNDLED: List<KokoroVoice> = listOf(AF_HEART)
    }
}
