package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

public class LumkaConfig {

    private static final String PREFS_NAME = "lumkaconfig";

    // Ghost mode
    public static boolean ghostDontRead = false;
    public static boolean ghostDontReadStories = false;
    public static boolean ghostHideTyping = false;

    // Messages
    public static boolean saveDeletedMessages = true;
    public static boolean saveEditedMessages = true;

    // Conveniences
    public static boolean allowCopyRestricted = true;
    public static boolean confirmCall = true;
    public static boolean confirmSendStickerOrGif = true;

    private static boolean loaded = false;

    static {
        load();
    }

    public static void load() {
        if (loaded) return;
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        ghostDontRead = prefs.getBoolean("ghostDontRead", false);
        ghostDontReadStories = prefs.getBoolean("ghostDontReadStories", false);
        ghostHideTyping = prefs.getBoolean("ghostHideTyping", false);

        saveDeletedMessages = prefs.getBoolean("saveDeletedMessages", true);
        saveEditedMessages = prefs.getBoolean("saveEditedMessages", true);

        allowCopyRestricted = prefs.getBoolean("allowCopyRestricted", true);
        confirmCall = prefs.getBoolean("confirmCall", true);
        confirmSendStickerOrGif = prefs.getBoolean("confirmSendStickerOrGif", true);

        loaded = true;
    }

    public static void toggle(String key, boolean value) {
        Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putBoolean(key, value);
            editor.apply();
        }

        switch (key) {
            case "ghostDontRead":
                ghostDontRead = value;
                break;
            case "ghostDontReadStories":
                ghostDontReadStories = value;
                break;
            case "ghostHideTyping":
                ghostHideTyping = value;
                break;
            case "saveDeletedMessages":
                saveDeletedMessages = value;
                break;
            case "saveEditedMessages":
                saveEditedMessages = value;
                break;
            case "allowCopyRestricted":
                allowCopyRestricted = value;
                break;
            case "confirmCall":
                confirmCall = value;
                break;
            case "confirmSendStickerOrGif":
                confirmSendStickerOrGif = value;
                break;
        }
    }
}
