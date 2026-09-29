package com.phoenix.game.maps;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.ctype.Content;
import com.phoenix.game.util.Log;

/**
 * 地图预览纹理的异步加载器。参照 Mindustry mindustry.maps.MapPreviewLoader 移植。
 *
 * <p><b>好消息</b>：arc 的 {@code arc.assets.*} 本来就是从 libgdx 的 {@code com.badlogic.gdx.assets.*}
 * 抄过去的，两边 API 几乎逐字对应（{@code TextureLoader} / {@code TextureParameter} /
 * {@code AssetDescriptor} / {@code AbsoluteFileHandleResolver} 同名同签名），所以本类**不需要降级**，
 * 只把 {@code Fi} 换成 {@code FileHandle}、{@code arc.struct.Array} 换成 libgdx {@code Array}。
 *
 * <p><b>一处降级（TODO）</b>
 * <ul>
 *   <li>预览加载失败时原版调 {@code Vars.maps.queueNewPreview(map)} 去**同步生成**一张缩略图；
 *       本项目没有地图缩略图生成（{@code MapIO.generatePreview} 未移植）→ {@link Maps#queueNewPreview}
 *       只打日志。</li>
 *   <li>彻底失败时原版返回 {@code new Texture("sprites/error.png")}；本项目改用图集里的 {@code error} 区域
 *       （已存在），缺失时退回 {@code nomap} 区域的纹理（可能为 null）。</li>
 * </ul>
 *
 * <p><b>接线状态</b>：本类尚未注册进 {@code Core.assets}（与 W1 的 {@code RespawnBlock} 一样属"只新增文件"），
 * 等地图预览生成落地后由 {@code Maps.loadPreviews()} 注册并驱动。
 */
public class MapPreviewLoader extends TextureLoader{

    public MapPreviewLoader(){
        super(new AbsoluteFileHandleResolver());
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, TextureParameter parameter){
        try{
            super.loadAsync(manager, fileName, file.sibling(file.nameWithoutExtension()), parameter);
        }catch(Exception e){
            Log.err(e);
            MapPreviewParameter param = (MapPreviewParameter)parameter;
            //TODO 本项目 Maps.queueNewPreview 是降级桩（没有缩略图生成）
            if(Vars.maps != null){
                Vars.maps.queueNewPreview(param.map);
            }
        }
    }

    @Override
    public Texture loadSync(AssetManager manager, String fileName, FileHandle file, TextureParameter parameter){
        try{
            return super.loadSync(manager, fileName, file, parameter);
        }catch(Throwable e){
            Log.err(e);
            try{
                return new Texture(file);
            }catch(Throwable e2){
                Log.err(e2);
                //原版返回 new Texture("sprites/error.png")；本项目图集里已有 error 区域，缺失时退回 nomap
                TextureRegion region = Core.atlas == null ? null : Core.atlas.findRegion("error");
                if(region == null && Core.atlas != null) region = Core.atlas.findRegion("nomap");
                return region == null ? null : region.getTexture();
            }
        }
    }

    @Override
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, TextureParameter parameter){
        //原版依赖 "contentcreate"（Content 全部加载完）—— 本项目由 Vars.content.load() 保证。
        //注意用显式构造 + add，避免 Array.with(泛型 varargs) 在「返回裸 Array<AssetDescriptor>」时推断失败
        Array<AssetDescriptor> out = new Array<>();
        out.add(new AssetDescriptor<Content>("contentcreate", Content.class));
        return out;
    }

    public static class MapPreviewParameter extends TextureParameter{
        public Map map;

        public MapPreviewParameter(Map map){
            this.map = map;
        }
    }
}
