import { registerPlugin } from "@capacitor/core";

const ApkUpdater = registerPlugin("ApkUpdater");

window.ledgerNative = {
  async exportBackup(fileName, contents) {
    return ApkUpdater.exportBackup({ fileName, contents });
  },
  getAppVersion() {
    return ApkUpdater.getAppVersion();
  },
  addDownloadProgressListener(listener) {
    return ApkUpdater.addListener("downloadProgress", listener);
  },
  installApk(url) {
    return ApkUpdater.installApk({ url });
  },
};
