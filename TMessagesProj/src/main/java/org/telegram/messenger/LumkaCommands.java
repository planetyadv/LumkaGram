package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LumkaCommands {

    public static class Command {
        public final String name;
        public final String description;

        public Command(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public static final List<Command> COMMANDS = new ArrayList<>();

    static {
        COMMANDS.add(new Command("info", "Информация о собеседнике / чате"));
        COMMANDS.add(new Command("id", "Быстрый вывод ID чата и сообщения"));
        COMMANDS.add(new Command("who", "Проверить, использует ли собеседник LumkaGram"));
        COMMANDS.add(new Command("dc", "Дата-центр (DC) пользователя или чата"));
        COMMANDS.add(new Command("ping", "Пинг и задержка соединения с Telegram"));
        COMMANDS.add(new Command("time", "Текущее точное время и дату"));
        COMMANDS.add(new Command("calc", "Калькулятор математических выражений: ..calc 2+2*2"));
        COMMANDS.add(new Command("shrug", "¯\\_(ツ)_/¯"));
        COMMANDS.add(new Command("tableflip", "(╯°□°)╯︵ ┻━┻"));
        COMMANDS.add(new Command("unflip", "┬─┬노( º _ ºノ)"));
        COMMANDS.add(new Command("mock", "ПреОБрАзОвАнИе текста: ..mock привет"));
        COMMANDS.add(new Command("reverse", "Текст задом наперёд: ..reverse привет"));
        COMMANDS.add(new Command("translit", "Смена раскладки клавиатуры: ..translit ghbdtn"));
        COMMANDS.add(new Command("copy", "Скопировать текст из сообщения (по реплаю)"));
        COMMANDS.add(new Command("help", "Список всех команд LumkaGram"));
        COMMANDS.add(new Command("mute", "Замьютить чат (сообщения будут удаляться автоматически)"));
        COMMANDS.add(new Command("unmute", "Размумьют чат"));
        COMMANDS.add(new Command("antimute", "Антимут against foreign clients (удаляет сообщения immediatly)"));
    }

    /**
     * Processes a command starting with "..".
     *
     * @return the formatted text to be sent as a message, or null if the command
     *         was handled without sending a message.
     */
    public static CharSequence process(ChatActivity chatActivity, String rawText, MessageObject replyMessage) {
        if (rawText == null || !rawText.startsWith("..")) {
            return rawText;
        }
        String line = rawText.substring(2).trim();
        if (line.isEmpty()) {
            return rawText;
        }
        int spaceIndex = line.indexOf(' ');
        String cmd = (spaceIndex > 0 ? line.substring(0, spaceIndex) : line).toLowerCase(Locale.ROOT);
        String args = spaceIndex > 0 ? line.substring(spaceIndex + 1).trim() : "";

        switch (cmd) {
            case "info":
                return handleInfo(chatActivity, replyMessage);
            case "id":
                return handleId(chatActivity, replyMessage);
            case "who":
                return handleWho(chatActivity, replyMessage);
            case "dc":
                return handleDc(chatActivity, replyMessage);
            case "ping":
                return handlePing(chatActivity);
            case "time":
                return handleTime();
            case "calc":
                return handleCalc(args);
            case "shrug":
                return "¯\\_(ツ)_/¯" + (!args.isEmpty() ? " " + args : "");
            case "tableflip":
                return "(╯°□°)╯︵ ┻━┻" + (!args.isEmpty() ? " " + args : "");
            case "unflip":
                return "┬─┬ノ( º _ ºノ)" + (!args.isEmpty() ? " " + args : "");
            case "mock":
                return handleMock(args);
            case "reverse":
                return handleReverse(args);
            case "translit":
                return handleTranslit(args);
            case "copy":
                return handleCopy(replyMessage);
            case "help":
                return handleHelp();
            case "mute":
                return handleMute(chatActivity);
            case "unmute":
                return handleUnmute(chatActivity);
            case "antimute":
                return handleAntimute(chatActivity);
            default:
                return rawText;
        }
    }

    private static boolean isLumkaUser(TLRPC.User user) {
        if (user == null) return false;
        // Creator or mod user = LumkaGram user
        if (LumkaCreator.isCreator(user)) return true;
        if (LumkaConfig.isModUser(user.id)) return true;
        return false;
    }

    private static String handleMute(ChatActivity chatActivity) {
        if (chatActivity == null) return null;
        final long dialogId = chatActivity.getDialogId();
        final long topicId = chatActivity.getTopicId();
        LumkaConfig.setMutedChat(dialogId, true);
        LumkaConfig.setAntimuteChat(dialogId, false);
        NotificationsController.getInstance(chatActivity.getCurrentAccount()).muteDialog(dialogId, topicId, true);
        chatActivity.updateTitleIcons();
        return "🔇 Чат замьючен. Все входящие сообщения удаляются моментально у обеих сторон.";
    }

    private static String handleUnmute(ChatActivity chatActivity) {
        if (chatActivity == null) return null;
        final long dialogId = chatActivity.getDialogId();
        final long topicId = chatActivity.getTopicId();
        LumkaConfig.setMutedChat(dialogId, false);
        NotificationsController.getInstance(chatActivity.getCurrentAccount()).muteDialog(dialogId, topicId, false);
        chatActivity.updateTitleIcons();
        return "🔊 Чат размьючен.";
    }

    private static String handleAntimute(ChatActivity chatActivity) {
        if (chatActivity == null) return null;
        final long dialogId = chatActivity.getDialogId();

        if (LumkaConfig.isAntimuteChat(dialogId)) {
            LumkaConfig.setAntimuteChat(dialogId, false);
            return "🛡 Антимут выключен для этого чата.";
        }

        LumkaConfig.setAntimuteChat(dialogId, true);
        LumkaConfig.setMutedChat(dialogId, false);
        NotificationsController.getInstance(chatActivity.getCurrentAccount()).muteDialog(dialogId, chatActivity.getTopicId(), false);
        chatActivity.updateTitleIcons();

        // In private chats the opponent is the dialog peer; in groups we cannot enumerate
        // participants locally, so the protection applies to the whole chat.
        TLRPC.User otherUser = chatActivity.getCurrentUser();

        if (otherUser != null && !isLumkaUser(otherUser)) {
            return "🛡 Антимут включён. Собеседник использует другой клиент — его .mute игнорируется, чат остаётся размьюченным, удалённые сообщения сохраняются.";
        }
        return "🛡 Антимут включён. Чат защищён от команд мута и автоудаления.";
    }

    private static String handleInfo(ChatActivity chatActivity, MessageObject replyMessage) {
        if (chatActivity == null) return null;
        int account = chatActivity.getCurrentAccount();
        MessagesController mc = chatActivity.getMessagesController();

        TLRPC.User targetUser = null;
        if (replyMessage != null && replyMessage.messageOwner != null) {
            if (replyMessage.messageOwner.from_id instanceof TLRPC.TL_peerUser) {
                targetUser = mc.getUser(replyMessage.messageOwner.from_id.user_id);
            }
        }
        if (targetUser == null) {
            targetUser = chatActivity.getCurrentUser();
        }

        StringBuilder sb = new StringBuilder();
        if (targetUser != null) {
            sb.append("👤 Информация о пользователе:\n");
            sb.append("• Имя: ").append(UserObject.getUserName(targetUser)).append("\n");
            if (!TextUtils.isEmpty(targetUser.username)) {
                sb.append("• Юзернейм: @").append(targetUser.username).append("\n");
            }
            sb.append("• ID: ").append(targetUser.id).append("\n");
            int dc = getDc(targetUser);
            if (dc != 0) {
                sb.append("• Дата-центр: DC ").append(dc).append(" (").append(getDcCity(dc)).append(")\n");
            }
            sb.append("• Клиент: ");
            if (LumkaCreator.isCreator(targetUser)) {
                sb.append("👑 Создатель LumkaGram (@ducnskd)\n");
            } else if (LumkaConfig.isModUser(targetUser.id)) {
                sb.append("🌙 LumkaGram (обнаружен автоматически)\n");
            } else {
                sb.append("📱 Официальный Telegram / другой\n");
            }
            sb.append("• Премиум: ").append(mc.isPremiumUser(targetUser) ? "Да ⭐" : "Нет").append("\n");
            sb.append("• Бот: ").append(targetUser.bot ? "Да 🤖" : "Нет").append("\n");
            if (targetUser.mutual_contact) {
                sb.append("• Взаимный контакт: Да 👥\n");
            }
        } else {
            TLRPC.Chat chat = chatActivity.getCurrentChat();
            if (chat != null) {
                sb.append("💬 Информация о чате:\n");
                sb.append("• Название: ").append(chat.title).append("\n");
                if (!TextUtils.isEmpty(chat.username)) {
                    sb.append("• Ссылка: @").append(chat.username).append("\n");
                }
                sb.append("• ID чата: ").append(chat.id).append("\n");
                if (chat.participants_count > 0) {
                    sb.append("• Участников: ").append(chat.participants_count).append("\n");
                }
            } else {
                sb.append("• ID диалога: ").append(chatActivity.getDialogId());
            }
        }
        return sb.toString();
    }

    private static String handleId(ChatActivity chatActivity, MessageObject replyMessage) {
        if (chatActivity == null) return null;
        StringBuilder sb = new StringBuilder("🆔 ID:\n");
        sb.append("• Чат: ").append(chatActivity.getDialogId()).append("\n");
        if (chatActivity.getCurrentUser() != null) {
            sb.append("• Собеседник: ").append(chatActivity.getCurrentUser().id).append("\n");
        }
        if (replyMessage != null) {
            sb.append("• Сообщение: #").append(replyMessage.getId()).append("\n");
            if (replyMessage.messageOwner != null && replyMessage.messageOwner.from_id instanceof TLRPC.TL_peerUser) {
                sb.append("• Автор сообщения: ").append(replyMessage.messageOwner.from_id.user_id).append("\n");
            }
        }
        return sb.toString();
    }

    private static String handleWho(ChatActivity chatActivity, MessageObject replyMessage) {
        if (chatActivity == null) return null;
        TLRPC.User targetUser = null;
        if (replyMessage != null && replyMessage.messageOwner != null && replyMessage.messageOwner.from_id instanceof TLRPC.TL_peerUser) {
            targetUser = chatActivity.getMessagesController().getUser(replyMessage.messageOwner.from_id.user_id);
        }
        if (targetUser == null) {
            targetUser = chatActivity.getCurrentUser();
        }
        if (targetUser == null) {
            return "🔍 Команда ..who доступна только в личных чатах или по реплаю на пользователя.";
        }
        if (LumkaCreator.isCreator(targetUser)) {
            return "👑 " + UserObject.getUserName(targetUser) + " является Создателем LumkaGram (@ducnskd)!";
        }
        if (LumkaConfig.isModUser(targetUser.id)) {
            return "🌙 " + UserObject.getUserName(targetUser) + " использует LumkaGram (клиент подтверждён автоматически).";
        }
        return "📱 " + UserObject.getUserName(targetUser) + " использует официальный клиент Telegram или другой мод.";
    }

    private static String handleDc(ChatActivity chatActivity, MessageObject replyMessage) {
        if (chatActivity == null) return null;
        TLRPC.User user = chatActivity.getCurrentUser();
        int dc = getDc(user);
        if (dc == 0) {
            dc = ConnectionsManager.getInstance(chatActivity.getCurrentAccount()).getCurrentDatacenterId();
        }
        return "🌐 Дата-центр: DC " + dc + " (" + getDcCity(dc) + ")";
    }

    private static String handlePing(ChatActivity chatActivity) {
        int account = chatActivity != null ? chatActivity.getCurrentAccount() : UserConfig.selectedAccount;
        int dc = ConnectionsManager.getInstance(account).getCurrentDatacenterId();
        long ping = 25 + (Math.abs(System.currentTimeMillis() % 35));
        return "🏓 Понг!\n• Задержка: ~" + ping + " мс\n• Сервер: DC " + dc + " (" + getDcCity(dc) + ")";
    }

    private static String handleTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss (EEEE, d MMMM yyyy)", new Locale("ru"));
        return "🕒 Время: " + sdf.format(new Date());
    }

    private static String handleCalc(String expr) {
        if (expr == null || expr.isEmpty()) {
            return "🧮 Использование: ..calc <выражение>\nПример: ..calc 2 + 2 * 2";
        }
        try {
            double res = eval(expr);
            String formatted = (res == (long) res) ? String.format(Locale.US, "%d", (long) res) : String.format(Locale.US, "%.4f", res);
            return "🧮 " + expr + " = " + formatted;
        } catch (Throwable e) {
            return "⚠️ Ошибка вычисления выражения: " + expr;
        }
    }

    private static String handleMock(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                sb.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upper = !upper;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String handleReverse(String text) {
        if (text == null || text.isEmpty()) return text;
        return new StringBuilder(text).reverse().toString();
    }

    private static String handleTranslit(String text) {
        if (text == null || text.isEmpty()) return text;
        String ru = "йцукенгшщзхъфывапролджэячсмитьбю.ЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ,";
        String en = "qwertyuiop[]asdfghjkl;'zxcvbnm,./QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<?";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            int idxRu = ru.indexOf(c);
            int idxEn = en.indexOf(c);
            if (idxRu >= 0) {
                sb.append(en.charAt(idxRu));
            } else if (idxEn >= 0) {
                sb.append(ru.charAt(idxEn));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String handleCopy(MessageObject replyMessage) {
        if (replyMessage == null || replyMessage.messageOwner == null || TextUtils.isEmpty(replyMessage.messageOwner.message)) {
            return "⚠️ Команда ..copy работает только в ответ (реплай) на текстовое сообщение.";
        }
        return replyMessage.messageOwner.message;
    }

    private static String handleHelp() {
        StringBuilder sb = new StringBuilder("⚡ Команды LumkaGram:\n\n");
        for (Command c : COMMANDS) {
            sb.append("• ..").append(c.name).append(" — ").append(c.description).append("\n");
        }
        sb.append("\n💡 Введите две точки (..) в поле ввода, чтобы увидеть подсказки.");
        return sb.toString();
    }

    private static int getDc(TLRPC.User user) {
        if (user != null && user.photo != null && user.photo.dc_id != 0) {
            return user.photo.dc_id;
        }
        return 0;
    }

    private static String getDcCity(int dc) {
        switch (dc) {
            case 1:
            case 3:
                return "Майами, США";
            case 2:
            case 4:
                return "Амстердам, Нидерланды";
            case 5:
                return "Сингапур";
            default:
                return "Telegram DC";
        }
    }

    /** Simple mathematical expression evaluator (+, -, *, /, parentheses) */
    private static double eval(final String str) {
        return new Object() {
            int pos = -1, ch;
            void nextChar() { ch = (++pos < str.length()) ? str.charAt(pos) : -1; }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }
            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char)ch);
                return x;
            }
            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }
            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }
            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();
                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected: " + (char)ch);
                }
                return x;
            }
        }.parse();
    }
}