// ====== LAUNCHER CONFIG ====== //
var config = {
    dir: "tfc-survival2", // Launcher directory
    title: "TFC-survival launcher", // Window title
    icons: [ "favicon.png" ], // Window icon paths

    // Auth config
    newsURL: "https://tfc-survival.ru/news?banner=true", // News WebView URL
    linkText: "Наш сайт", // Text for link under "Auth" button
    linkURL: new java.net.URL("https://tfc-survival.ru"), // URL for link under "Auth" button

    // Settings defaults
    settingsMagic: 0xC0DE5, // Ancient magic, don't touch
    autoEnterDefault: false, // Should autoEnter be enabled by default?
    fullScreenDefault: false, // Should fullScreen be enabled by default?
    ramDefault: 5120 // Default RAM amount (0 for auto)
};

// ====== DON'T TOUCH! ====== //
var dir = IOHelper.HOME_DIR.resolve(config.dir);
if (JVMHelper.OS_TYPE == JVMHelperOS.MUSTDIE)
{
    dir = IOHelper.HOME_DIR_WIN.resolve(config.dir);
}
if (!IOHelper.isDir(dir)) {
    java.nio.file.Files.createDirectory(dir);
}
var defaultUpdatesDir = dir.resolve("updates");
if (!IOHelper.isDir(defaultUpdatesDir)) {
    java.nio.file.Files.createDirectory(defaultUpdatesDir);
}
