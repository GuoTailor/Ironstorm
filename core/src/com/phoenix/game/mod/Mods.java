package com.phoenix.game.mod;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.function.Consumer;

/**
 * 模组/插件加载器。对应原版 Mindustry 的 mindustry.mod.Mods（headless 精简版）。
 * <p>扫描 {@code mods/} 目录下的 {@code .jar}/ {@code .zip}，读取入口描述文件
 * （{@code mod.json}/ {@code plugin.json}）里的 {@code main} 类名，用独立 URLClassLoader 加载并实例化。
 * 实例若是 {@link Plugin} 则标记隐藏（不加入"普通模组"）。脚本/动态内容不在本阶段范围。
 */
public class Mods {
    /** 模组目录（应用本地目录下）。 */
    private static final String MODS_DIR = "mods";
    /** 已加载的模组/插件。 */
    private final Array<LoadedMod> mods = new Array<>();
    /** 是否有内容加载错误（脚本/动态内容未实现，恒 false）。 */
    private boolean hasContentErrors;

    /** 应用启动后调用：扫描并加载全部模组/插件。 */
    public void load(){
        mods.clear();
        File dir = Gdx.files.local(MODS_DIR).file();
        if(!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".jar") || name.endsWith(".zip"));
        if(files == null) return;

        for(File file : files){
            try{
                loadMod(file);
            }catch(Exception e){
                System.err.println("加载模组失败 " + file + ": " + e);
            }
        }
    }

    /** 加载单个模组文件（jar/zip）。 */
    public void loadMod(File file) throws Exception {
        String mainClass = findMainClass(file);
        if(mainClass == null) return;

        URLClassLoader loader = new URLClassLoader(new URL[]{ file.toURI().toURL() }, getClass().getClassLoader());
        Class<?> cls = Class.forName(mainClass, true, loader);
        Object instance = cls.getDeclaredConstructor().newInstance();

        boolean plugin = instance instanceof Plugin;
        LoadedMod mod = new LoadedMod(file, mainClass, (Mod)instance, plugin);
        mods.add(mod);

        System.out.println((plugin ? "已加载插件 " : "已加载模组 ") + file.getName() + " (main=" + mainClass + ")");
    }

    /** 从 jar/zip 内查找入口描述文件的 main 类名。 */
    private String findMainClass(File file) throws Exception {
        try(JarFile jar = new JarFile(file)){
            for(String metaName : new String[]{ "mod.json", "plugin.json", "mod.hjson" }){
                JarEntry entry = jar.getJarEntry(metaName);
                if(entry == null) continue;
                try(InputStream in = jar.getInputStream(entry)){
                    String content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    String main = extractMain(content);
                    if(main != null) return main;
                }
            }
        }
        return null;
    }

    /** 从 JSON 里提取 "main": "..." 的值（免 JSON 库的粗略解析）。 */
    private String extractMain(String json){
        int idx = json.indexOf("\"main\"");
        if(idx < 0) idx = json.indexOf("'main'");
        if(idx < 0) return null;
        int colon = json.indexOf(':', idx);
        if(colon < 0) return null;
        int start = json.indexOf('"', colon);
        if(start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if(end < 0) return null;
        String main = json.substring(start + 1, end).trim();
        return main.isEmpty() ? null : main;
    }

    /** 全部模组/插件。 */
    public Array<LoadedMod> list(){
        return mods;
    }

    /** 全部模组（排除插件）。 */
    public Array<LoadedMod> listMods(){
        Array<LoadedMod> out = new Array<>();
        for(LoadedMod mod : mods){
            if(!mod.hidden) out.add(mod);
        }
        return out;
    }

    /** 遍历每个模组/插件的命令注册入口。 */
    public void eachClass(Consumer<Mod> cons){
        for(LoadedMod mod : mods){
            cons.accept(mod.main);
        }
    }

    public boolean hasContentErrors(){
        return hasContentErrors;
    }

    /** 一个已加载的模组/插件。 */
    public static class LoadedMod {
        public final File file;
        public final String mainClass;
        public final Mod main;
        /** 插件恒隐藏（不当作"普通模组"）。 */
        public final boolean hidden;

        LoadedMod(File file, String mainClass, Mod main, boolean hidden){
            this.file = file;
            this.mainClass = mainClass;
            this.main = main;
            this.hidden = hidden;
        }

        @Override
        public String toString(){
            return file.getName() + " (" + mainClass + ")";
        }
    }
}