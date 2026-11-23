package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashMap;
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

    /** Re-send counter per dialog/message to keep the delete/re-send fight finite. */
    private static final HashMap<String, Integer> resendCounts = new HashMap<>();
    /** Messages we deleted ourselves, so antimute does not resurrect them. */
    private static final HashMap<String, Long> localDeletions = new HashMap<>();
    /** Per-dialog re-send budget: {count, windowStart}. */
    private static final HashMap<Long, long[]> dialogResends = new HashMap<>();
    private static final long LOCAL_DELETION_TTL = 60_000L;

    /** Recognises mute commands used by other clients: .mute, ..mute, /mute (and @bot variants). */
    public static boolean isMuteCommand(String text) {
        if (text == null) return false;
        String t = text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return false;
        int at = t.indexOf('@');
        if (at > 0) {
            t = t.substring(0, at).trim();
        }
        return t.equals(".mute") || t.equals("..mute") || t.equals("/mute")
                || t.equals(".мут") || t.equals("..мут") || t.equals("/мут")
                || t.equals(".m") || t.equals("..m") || t.equals("/m");
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
        return t.equals(".unmute") || t.equals("..unmute") || t.equals("/unmute")
                || t.equals(".анмут") || t.equals("..анмут") || t.equals("/анмут")
                || t.equals(".размут") || t.equals("..размут") || t.equals("/размут");
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

    /**
     * Called when messages were deleted by somebody else (a remote update, not a local user action).
     * In antimute chats every deleted outgoing message is re-sent immediately, so the
     * interlocutor sees it again even if their client tries to erase it.
     */
    public static void onRemoteDeletion(int account, ArrayList<MessageObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        for (int i = 0, size = objects.size(); i < size; i++) {
            final MessageObject obj = objects.get(i);
            if (obj == null || obj.messageOwner == null || !obj.isOut()) {
                continue;
            }
            if (obj.messageOwner instanceof TLRPC.TL_messageService) {
                continue;
            }
            final long dialogId = obj.getDialogId();
            if (dialogId == 0 || !LumkaConfig.isAntimuteChat(dialogId)) {
                continue;
            }
            final CharSequence text = buildResendText(obj);
            if (text == null || text.length() == 0) {
                continue;
            }
            if (isLocalDeletion(dialogId, obj.getId())) {
                continue;
            }
            if (allowResend(dialogId, obj.getId())) {
                AndroidUtilities.runOnUIThread(() -> {
                    SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(text.toString(), dialogId);
                    if (obj.messageOwner.entities != null && !obj.messageOwner.entities.isEmpty() && obj.messageOwner.message != null) {
                        params.entities = obj.messageOwner.entities;
                    }
                    params.notify = true;
                    SendMessagesHelper.getInstance(account).sendMessage(params);
                });
            }
        }
    }

    /** Called by MessagesController when this client initiates a deletion. */
    public static void markLocalDeletion(long dialogId, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        synchronized (localDeletions) {
            for (int i = 0, size = ids.size(); i < size; i++) {
                Integer id = ids.get(i);
                if (id != null) {
                    localDeletions.put(dialogId + ":" + id, now);
                }
            }
            if (localDeletions.size() > 1000) {
                for (java.util.Iterator<java.util.Map.Entry<String, Long>> it = localDeletions.entrySet().iterator(); it.hasNext(); ) {
                    if (now - it.next().getValue() > LOCAL_DELETION_TTL) {
                        it.remove();
                    }
                }
            }
        }
    }

    private static boolean isLocalDeletion(long dialogId, int mid) {
        final long now = System.currentTimeMillis();
        synchronized (localDeletions) {
            Long time = localDeletions.remove(dialogId + ":" + mid);
            return time != null && now - time <= LOCAL_DELETION_TTL;
        }
    }

    private static CharSequence buildResendText(MessageObject obj) {
        final String text = obj.messageOwner.message;
        if (text != null && !text.isEmpty()) {
            return text;
        }
        final TLRPC.MessageMedia media = obj.messageOwner.media;
        if (media == null) {
            return null;
        }
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            return "🖼 Фото (восстановлено антимутом)";
        } else if (media instanceof TLRPC.TL_messageMediaDocument) {
            if (obj.isSticker()) {
                return "🎨 Стикер (восстановлено антимутом)";
            } else if (obj.isGif()) {
                return "🎞 GIF (восстановлено антимутом)";
            } else if (obj.isVideo()) {
                return "🎬 Видео (восстановлено антимутом)";
            } else if (obj.isVoice()) {
                return "🎤 Голосовое (восстановлено антимутом)";
            }
            return "📎 Файл (восстановлено антимутом)";
        } else if (media instanceof TLRPC.TL_messageMediaPoll) {
            return "📊 Опрос (восстановлено антимутом)";
        } else if (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaGeoLive) {
            return "📍 Геопозиция (восстановлено антимутом)";
        }
        return null;
    }

    /** At most 5 re-sends for the same message to avoid an endless delete/re-send loop. */
    private static boolean allowResend(long dialogId, int mid) {
        final String key = dialogId + ":" + mid;
        synchronized (resendCounts) {
            Integer count = resendCounts.get(key);
            int next = count == null ? 1 : count + 1;
            if (next > 5) {
                return false;
            }
            resendCounts.put(key, next);
            if (resendCounts.size() > 500) {
                resendCounts.clear();
                resendCounts.put(key, next);
            }
        }
        // Global per-dialog throttle: 20 re-sends per 10 minutes, no matter how many
        // times the other side deletes the re-sent copies (each copy gets a new id).
        synchronized (dialogResends) {
            long now = System.currentTimeMillis();
            long[] state = dialogResends.get(dialogId);
            if (state == null || now - state[1] > 600_000L) {
                state = new long[]{0, now};
                dialogResends.put(dialogId, state);
            }
            if (state[0] >= 20) {
                return false;
            }
            state[0]++;
        }
        return true;
    }
}
