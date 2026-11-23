package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Global music search for LumkaGram (..music command).
 */
public class LumkaMusic {

    public static class Track {
        public final String title;
        public final String performer;
        public final int duration;
        public final TLRPC.Document document;
        public final MessageObject messageObject;

        public Track(String title, String performer, int duration, TLRPC.Document document, MessageObject messageObject) {
            this.title = (title != null && !title.isEmpty()) ? title : "Трек";
            this.performer = (performer != null && !performer.isEmpty()) ? performer : "Неизвестный исполнитель";
            this.duration = duration;
            this.document = document;
            this.messageObject = messageObject;
        }

        public String getFormattedDuration() {
            if (duration <= 0) return "";
            int m = duration / 60;
            int s = duration % 60;
            return String.format(java.util.Locale.US, "%d:%02d", m, s);
        }

        public String getDisplayName() {
            return performer + " — " + title;
        }
    }

    public interface SearchCallback {
        void onResults(ArrayList<Track> tracks);
    }

    private static int lastReqId = 0;

    public static void search(int account, String query, SearchCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            if (callback != null) callback.onResults(new ArrayList<>());
            return;
        }
        if (lastReqId != 0) {
            ConnectionsManager.getInstance(account).cancelRequest(lastReqId, true);
            lastReqId = 0;
        }

        TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.filter = new TLRPC.TL_inputMessagesFilterMusic();
        req.q = query.trim();
        req.limit = 25;

        lastReqId = ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            ArrayList<Track> tracks = new ArrayList<>();
            if (response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                MessagesController.getInstance(account).putUsers(res.users, false);
                MessagesController.getInstance(account).putChats(res.chats, false);
                for (TLRPC.Message msg : res.messages) {
                    if (msg != null && msg.media instanceof TLRPC.TL_messageMediaDocument) {
                        TLRPC.Document doc = msg.media.document;
                        if (doc != null) {
                            String title = null;
                            String performer = null;
                            int duration = 0;
                            for (TLRPC.DocumentAttribute attr : doc.attributes) {
                                if (attr instanceof TLRPC.TL_documentAttributeAudio) {
                                    TLRPC.TL_documentAttributeAudio audioAttr = (TLRPC.TL_documentAttributeAudio) attr;
                                    title = audioAttr.title;
                                    performer = audioAttr.performer;
                                    duration = audioAttr.duration;
                                    break;
                                }
                            }
                            MessageObject obj = new MessageObject(account, msg, false, true);
                            tracks.add(new Track(title, performer, duration, doc, obj));
                        }
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    callback.onResults(tracks);
                }
            });
        });
    }
}
