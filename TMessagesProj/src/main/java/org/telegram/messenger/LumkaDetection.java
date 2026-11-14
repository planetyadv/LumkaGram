package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * Automatic detection of LumkaGram users.
 *
 * Appends an invisible zero-width watermark to outgoing text messages.
 * When ANY client running LumkaGram receives a message with this watermark
 * (or a fake gift), the sender is automatically registered as a LumkaGram user
 * without requiring any manual user ID entry.
 */
public class LumkaDetection {

    /**
     * Invisible 4-character zero-width signature:
     * Zero-width space + Word joiner + Zero-width non-joiner + Zero-width joiner.
     * Takes 0 pixels and is completely invisible in all official Telegram apps.
     */
    public static final String WATERMARK = "\u200B\u2060\u200C\u200D";

    /**
     * Attaches the invisible watermark to an outgoing message text if not already present.
     */
    public static String attachWatermark(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.contains(WATERMARK) || text.startsWith(LumkaGifts.PREFIX)) {
            return text;
        }
        return text + WATERMARK;
    }

    /**
     * Checks if a message text contains the invisible Lumka watermark or a fake gift.
     */
    public static boolean hasWatermark(CharSequence text) {
        if (text == null) {
            return false;
        }
        String s = text.toString();
        return s.contains(WATERMARK) || s.startsWith(LumkaGifts.PREFIX);
    }

    /**
     * Inspects an incoming or outgoing message. If it carries the Lumka watermark
     * or a fake gift, automatically registers the sender as a LumkaGram user.
     */
    public static void inspectMessage(TLRPC.Message message) {
        if (message == null || message.message == null) {
            return;
        }
        if (hasWatermark(message.message)) {
            long senderId = 0;
            if (message.from_id instanceof TLRPC.TL_peerUser) {
                senderId = message.from_id.user_id;
            } else if (message.peer_id instanceof TLRPC.TL_peerUser && !message.out) {
                senderId = message.peer_id.user_id;
            } else if (message.peer_id instanceof TLRPC.TL_peerUser && message.out) {
                // If it's outgoing, the destination in private chat is also a Lumka chat
                senderId = message.peer_id.user_id;
            }
            if (senderId > 0) {
                LumkaConfig.addModUser(senderId);
            }
        }
    }
}
