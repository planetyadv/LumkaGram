package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/**
 * Local LumkaGram badge for the creator of the client.
 *
 * Telegram does not give forks a real verified badge, so LumkaGram draws the standard
 * verified check itself for the account whose username matches the client creator.
 */
public class LumkaCreator {

    /** Username of the LumkaGram client creator (without the leading @). */
    public static final String USERNAME = "ducnskd";

    public static boolean isCreator(String username) {
        return username != null && username.equalsIgnoreCase(USERNAME);
    }

    public static boolean isCreator(TLRPC.User user) {
        return user != null && isCreator(user.username);
    }

    public static boolean isCreator(TLRPC.Chat chat) {
        return chat != null && isCreator(chat.username);
    }
}
