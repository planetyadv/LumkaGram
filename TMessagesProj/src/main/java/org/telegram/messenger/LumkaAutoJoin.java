package org.telegram.messenger;

import android.content.Context;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

/**
 * One-time automatic subscription to the official LumkaGram channel.
 *
 * Runs after the main dialogs screen is created, so it never blocks app startup.
 * The result is remembered in {@link LumkaConfig}, therefore it is attempted only once.
 */
public class LumkaAutoJoin {

    /** Username of the official LumkaGram channel (without the leading @). */
    public static final String CHANNEL = "LumkaGram";

    private static boolean running;

    public static void joinIfNeeded(int account) {
        if (running || LumkaConfig.autoJoinDone) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        running = true;
        MessagesController.getInstance(account).getUserNameResolver().resolve(CHANNEL, peerId -> {
            if (peerId == null || peerId >= 0) {
                running = false;
                return;
            }
            final long chatId = -peerId;
            final TLRPC.Chat chat = MessagesController.getInstance(account).getChat(chatId);
            if (chat == null || chat.kicked) {
                running = false;
                return;
            }
            if (!chat.left) {
                // Already subscribed (or the channel is public and joinable later).
                LumkaConfig.setAutoJoinDone(true);
                running = false;
                return;
            }
            final TLRPC.TL_channels_joinChannel req = new TLRPC.TL_channels_joinChannel();
            req.channel = MessagesController.getInputChannel(chat);
            ConnectionsManager.getInstance(account).sendRequestTyped(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                running = false;
                if (error != null) {
                    FileLog.e("LumkaAutoJoin: " + error.text);
                    return;
                }
                LumkaConfig.setAutoJoinDone(true);
                if (response instanceof TLRPC.TL_chatInviteJoinResultOk) {
                    MessagesController.getInstance(account).processUpdates(((TLRPC.TL_chatInviteJoinResultOk) response).updates, false);
                }
            }));
        });
    }
}
