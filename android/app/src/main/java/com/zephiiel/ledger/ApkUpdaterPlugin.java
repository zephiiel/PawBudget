package com.zephiiel.ledger;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;

import com.getcapacitor.annotation.ActivityCallback;
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "ApkUpdater")
public class ApkUpdaterPlugin extends Plugin {
    private static final int MAX_APK_SIZE = 150 * 1024 * 1024;
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();

    @PluginMethod
    public void exportBackup(PluginCall call) {
        String fileName = call.getString("fileName");
        String contents = call.getString("contents");
        if (fileName == null || fileName.isEmpty() || contents == null) {
            call.reject("A file name and backup contents are required.");
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        call.getData().put("contents", contents);
        startActivityForResult(call, intent, "backupExportResult");
    }

    @ActivityCallback
    private void backupExportResult(PluginCall call, ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_CANCELED) {
            JSObject response = new JSObject();
            response.put("cancelled", true);
            call.resolve(response);
            return;
        }

        Intent intent = result.getData();
        Uri uri = intent == null ? null : intent.getData();
        String contents = call.getData().getString("contents");
        if (uri == null || contents == null) {
            call.reject("Android did not return a file location for the backup.");
            return;
        }

        try (OutputStream output = getContext().getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IOException("Could not open the selected backup file.");
            }
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            JSObject response = new JSObject();
            response.put("cancelled", false);
            call.resolve(response);
        } catch (IOException | SecurityException error) {
            call.reject("Could not save the backup to the selected location.", error);
        }
    }

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
            downloadApk(urlValue, apkFile, call);
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

    private void downloadApk(String urlValue, File apkFile, PluginCall call) throws IOException {
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
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_APK_SIZE) {
                throw new IOException("The APK exceeds the 150 MB download limit.");
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(apkFile)) {
                byte[] buffer = new byte[8192];
                long totalBytes = 0;
                long lastReportedBytes = 0;
                int bytesRead;
                notifyDownloadProgress(call, totalBytes, contentLength);
                while ((bytesRead = input.read(buffer)) != -1) {
                    totalBytes += bytesRead;
                    if (totalBytes > MAX_APK_SIZE) {
                        throw new IOException("The APK exceeds the 150 MB download limit.");
                    }
                    output.write(buffer, 0, bytesRead);
                    if (totalBytes - lastReportedBytes >= 256 * 1024
                        || (contentLength > 0 && totalBytes >= contentLength)) {
                        notifyDownloadProgress(call, totalBytes, contentLength);
                        lastReportedBytes = totalBytes;
                    }
                }
                notifyDownloadProgress(call, totalBytes, contentLength);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void notifyDownloadProgress(PluginCall call, long downloadedBytes, long totalBytes) {
        JSObject progress = new JSObject();
        progress.put("downloadedBytes", downloadedBytes);
        progress.put("totalBytes", totalBytes);
        progress.put("percent", totalBytes > 0 ? Math.min(100, (int) (downloadedBytes * 100 / totalBytes)) : -1);
        notifyListeners("downloadProgress", progress);
    }
}
