package com.phoenix.game.maps;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.io.SaveIO;

import java.io.File;
import java.io.IOException;

/**
 * 地图管理。对应原版 Mindustry 的 mindustry.maps.Maps 最小版（headless 友好）。
 * <p>phoenix 无独立地图格式：一张地图 = 一个 SaveIO 序列化的世界存档（{@code .msav}）。
 * 程序生成的默认图在启动时「导出」到 maps 目录，随后扫描目录作为可玩地图列表。
 * <p>缩略图、steam workshop、地图过滤、预览等在客户端侧，服务端只关心名字与加载。
 */
public class Maps {
    /** 地图目录（应用本地目录下）。 */
    private static final String MAPS_DIR = "maps";
    /** 全部地图（含自定义），按名字排序。 */
    private final Array<Map> maps = new Array<>();

    /** 扫描地图目录，恢复地图列表。应用启动后调用一次。 */
    public void load(){
        maps.clear();
        File dir = Gdx.files.local(MAPS_DIR).file();
        if(!dir.exists() && !dir.mkdirs()){
            System.err.println("无法创建地图目录: " + dir);
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith("." + com.phoenix.game.Vars.saveExtension));
        if(files == null) return;

        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for(File file : files){
            maps.add(new Map(file));
        }
    }

    /** 全部地图。 */
    public Array<Map> all(){
        return maps;
    }

    /** 按名字查找地图。 */
    public Map byName(String name){
        for(Map map : maps){
            if(map.name.equals(name)) return map;
        }
        return null;
    }

    /** 把当前世界导出成一张新地图（maps/map_N.msav），并加入列表。 */
    public Map exportCurrent(File file) throws IOException{
        SaveIO.save(file);
        Map map = new Map(file);
        if(byName(map.name) == null){
            maps.add(map);
        }
        return map;
    }

    /** 生成一个不冲突的地图文件。 */
    public File nextMapFile(){
        File dir = Gdx.files.local(MAPS_DIR).file();
        if(!dir.exists() && !dir.mkdirs()){
            throw new IllegalStateException("无法创建地图目录: " + dir);
        }
        int i = 0;
        File file;
        do{
            file = new File(dir, "map_" + i + "." + com.phoenix.game.Vars.saveExtension);
            i++;
        }while(file.exists());
        return file;
    }

    /** 从指定文件加载地图到当前世界（Vars.world 被替换）。 */
    public void loadMap(Map map){
        try{
            SaveIO.load(map.file);
            System.out.println("已加载地图 " + map.name);
        }catch(IOException e){
            System.err.println("加载地图失败 " + map.name + ": " + e);
        }
    }

    /** 一张地图。 */
    public static class Map {
        /** 文件。 */
        public final File file;
        /** 显示名（去扩展名的文件名）。 */
        public final String name;

        Map(File file){
            this.file = file;
            String n = file.getName();
            this.name = n.substring(0, n.lastIndexOf('.'));
        }

        @Override
        public String toString(){
            return name;
        }
    }
}