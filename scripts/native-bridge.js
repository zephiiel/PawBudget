import { Directory, Filesystem } from "@capacitor/filesystem";
import { Share } from "@capacitor/share";

window.ledgerNative = {
  async exportBackup(fileName, contents) {
    const file = await Filesystem.writeFile({
      path: fileName,
      data: contents,
      directory: Directory.Cache,
      mimeType: "application/json",
    });
    await Share.share({
      title: "PawBudget backup",
      text: "Save or share your PawBudget backup file.",
      files: [file.uri],
      dialogTitle: "Export backup",
    });
  },
};
