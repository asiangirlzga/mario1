package com.blockhero.tv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Optional extra assets downloaded from the internet on first launch and cached in the app's
 * private storage. If a file is missing or the download fails, the built-in procedural
 * graphics/sounds are used, so the game always works offline.
 *
 * >>> Set BASE to the https folder where you host the files below. <<<
 */
final class Assets {
    static final String BASE = "https://YOUR-HOST/blockhero/";   // must end with '/'

    static final String TITLE_IMAGE = "title.jpg";   // title-screen picture (jpg/png)
    static final String MUSIC = "music.mp3";          // looping background music (mp3/ogg)
    static final String[] FX = {"jump.ogg", "coin.ogg", "stomp.ogg", "hurt.ogg", "win.ogg"}; // same order as Sfx ids

    private static final long MAX_BYTES = 6L * 1024 * 1024;   // per-file safety limit

    static boolean configured() { return !BASE.contains("YOUR-HOST"); }

    static File dir(android.content.Context c) {
        File d = new File(c.getFilesDir(), "dl");
        d.mkdirs();
        return d;
    }

    /** Blocking: call from a background thread. Downloads only files not cached yet. */
    static void fetchMissing(File dir) {
        if (!configured()) return;
        String[] names = new String[FX.length + 2];
        names[0] = TITLE_IMAGE; names[1] = MUSIC;
        System.arraycopy(FX, 0, names, 2, FX.length);
        for (String n : names) {
            File f = new File(dir, n);
            if (f.exists()) continue;
            File part = new File(dir, n + ".part");
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(BASE + n).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() != 200) continue;
                InputStream in = c.getInputStream();
                OutputStream out = new FileOutputStream(part);
                byte[] buf = new byte[8192];
                long total = 0;
                int r;
                boolean ok = true;
                while ((r = in.read(buf)) > 0) {
                    total += r;
                    if (total > MAX_BYTES) { ok = false; break; }
                    out.write(buf, 0, r);
                }
                out.close();
                in.close();
                if (!ok || !part.renameTo(f)) part.delete();
            } catch (Exception e) {
                part.delete();
            } finally {
                if (c != null) c.disconnect();
            }
        }
    }
}
