package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;

/**
 * Fake gifts that exist only inside LumkaGram.
 *
 * <p>A fake gift is an ordinary text message that starts with {@link #PREFIX} and carries the
 * real Telegram gift id encoded with zero width characters, so official clients only show a
 * short "FAKE GIFT" line, while LumkaGram replaces the message with a locally fabricated
 * {@code messageActionStarGift} built from the real gift catalog. That way the bubble is rendered
 * by the same code (and with the same sticker) as a real Telegram gift.
 */
public class LumkaGifts {

    /** Text that starts every fake gift message. Visible in official clients. */
    public static final String PREFIX = "\uD83C\uDF81 FAKE GIFT";

    /** Caption that is drawn under the gift card. */
    public static final String CAPTION = "FAKE GIFT";

    private static final char DELIMITER = '\u2060'; // word joiner
    private static final char BIT_ZERO = '\u200B';  // zero width space
    private static final char BIT_ONE = '\u200C';   // zero width non-joiner
    private static final int BITS = 64;

    /**
     * Builds the text of a fake gift message for the given real Telegram gift id.
     */
    public static String build(long giftId) {
        StringBuilder sb = new StringBuilder(PREFIX);
        sb.append(' ').append(DELIMITER);
        for (int i = 0; i < BITS; i++) {
            sb.append(((giftId >>> i) & 1L) == 1L ? BIT_ONE : BIT_ZERO);
        }
        sb.append(DELIMITER);
        return sb.toString();
    }

    /**
     * @return the real Telegram gift id encoded in the message, or 0 when it is not a fake gift.
     */
    public static long parseGiftId(CharSequence text) {
        if (text == null) {
            return 0;
        }
        final String s = text.toString();
        if (!s.startsWith(PREFIX)) {
            return 0;
        }
        final int start = s.indexOf(DELIMITER);
        if (start < 0) {
            return 0;
        }
        final int end = s.indexOf(DELIMITER, start + 1);
        if (end < 0) {
            return 0;
        }
        long id = 0;
        int bit = 0;
        for (int i = start + 1; i < end && bit < BITS; i++, bit++) {
            final char c = s.charAt(i);
            if (c == BIT_ONE) {
                id |= 1L << bit;
            } else if (c != BIT_ZERO) {
                return 0;
            }
        }
        return id;
    }

    public static boolean isGift(CharSequence text) {
        return parseGiftId(text) != 0;
    }

    /**
     * Looks the gift up in the already loaded real Telegram gift catalog. Does not trigger a
     * network request, because this is called from background threads as well.
     */
    public static TL_stars.StarGift findGift(int account, long giftId) {
        if (giftId == 0) {
            return null;
        }
        final StarsController controller = StarsController.getInstance(account);
        if (controller == null) {
            return null;
        }
        final ArrayList<TL_stars.StarGift> gifts = controller.gifts;
        for (int i = 0; i < gifts.size(); i++) {
            final TL_stars.StarGift gift = gifts.get(i);
            if (gift != null && gift.id == giftId && gift.sticker != null) {
                return gift;
            }
        }
        return null;
    }

    /**
     * Builds a service message with a real {@code messageActionStarGift} for a fake gift text
     * message, or {@code null} when the message is not a fake gift or the gift is unknown.
     */
    public static TLRPC.Message toServiceMessage(int account, TLRPC.Message src) {
        if (!(src instanceof TLRPC.TL_message)) {
            return null;
        }
        final long giftId = parseGiftId(src.message);
        if (giftId == 0) {
            return null;
        }
        final long dialogId = MessageObject.getDialogId(src);
        if (dialogId <= 0) {
            return null;
        }
        final TL_stars.StarGift gift = findGift(account, giftId);
        if (gift == null) {
            return null;
        }

        final TLRPC.TL_messageService service = new TLRPC.TL_messageService();
        service.id = src.id;
        service.local_id = src.local_id;
        service.date = src.date;
        service.out = src.out;
        service.unread = src.unread;
        service.mentioned = src.mentioned;
        service.media_unread = src.media_unread;
        service.silent = src.silent;
        service.post = src.post;
        service.legacy = src.legacy;
        service.send_state = src.send_state;
        service.ttl = src.ttl;
        service.seq_in = src.seq_in;
        service.seq_out = src.seq_out;
        service.dialog_id = dialogId;
        service.from_id = src.from_id;
        service.peer_id = src.peer_id;
        service.via_bot_id = src.via_bot_id;

        final TLRPC.TL_peerUser self = new TLRPC.TL_peerUser();
        self.user_id = UserConfig.getInstance(account).getClientUserId();

        if (service.from_id == null) {
            service.from_id = src.out ? self : src.peer_id;
        }

        final TLRPC.TL_messageActionStarGift action = new TLRPC.TL_messageActionStarGift();
        action.gift = gift;
        action.from_id = service.from_id;
        action.to_id = src.out ? src.peer_id : self;
        action.forceIn = !src.out;
        action.convert_stars = 0;
        action.message = new TLRPC.TL_textWithEntities();
        action.message.text = CAPTION;
        action.message.entities = new ArrayList<>();
        service.action = action;
        return service;
    }
}
