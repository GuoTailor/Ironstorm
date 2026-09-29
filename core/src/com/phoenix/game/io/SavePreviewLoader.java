package com.phoenix.game.io;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;

/**
 * 存档缩略图的异步加载器。参照 Mindustry {@code mindustry.io.SavePreviewLoader} 移植。
 *
 * <p>存档旁边会有一张同名（去掉扩展名）的预览图，本加载器就是把它当纹理加载进来：
 * 请求的是 {@code saves/0.msav}，实际读的是同目录的 {@code 0.png}
 * （原版就是靠 {@code file.sibling(file.nameWithoutExtension())} 做这个转换的）。
 *
 * <p><b>与原版的差异 + 刻意降级</b>
 * <ul>
 *   <li>原版用 {@code arc.assets.*} / {@code arc.files.Fi}；本项目用 libgdx 的
 *       {@code com.badlogic.gdx.assets.*} / {@code com.badlogic.gdx.files.FileHandle}。
 *       两边 API 逐字对应（{@code TextureLoader} / {@code TextureParameter} /
 *       {@code AbsoluteFileHandleResolver} 同名同签名），所以本类**没有语义降级**，
 *       只是把 {@code Fi} 换成 {@code FileHandle}。这与已移植的
 *       {@code maps/MapPreviewLoader} 是同一套写法。</li>
 *   <li>{@code Core.assets} 在本项目**是存在的**（{@code ClientLauncher} 里 {@code new AssetManager()}），
 *       所以"依赖 Core.assets"这点不构成阻塞。</li>
 *   <li>TODO 本类尚未注册进 {@code Core.assets}（与 {@code MapPreviewLoader} 一样属"只新增文件"）：
 *       需要等存档列表 UI 真正要用缩略图时，在启动器里
 *       {@code Core.assets.setLoader(Texture.class, ".msav", new SavePreviewLoader())}。
 *       目前缩略图的生成走 {@link SavePreview}（在游戏进程内用 FBO 渲染后写成 {@code <存档>.png}），
 *       与"加载"这一半是分开的。</li>
 * </ul>
 */
public class SavePreviewLoader extends TextureLoader{

    public SavePreviewLoader(){
        super(new AbsoluteFileHandleResolver());
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, TextureParameter parameter){
        try{
            super.loadAsync(manager, fileName, file.sibling(file.nameWithoutExtension()), parameter);
        }catch(Exception e){
            e.printStackTrace();
            //预览图坏了就直接删掉，下次进存档列表时会重新生成
            file.sibling(file.nameWithoutExtension()).delete();
        }
    }
}
