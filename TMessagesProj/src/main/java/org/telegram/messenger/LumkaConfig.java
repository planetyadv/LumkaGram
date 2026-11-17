package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class LumkaConfig {

    private static final String PREFS_NAME = "lumkaconfig";

    // Ghost mode
    public static boolean ghostDontRead = false;
    public static boolean ghostDontReadStories = false;
    public static boolean ghostHideTyping = false;

    // Messages
    public static boolean saveDeletedMessages = false;
    public static boolean saveEditedMessages = true;

    // Conveniences
    public static boolean allowCopyRestricted = true;
    public static boolean confirmCall = true;
    public static boolean confirmSendStickerOrGif = true;

    // Privacy
    public static boolean blockScreenshots = false;

    // Appearance
    public static boolean hideDialogsAvatars = false;
    public static boolean localPremium = false;
    public static boolean hideAds = true;
    public static String customBadge = "";

    // Fake gifts & client auto-detection
    public static String modUserIds = "";
    private static final Set<Long> detectedModUsers = Collections.synchronizedSet(new HashSet<>());

    // One-time auto subscription to the official @LumkaGram channel.
    public static boolean autoJoinDone = false;

    // Custom mute: dialog ids whose incoming messages are deleted instantly (for both sides).
    private static final Set<Long> mutedChats = Collections.synchronizedSet(new HashSet<>());
    // Antimute: dialog ids where any mute, including a mute set by a foreign client, is bypassed.
    private static final Set<Long> antimuteChats = Collections.synchronizedSet(new HashSet<>());

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

        saveDeletedMessages = prefs.getBoolean("saveDeletedMessages", false);
        saveEditedMessages = prefs.getBoolean("saveEditedMessages", true);

        allowCopyRestricted = prefs.getBoolean("allowCopyRestricted", true);
        confirmCall = prefs.getBoolean("confirmCall", true);
        confirmSendStickerOrGif = prefs.getBoolean("confirmSendStickerOrGif", true);

        blockScreenshots = prefs.getBoolean("blockScreenshots", false);

        hideDialogsAvatars = prefs.getBoolean("hideDialogsAvatars", false);
        localPremium = prefs.getBoolean("localPremium", false);
        hideAds = prefs.getBoolean("hideAds", true);
        customBadge = prefs.getString("customBadge", "");

        modUserIds = prefs.getString("modUserIds", "");
        parseModUsers(modUserIds);

        autoJoinDone = prefs.getBoolean("autoJoinDone", false);

        parseIdSet(prefs.getString("mutedChats", ""), mutedChats);
        parseIdSet(prefs.getString("antimuteChats", ""), antimuteChats);

        loaded = true;
    }

    private static void parseIdSet(String string, Set<Long> target) {
        target.clear();
        if (string == null || string.isEmpty()) return;
        for (String part : string.split("[,\\s;]+")) {
            if (part.isEmpty()) continue;
            try {
                target.add(Long.parseLong(part));
            } catch (NumberFormatException ignore) {
            }
        }
    }

    private static void saveIdSet(String key, Set<Long> set) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return;
        StringBuilder sb = new StringBuilder();
        synchronized (set) {
            for (long id : set) {
                if (sb.length() > 0) sb.append(',');
                sb.append(id);
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, sb.toString())
                .apply();
    }

    public static boolean isMutedChat(long dialogId) {
        return mutedChats.contains(dialogId);
    }

    public static boolean isAntimuteChat(long dialogId) {
        return antimuteChats.contains(dialogId);
    }

    public static void setMutedChat(long dialogId, boolean value) {
        if (value) {
            mutedChats.add(dialogId);
        } else {
            mutedChats.remove(dialogId);
        }
        saveIdSet("mutedChats", mutedChats);
    }

    public static void setAntimuteChat(long dialogId, boolean value) {
        if (value) {
            antimuteChats.add(dialogId);
        } else {
            antimuteChats.remove(dialogId);
        }
        saveIdSet("antimuteChats", antimuteChats);
    }

    private static void parseModUsers(String string) {
        if (string == null || string.isEmpty()) return;
        for (String part : string.split("[,\\s;]+")) {
            if (part.isEmpty()) continue;
            try {
                detectedModUsers.add(Long.parseLong(part));
            } catch (NumberFormatException ignore) {
            }
        }
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
            case "localPremium":
                localPremium = value;
                break;
            case "hideAds":
                hideAds = value;
                break;
        }
    }

    public static void setCustomBadge(String badge) {
        customBadge = badge == null ? "" : badge.trim();
        Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("customBadge", customBadge)
                    .apply();
        }
    }

    public static void setModUserIds(String value) {
        modUserIds = value == null ? "" : value.trim();
        detectedModUsers.clear();
        parseModUsers(modUserIds);
        Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("modUserIds", modUserIds)
                    .apply();
        }
    }

    /**
     * Automatically registers a detected LumkaGram user ID.
     */
    public static synchronized void addModUser(long id) {
        if (id <= 0 || detectedModUsers.contains(id)) {
            return;
        }
        detectedModUsers.add(id);
        StringBuilder sb = new StringBuilder(modUserIds == null ? "" : modUserIds.trim());
        if (sb.length() > 0 && !sb.toString().endsWith(",")) {
            sb.append(",");
        }
        sb.append(id);
        modUserIds = sb.toString();
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
     * Checks if a user is a LumkaGram user (creator, self, automatically detected, or manually added).
     */
    public static boolean isModUser(long id) {
        if (id == 0) {
            return false;
        }
        if (LumkaCreator.isCreator(id)) {
            return true;
        }
        try {
            if (UserConfig.selectedAccount >= 0 && id == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        if (detectedModUsers.contains(id)) {
            return true;
        }
        return false;
    }
}
