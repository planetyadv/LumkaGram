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
import java.util.Map;

/**
 * Temporary diagnostic helper.
 *
 * Writes uncaught exception reports to Download/LumkaGram-crash.txt and, if the app is stuck on
 * startup for too long, dumps all thread stacks to Download/LumkaGram-hang.txt.
 * Both files are reachable with any file manager, so no adb is needed.
 */
public class LumkaCrashHandler {

    public static final String CRASH_FILE = "LumkaGram-crash.txt";
    public static final String HANG_FILE = "LumkaGram-hang.txt";

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
                final String report = buildCrashReport(thread, throwable);
                final Thread writer = new Thread(() -> {
                    try {
                        save(appContext, CRASH_FILE, report);
                    } catch (Throwable ignore) {
                    }
                });
                writer.start();
                writer.join(2500);
            } catch (Throwable ignore) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                // Never leave the process alive with a dead main thread: that looks like a frozen splash.
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    /**
     * Dumps every thread stack a few seconds after startup, so a frozen splash screen can be
     * diagnosed without adb. Runs unconditionally: this build exists only for diagnostics.
     */
    public static void startHangWatchdog(final Context context) {
        final Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        final Thread watchdog = new Thread(() -> {
            final long[] delays = {20000L, 25000L};
            for (int i = 0; i < delays.length; i++) {
                try {
                    Thread.sleep(delays[i]);
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    save(appContext, i == 0 ? HANG_FILE : "LumkaGram-hang-late.txt", buildHangReport());
                } catch (Throwable ignore) {
                }
            }
        });
        watchdog.setDaemon(true);
        watchdog.setName("lumka-hang-watchdog");
        watchdog.start();
    }

    private static String header(String title) {
        final StringBuilder sb = new StringBuilder();
        sb.append(title).append('\n');
        sb.append("time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("android: ").append(Build.VERSION.RELEASE).append(" (api ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("abi: ").append(Build.CPU_ABI);
        if (Build.CPU_ABI2 != null && !Build.CPU_ABI2.isEmpty()) {
            sb.append(", ").append(Build.CPU_ABI2);
        }
        sb.append('\n');
        try {
            final Context ctx = ApplicationLoader.applicationContext;
            if (ctx != null) {
                sb.append("package: ").append(ctx.getPackageName()).append('\n');
                sb.append("version: ").append(ctx.getPackageManager()
                        .getPackageInfo(ctx.getPackageName(), 0).versionName).append('\n');
            }
        } catch (Throwable ignore) {
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String buildCrashReport(Thread thread, Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        pw.print(header("LumkaGram crash report"));
        pw.println("thread: " + (thread == null ? "?" : thread.getName()));
        pw.println();
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static String buildHangReport() {
        final StringBuilder sb = new StringBuilder(header("LumkaGram thread dump (app is stuck on the splash screen)"));
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            final Thread thread = entry.getKey();
            sb.append("\"").append(thread.getName()).append("\" state=").append(thread.getState()).append('\n');
            for (StackTraceElement element : entry.getValue()) {
                sb.append("    at ").append(element).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void save(Context context, String fileName, String text) {
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
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
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
                    writeFile(new File(dir, fileName), data);
                }
            } catch (Throwable ignore) {
            }
        }

        // 2. App external files dir as a backup copy.
        try {
            final File ext = context.getExternalFilesDir(null);
            if (ext != null) {
                writeFile(new File(ext, fileName), data);
            }
        } catch (Throwable ignore) {
        }

        // 3. Internal storage, always writable.
        try {
            writeFile(new File(context.getFilesDir(), fileName), data);
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
