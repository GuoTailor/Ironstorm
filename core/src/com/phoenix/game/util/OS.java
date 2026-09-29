package com.phoenix.game.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 平台探测与系统工具。参照 {@code arc.util.OS} 移植。
 * <p>原版靠 {@code OS.isAndroid/isIos/...} 做平台分支，靠 {@code getAppDataDirectoryString}
 * 定位 mod/存档/截图目录；本项目用 libgdx 的 {@code Application} 类型 + {@code SharedLibraryLoader} 推断。
 */
public class OS{

    public static final boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
    public static final boolean isLinux = System.getProperty("os.name", "").toLowerCase().contains("linux");
    public static final boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
    public static final boolean isIos = System.getProperty("os.name", "").toLowerCase().contains("ios")
            || System.getProperty("os.name", "").toLowerCase().contains("darwin");
    public static final boolean isAndroid = System.getProperty("java.runtime.name", "").toLowerCase().contains("android")
            || System.getProperty("java.vm.name", "").toLowerCase().contains("dalvik");
    public static final boolean isARM = System.getProperty("os.arch", "").toLowerCase().startsWith("arm")
            || System.getProperty("os.arch", "").toLowerCase().startsWith("aarch");
    public static final boolean is64Bit = System.getProperty("sun.arch.data.model", "64").contains("64")
            || System.getProperty("os.arch", "").contains("64");

    private OS(){
    }

    /** @return 应用数据目录（带结尾分隔符），对应原版 OS.getAppDataDirectoryString(appname)。 */
    public static String getAppDataDirectoryString(String appname){
        String dir = getAppDataDirectoryRaw(appname);
        return dir.endsWith("/") || dir.endsWith("\\") ? dir : dir + "/";
    }

    /** @return 应用数据目录，对应原版 OS.getAppDataDirectory(appname)。 */
    public static Fi getAppDataDirectory(String appname){
        return new Fi(getAppDataDirectoryRaw(appname));
    }

    private static String getAppDataDirectoryRaw(String appname){
        if(isWindows){
            String dir = env("APPDATA");
            if(dir == null) dir = prop("user.home");
            return dir + "\\" + appname + "\\";
        }else if(isMac){
            return prop("user.home") + "/Library/Application Support/" + appname + "/";
        }else{
            return prop("user.home") + "/." + appname.toLowerCase() + "/";
        }
    }

    /** 执行一条命令，返回标准输出（对应原版 OS.exec）。 */
    public static String exec(String... command){
        try{
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder result = new StringBuilder();
            try(BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))){
                String line;
                while((line = reader.readLine()) != null){
                    result.append(line).append('\n');
                }
            }
            process.waitFor();
            return result.toString();
        }catch(Exception e){
            return "";
        }
    }

    /** 执行一条命令，成功返回 true（对应原版 OS.execSafe）。 */
    public static boolean execSafe(String command){
        try{
            if(isWindows){
                return new ProcessBuilder("cmd", "/c", command).start().waitFor() == 0;
            }
            return new ProcessBuilder("sh", "-c", command).start().waitFor() == 0;
        }catch(Exception e){
            return false;
        }
    }

    public static String prop(String name){
        return System.getProperty(name);
    }

    public static String env(String name){
        return System.getenv(name);
    }

    /** 同 {@link #prop(String)}，但为空时返回空串而不是 null（对应原版 propNoNull）。 */
    public static String propNoNull(String name){
        String value = System.getProperty(name);
        return value == null ? "" : value;
    }
}
