package org.telegram.messenger;

/**
 * Fake "gifts" that exist only inside LumkaGram. They are ordinary text messages
 * with a recognisable header, so any Telegram client shows them as text, while
 * LumkaGram can treat them as gifts. Sending is limited to users listed as
 * LumkaGram users in the settings.
 */
public class LumkaGifts {

    public static final String PREFIX = "🎁 Lumka Gift";

    public static final String[] EMOJI = {
            "🌹", "💎", "👑", "🧸", "🍾", "🚀", "💝", "🏆", "🦄", "🍀", "🔥", "🎂"
    };

    public static final String[] NAMES = {
            "Роза", "Алмаз", "Корона", "Мишка", "Шампанское", "Ракета",
            "Сердце", "Кубок", "Единорог", "Клевер", "Огонь", "Торт"
    };

    public static String build(int index) {
        if (index < 0 || index >= EMOJI.length) {
            index = 0;
        }
        return PREFIX + "\n" + EMOJI[index] + " " + NAMES[index];
    }

    /**
     * @return index of the gift, or -1 when the text is not a LumkaGram gift.
     */
    public static int parse(CharSequence text) {
        if (text == null) {
            return -1;
        }
        String s = text.toString();
        if (!s.startsWith(PREFIX)) {
            return -1;
        }
        for (int i = 0; i < NAMES.length; i++) {
            if (s.contains(NAMES[i])) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isGift(CharSequence text) {
        return parse(text) >= 0;
    }
}
