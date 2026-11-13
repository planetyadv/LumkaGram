package org.telegram.messenger;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Temporary diagnostic helper: writes an uncaught exception report to a file the user can
 * actually reach (Download/LumkaGram-crash.txt) so a crash can be reported without adb.
 */
public class LumkaCrashHandler {

    public static final String FILE_NAME = "LumkaGram-crash.txt";

    private static volatile boolean installed;

    public static void install(final Context context) {
        if (context == null || installed) {
            return;
        }
        installed = true;
        final Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                save(appContext, buildReport(thread, throwable));
            } catch (Throwable ignore) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    private static String buildReport(Thread thread, Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        pw.println("LumkaGram crash report");
        pw.println("time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("thread: " + (thread == null ? "?" : thread.getName()));
        pw.println("device: " + Build.MANUFACTURER + " " + Build.MODEL);
        pw.println("android: " + Build.VERSION.RELEASE + " (api " + Build.VERSION.SDK_INT + ")");
        pw.println("abi: " + Build.CPU_ABI + (Build.CPU_ABI2 == null || Build.CPU_ABI2.isEmpty() ? "" : ", " + Build.CPU_ABI2));
        try {
            pw.println("package: " + ApplicationLoader.applicationContext.getPackageName());
            pw.println("version: " + ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0).versionName);
        } catch (Throwable ignore) {
        }
        pw.println();
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static void save(Context context, String text) {
        final byte[] data;
        try {
            data = text.getBytes("UTF-8");
        } catch (Throwable e) {
            return;
        }

        // 1. Public Download folder - visible in any file manager / Downloads app.
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                final ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                final Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    final OutputStream out = context.getContentResolver().openOutputStream(uri, "wt");
                    if (out != null) {
                        out.write(data);
                        out.close();
                    }
                }
            } catch (Throwable ignore) {
            }
        } else {
            try {
                final File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (dir != null && (dir.exists() || dir.mkdirs())) {
                    writeFile(new File(dir, FILE_NAME), data);
                }
            } catch (Throwable ignore) {
            }
        }

        // 2. App external files dir as a backup copy.
        try {
            final File ext = context.getExternalFilesDir(null);
            if (ext != null) {
                writeFile(new File(ext, FILE_NAME), data);
            }
        } catch (Throwable ignore) {
        }

        // 3. Internal storage, always writable.
        try {
            writeFile(new File(context.getFilesDir(), FILE_NAME), data);
        } catch (Throwable ignore) {
        }
    }

    private static void writeFile(File file, byte[] data) {
        try {
            final FileOutputStream out = new FileOutputStream(file);
            out.write(data);
            out.close();
        } catch (Throwable ignore) {
        }
    }
}
