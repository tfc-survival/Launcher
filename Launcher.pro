-injars 'Launcher.jar'
-outjars 'Launcher-obf.jar'
-libraryjars 'build/libraries/jna-5.13.0.jar'
-libraryjars 'build/libraries/jna-platform-5.13.0.jar'
-libraryjars 'build/libraries/oshi-core-6.4.2-20230427.025644-18.jar'
-libraryjars 'build/libraries/slf4j-api-2.0.7.jar'
-libraryjars 'build/libraries/gson-2.8.0.jar'
-libraryjars 'build/libraries/guava-17.0.jar'
-libraryjars 'build/libraries/jansi-1.18.jar'
-libraryjars '<java.home>/lib/rt.jar'
-libraryjars '<java.home>/lib/jce.jar'
-libraryjars '<java.home>/lib/ext/jfxrt.jar'
-libraryjars '<java.home>/lib/ext/nashorn.jar'

-printmapping 'build/mapping.pro'
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute Source

-dontnote
-dontwarn
-dontshrink
-dontoptimize
-ignorewarnings
-target 8
-forceprocessing

-obfuscationdictionary 'build/dictionary.pro'
-classobfuscationdictionary 'build/dictionary.pro'
-overloadaggressively
-repackageclasses 'launcher'
-keepattributes SourceFile,LineNumberTable,*Annotation*
-renamesourcefileattribute SourceFile
-adaptresourcefilecontents META-INF/MANIFEST.MF

-keeppackagenames com.eclipsesource.json.**,com.mojang.**,oshi.**,com.sun.jna.**

-keep class com.eclipsesource.json.**,com.mojang.**,oshi.**,com.sun.jna.** {
    <fields>;
    <methods>;
}

-keepclassmembers @launcher.LauncherAPI class ** {
    <fields>;
    <methods>;
}

-keepclassmembers class ** {
    @launcher.LauncherAPI
    <fields>;
    @launcher.LauncherAPI
    <methods>;
}

-keepclassmembers public class ** {
    public static void main(java.lang.String[]);
}

-keepclassmembers enum ** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep public class com.sun.jna.** {
  *;
}
-keep public class oshi.** {
  *;
}
-keep public class oshi.hardware.platform.windows.** {
  *;
}
-keep public class oshi.util.** {
  *;
}
-keep public class org.slf4j.** {
  *;
}