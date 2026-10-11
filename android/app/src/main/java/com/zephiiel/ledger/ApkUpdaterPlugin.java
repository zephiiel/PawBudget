package com.zephiiel.ledger;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "ApkUpdater")
public class ApkUpdaterPlugin extends Plugin {
    private static final int MAX_APK_SIZE = 150 * 1024 * 1024;
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();

    @PluginMethod
    public void getAppVersion(PluginCall call) {
        try {
            PackageInfo packageInfo = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            JSObject result = new JSObject();
            result.put("versionName", packageInfo.versionName);
            call.resolve(result);
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            call.reject("Could not determine the installed app version.", error);
        }
    }

    @PluginMethod
    public void installApk(PluginCall call) {
        String urlValue = call.getString("url");
        if (!isReleaseAssetUrl(urlValue)) {
            call.reject("The APK URL is not a PawBudget GitHub release asset.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getContext().getPackageManager().canRequestPackageInstalls()) {
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            settingsIntent.setData(Uri.parse("package:" + getContext().getPackageName()));
            getActivity().startActivity(settingsIntent);
            call.reject("Allow PawBudget to install apps in Android settings, then try again.", "INSTALL_PERMISSION_REQUIRED");
            return;
        }

        DOWNLOAD_EXECUTOR.execute(() -> downloadAndInstall(call, urlValue));
    }

    private boolean isReleaseAssetUrl(String urlValue) {
        if (urlValue == null) return false;
        Uri uri = Uri.parse(urlValue);
        String path = uri.getPath();
        return "https".equals(uri.getScheme())
            && "github.com".equals(uri.getHost())
            && path != null
            && path.startsWith("/zephiiel/PawBudget/releases/download/");
    }

    private void downloadAndInstall(PluginCall call, String urlValue) {
        File apkFile = new File(getContext().getCacheDir(), "updates/pawbudget-update.apk");
        try {
            downloadApk(urlValue, apkFile);
            try (FileInputStream input = new FileInputStream(apkFile)) {
                if (input.read() != 0x50 || input.read() != 0x4b || input.read() != 0x03 || input.read() != 0x04) {
                    throw new IOException("The downloaded file is not a valid APK archive.");
                }
            }
            Uri apkUri = FileProvider.getUriForFile(
                getContext(),
                getContext().getPackageName() + ".fileprovider",
                apkFile
            );
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().runOnUiThread(() -> {
                try {
                    getActivity().startActivity(installIntent);
                    call.resolve();
                } catch (ActivityNotFoundException | SecurityException error) {
                    apkFile.delete();
                    call.reject("Could not open the Android package installer.", error);
                }
            });
        } catch (IOException error) {
            apkFile.delete();
            call.reject("Could not download the APK update. Check your connection and try again.", error);
        }
    }

    private void downloadApk(String urlValue, File apkFile) throws IOException {
        File updateDirectory = apkFile.getParentFile();
        if (!updateDirectory.isDirectory() && !updateDirectory.mkdirs()) {
            throw new IOException("Could not create the APK cache directory.");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(urlValue).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub returned HTTP " + responseCode + ".");
            }
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_APK_SIZE) {
                throw new IOException("The APK exceeds the 150 MB download limit.");
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(apkFile)) {
                byte[] buffer = new byte[8192];
                int totalBytes = 0;
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                    if (totalBytes > MAX_APK_SIZE) {
                        throw new IOException("The APK exceeds the 150 MB download limit.");
                    }
                    output.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            connection.disconnect();
        }
    }
}
