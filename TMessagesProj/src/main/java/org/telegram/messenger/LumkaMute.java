package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Client-side mute and antimute.
 *
 * Mute deletes every incoming message in the chat immediately and for both sides.
 * Antimute protects the chat: it never deletes messages and it swallows mute commands
 * sent by users of other (non-LumkaGram) clients.
 *
 * Telegram has no server-side API that lets one account change another account's local
 * mute setting, so antimute works on our side: the foreign mute command is discarded,
 * our client stays unmuted, and deleted messages are kept by the save-deleted feature.
 */
public class LumkaMute {

    /** Recognises mute commands used by other clients: .mute, ..mute, /mute (and @bot variants). */
    public static boolean isMuteCommand(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return false;
        int at = t.indexOf('@');
        if (at > 0) {
            t = t.substring(0, at).trim();
        }
        return t.equals(".mute") || t.equals("..mute") || t.equals("/mute");
    }

    /** Same, but for an unmute command. */
    public static boolean isUnmuteCommand(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return false;
        int at = t.indexOf('@');
        if (at > 0) {
            t = t.substring(0, at).trim();
        }
        return t.equals(".unmute") || t.equals("..unmute") || t.equals("/unmute");
    }

    /**
     * Called for every batch of newly received messages before it reaches the UI.
     * Deletes incoming messages when the chat is muted and blocks foreign mute commands
     * when antimute is enabled for the chat.
     */
    public static void handleIncomingMessages(int account, long dialogId, ArrayList<MessageObject> messages, int mode) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        if (mode != ChatActivity.MODE_DEFAULT) {
            return;
        }
        final boolean muted = LumkaConfig.isMutedChat(dialogId);
        final boolean antimute = LumkaConfig.isAntimuteChat(dialogId);
        if (!muted && !antimute) {
            return;
        }

        final ArrayList<Integer> toDelete = new ArrayList<>();
        boolean blockedMute = false;
        for (int i = 0, size = messages.size(); i < size; i++) {
            final MessageObject message = messages.get(i);
            if (message == null || message.messageOwner == null || message.isOut()) {
                continue;
            }
            if (message.messageOwner instanceof TLRPC.TL_messageService) {
                continue;
            }
            final String text = message.messageOwner.message;
            if (antimute && (isMuteCommand(text) || isUnmuteCommand(text))) {
                // A foreign client tried to mute/unmute us. Drop the command, keep the chat usable.
                toDelete.add(message.getId());
                blockedMute = true;
                continue;
            }
            if (muted) {
                toDelete.add(message.getId());
            }
        }
        if (toDelete.isEmpty()) {
            return;
        }
        final boolean showBlocked = blockedMute;
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController.getInstance(account).deleteMessages(toDelete, null, null, dialogId, 0, true, 0);
            if (showBlocked) {
                NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.lumkaAntimuteBlocked, dialogId);
            }
        });
    }
}
