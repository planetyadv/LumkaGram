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

    // Privacy
    public static boolean blockScreenshots = false;

    // Appearance
    public static boolean hideDialogsAvatars = false;

    // Fake gifts: comma separated user ids that are considered LumkaGram users.
    public static String modUserIds = "";

    // One-time auto subscription to the official @LumkaGram channel.
    public static boolean autoJoinDone = false;

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

        blockScreenshots = prefs.getBoolean("blockScreenshots", false);

        hideDialogsAvatars = prefs.getBoolean("hideDialogsAvatars", false);

        modUserIds = prefs.getString("modUserIds", "");

        autoJoinDone = prefs.getBoolean("autoJoinDone", false);

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
            case "blockScreenshots":
                blockScreenshots = value;
                break;
            case "hideDialogsAvatars":
                hideDialogsAvatars = value;
                break;
        }
    }

    public static void setModUserIds(String value) {
        modUserIds = value == null ? "" : value.trim();
        Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("modUserIds", modUserIds)
                    .apply();
        }
    }

    public static void setAutoJoinDone(boolean value) {
        autoJoinDone = value;
        Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("autoJoinDone", value)
                    .apply();
        }
    }

    /**
     * A user is considered a LumkaGram user when their id is listed in the settings.
     * Fake gifts are only available between such users.
     */
    public static boolean isModUser(long id) {
        if (id == 0 || modUserIds == null || modUserIds.isEmpty()) {
            return false;
        }
        for (String part : modUserIds.split("[,\\s;]+")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                if (Long.parseLong(part) == id) {
                    return true;
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return false;
    }
}
