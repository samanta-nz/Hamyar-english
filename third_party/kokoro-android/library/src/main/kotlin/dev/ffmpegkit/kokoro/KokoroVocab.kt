package dev.ffmpegkit.kokoro

/**
 * Kokoro's fixed phoneme vocabulary: maps IPA symbols (produced by espeak-ng) to the
 * integer token ids the ONNX model expects. This is the canonical Kokoro-82M vocab
 * (pad + punctuation + letters + IPA). Index 0 (`$`) is the pad/boundary token.
 */
internal object KokoroVocab {

    private const val PAD = "$"
    private const val PUNCTUATION = ";:,.!?¡¿—…\"«»“” "
    private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val LETTERS_IPA =
        "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ"

    /** Symbol → id. Missing symbols are dropped during encoding. */
    private val map: Map<Char, Int> = buildMap {
        val symbols = PAD + PUNCTUATION + LETTERS + LETTERS_IPA
        symbols.forEachIndexed { i, c -> putIfAbsent(c, i) }
    }

    /** The pad / clause-boundary token id (0). */
    const val PAD_ID = 0

    /**
     * Encode an IPA phoneme string into model token ids, wrapped with a pad token at
     * each end (Kokoro convention): `[0, …phonemes…, 0]`.
     */
    fun encode(phonemes: String): LongArray {
        val ids = ArrayList<Long>(phonemes.length + 2)
        ids.add(PAD_ID.toLong())
        for (c in phonemes) map[c]?.let { ids.add(it.toLong()) }
        ids.add(PAD_ID.toLong())
        return ids.toLongArray()
    }
}
