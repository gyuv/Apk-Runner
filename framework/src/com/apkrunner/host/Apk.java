package com.apkrunner.host;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only access to the running app's APK (resources, assets). */
public final class Apk {
    private static ZipFile zip;

    public static void open(String path) throws IOException { zip = new ZipFile(path); }

    public static boolean isOpen() { return zip != null; }

    public static byte[] read(String name) {
        if (zip == null) return null;
        try {
            ZipEntry e = zip.getEntry(name);
            if (e == null) return null;
            try (InputStream in = zip.getInputStream(e)) { return in.readAllBytes(); }
        } catch (IOException ex) {
            return null;
        }
    }
}
