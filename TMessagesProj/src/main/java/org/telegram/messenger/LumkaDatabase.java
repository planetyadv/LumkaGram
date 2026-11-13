package org.telegram.messenger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class LumkaDatabase extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "lumka_data.db";
    private static final int DATABASE_VERSION = 1;

    private static volatile LumkaDatabase instance;

    public static LumkaDatabase getInstance() {
        if (instance == null) {
            synchronized (LumkaDatabase.class) {
                if (instance == null) {
                    Context ctx = ApplicationLoader.applicationContext;
                    if (ctx != null) {
                        instance = new LumkaDatabase(ctx);
                    }
                }
            }
        }
        return instance;
    }

    private LumkaDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS lumka_deleted_messages (" +
                "mid INTEGER, " +
                "did INTEGER, " +
                "PRIMARY KEY(mid, did));");

        db.execSQL("CREATE TABLE IF NOT EXISTS lumka_edited_messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "mid INTEGER, " +
                "did INTEGER, " +
                "date INTEGER, " +
                "text TEXT);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void markMessageDeleted(long did, int mid) {
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("mid", mid);
            cv.put("did", did);
            db.insertWithOnConflict("lumka_deleted_messages", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean isMessageDeleted(long did, int mid) {
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT 1 FROM lumka_deleted_messages WHERE mid = ? AND did = ? LIMIT 1",
                    new String[]{String.valueOf(mid), String.valueOf(did)});
            boolean exists = (c != null && c.moveToFirst());
            if (c != null) {
                c.close();
            }
            return exists;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public void saveMessageEdit(long did, int mid, int date, String text) {
        if (text == null || text.isEmpty()) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("mid", mid);
            cv.put("did", did);
            cv.put("date", date);
            cv.put("text", text);
            db.insert("lumka_edited_messages", null, cv);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public ArrayList<String> getEditHistory(long did, int mid) {
        ArrayList<String> history = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT text FROM lumka_edited_messages WHERE mid = ? AND did = ? ORDER BY id ASC",
                    new String[]{String.valueOf(mid), String.valueOf(did)});
            if (c != null) {
                while (c.moveToNext()) {
                    history.add(c.getString(0));
                }
                c.close();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return history;
    }

    public boolean hasEditHistory(long did, int mid) {
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT 1 FROM lumka_edited_messages WHERE mid = ? AND did = ? LIMIT 1",
                    new String[]{String.valueOf(mid), String.valueOf(did)});
            boolean has = (c != null && c.moveToFirst());
            if (c != null) {
                c.close();
            }
            return has;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static class SavedMessage {
        public long did;
        public int mid;
        public int date;
        public String text;
        public boolean deleted;
    }

    public ArrayList<SavedMessage> getDeletedMessages(int limit) {
        ArrayList<SavedMessage> result = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT did, mid FROM lumka_deleted_messages ORDER BY rowid DESC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            if (c != null) {
                while (c.moveToNext()) {
                    SavedMessage m = new SavedMessage();
                    m.did = c.getLong(0);
                    m.mid = c.getInt(1);
                    m.deleted = true;
                    result.add(m);
                }
                c.close();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public ArrayList<SavedMessage> getEditedMessages(int limit) {
        ArrayList<SavedMessage> result = new ArrayList<>();
        try {
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.rawQuery("SELECT did, mid, date, text FROM lumka_edited_messages ORDER BY id DESC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            if (c != null) {
                while (c.moveToNext()) {
                    SavedMessage m = new SavedMessage();
                    m.did = c.getLong(0);
                    m.mid = c.getInt(1);
                    m.date = c.getInt(2);
                    m.text = c.getString(3);
                    result.add(m);
                }
                c.close();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public void clearDeletedMessages() {
        try {
            getWritableDatabase().delete("lumka_deleted_messages", null, null);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void clearEditedMessages() {
        try {
            getWritableDatabase().delete("lumka_edited_messages", null, null);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
