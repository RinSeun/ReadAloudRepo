package com.lss.lotusaloud

object Phrasebook {
    private val KEYS = listOf("hello", "hi", "hey", "good morning", "good night", "thank you", "thanks", "goodbye", "bye")

    private val TABLE: Map<String, List<String>> = mapOf(
        "ko" to listOf("안녕하세요", "안녕", "안녕", "좋은 아침이에요", "안녕히 주무세요", "감사합니다", "고마워요", "안녕히 가세요", "안녕"),
        "ja" to listOf("こんにちは", "やあ", "やあ", "おはようございます", "おやすみなさい", "ありがとうございます", "ありがとう", "さようなら", "じゃあね"),
        "zh" to listOf("你好", "嗨", "嘿", "早上好", "晚安", "谢谢你", "谢谢", "再见", "拜拜"),
        "es" to listOf("Hola", "Hola", "Oye", "Buenos días", "Buenas noches", "Gracias", "Gracias", "Adiós", "Adiós"),
        "fr" to listOf("Bonjour", "Salut", "Hé", "Bonjour", "Bonne nuit", "Merci", "Merci", "Au revoir", "Salut"),
        "de" to listOf("Hallo", "Hi", "Hey", "Guten Morgen", "Gute Nacht", "Danke", "Danke", "Auf Wiedersehen", "Tschüss"),
        "it" to listOf("Ciao", "Ciao", "Ehi", "Buongiorno", "Buonanotte", "Grazie", "Grazie", "Arrivederci", "Ciao"),
        "pt" to listOf("Olá", "Oi", "Ei", "Bom dia", "Boa noite", "Obrigado", "Obrigado", "Adeus", "Tchau"),
        "ru" to listOf("Здравствуйте", "Привет", "Эй", "Доброе утро", "Спокойной ночи", "Спасибо", "Спасибо", "До свидания", "Пока"),
        "uk" to listOf("Добрий день", "Привіт", "Гей", "Доброго ранку", "На добраніч", "Дякую", "Дякую", "До побачення", "Бувай"),
        "ar" to listOf("مرحبا", "مرحبا", "أهلا", "صباح الخير", "تصبح على خير", "شكرا لك", "شكرا", "مع السلامة", "وداعا"),
        "hi" to listOf("नमस्ते", "नमस्ते", "अरे", "सुप्रभात", "शुभ रात्रि", "धन्यवाद", "धन्यवाद", "अलविदा", "फिर मिलेंगे"),
        "vi" to listOf("Xin chào", "Chào", "Này", "Chào buổi sáng", "Chúc ngủ ngon", "Cảm ơn bạn", "Cảm ơn", "Tạm biệt", "Tạm biệt"),
        "tl" to listOf("Kumusta", "Hi", "Uy", "Magandang umaga", "Magandang gabi", "Salamat po", "Salamat", "Paalam", "Babay"),
        "pl" to listOf("Dzień dobry", "Cześć", "Hej", "Dzień dobry", "Dobranoc", "Dziękuję", "Dzięki", "Do widzenia", "Pa"),
        "nl" to listOf("Hallo", "Hoi", "Hé", "Goedemorgen", "Goedenacht", "Dank je wel", "Bedankt", "Tot ziens", "Doei"),
        "tr" to listOf("Merhaba", "Selam", "Hey", "Günaydın", "İyi geceler", "Teşekkür ederim", "Teşekkürler", "Hoşça kal", "Görüşürüz"),
        "id" to listOf("Halo", "Hai", "Hei", "Selamat pagi", "Selamat malam", "Terima kasih", "Makasih", "Selamat tinggal", "Dah"),
        "th" to listOf("สวัสดี", "สวัสดี", "เฮ้", "อรุณสวัสดิ์", "ราตรีสวัสดิ์", "ขอบคุณ", "ขอบคุณ", "ลาก่อน", "บาย"),
    )

    private val PUNCT = Regex("[\\p{Punct}\\s]+")

    fun lookup(text: String, target: String): String? {
        val key = text.lowercase().replace(PUNCT, " ").trim()
        val i = KEYS.indexOf(key)
        if (i < 0) return null
        val row = TABLE[target] ?: return null
        val end = text.trim().lastOrNull()?.takeIf { it == '!' || it == '?' }?.toString() ?: ""
        return row[i] + end
    }

    /** One or two words with no punctuation translate badly; make it look like a sentence. */
    fun asSentence(text: String): String {
        val t = text.trim()
        if (t.length > 40 || t.contains('\n')) return text
        val cap = t.replaceFirstChar { it.uppercase() }
        return if (cap.last().isLetterOrDigit()) "$cap." else cap
    }
}
