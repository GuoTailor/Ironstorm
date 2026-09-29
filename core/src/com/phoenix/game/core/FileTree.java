package com.phoenix.game.core;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.ObjectMap;
import com.phoenix.game.util.Fi;

/**
 * 处理 mod 上下文中的文件：mod 文件优先，找不到时回退到游戏内资源。
 * 参照 Mindustry {@code mindustry.core.FileTree} 移植。
 *
 * <p><b>与 arc 的差异</b>：原版 {@code FileTree implements FileHandleResolver}（{@code arc.assets.loaders}），
 * 且 arc 的 {@code Fi} 本身就能当资源句柄用。本项目 {@link Fi} **不继承** libgdx 的 {@link FileHandle}
 * （见 {@code util/Fi} 的类注释：{@code list()} 的返回类型无法协变），
 * 所以这里实现的是 libgdx 的 {@link FileHandleResolver}，并在 {@link #resolve(String)} 里
 * 用 {@link Fi#handle()} 交出底层句柄 —— 语义与原版一致，且能直接喂给 {@code AssetManager}。
 *
 * <p>接线状态（F5）：{@code Vars.tree} 实例已在启动器里建好（客户端/headless 各一处，早于
 * {@code Vars.init()}）。但 mod 内容加载本身尚未接线 —— {@code mod/ContentParser} 里的
 * {@code Vars.tree.get(...)} 仍是注释状态，所以本实例目前只保证"字段存在、可用"。
 */
public class FileTree implements FileHandleResolver{
    private final ObjectMap<String, Fi> files = new ObjectMap<>();

    public void addFile(String path, Fi f){
        files.put(path, f);
    }

    /** Gets an asset file. */
    public Fi get(String path){
        if(files.containsKey(path)){
            return files.get(path);
        }else if(files.containsKey("/" + path)){
            return files.get("/" + path);
        }else{
            return new Fi(Core.files.internal(path));
        }
    }

    /** Clears all mod files. */
    public void clear(){
        files.clear();
    }

    @Override
    public FileHandle resolve(String fileName){
        return get(fileName).handle();
    }
}
