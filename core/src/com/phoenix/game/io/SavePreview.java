package com.phoenix.game.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.phoenix.game.Vars;
import com.phoenix.game.core.Core;
import com.phoenix.game.world.Tile;

import java.io.File;

/**
 * 存档缩略图（对应原版 {@code SavePreviewLoader}）。
 * <p>做法：把整张地图按 {@link #size}×{@link #size} 渲染一遍 —— 每格用
 * {@link MapIO#colorFor} 取小地图色，画成一个按比例缩放的色块（等价于原版的小地图式缩略图），
 * 再把像素读回来写成与存档同名的 {@code .png}。
 *
 * <p>与蓝图预览图（{@code Schematics.getBuffer}）一样有两个 GL 坑：
 * <ul>
 *   <li>往 FBO 里画必须自己 {@code batch.begin()/end()}，并保存/恢复调用方的投影、颜色与混合状态；</li>
 *   <li>FBO 读回的像素是自下而上的，写 PNG 前要翻正。</li>
 * </ul>
 */
public class SavePreview{
    /** 缩略图边长（像素）。 */
    public static final int size = 128;

    /** @return 存档文件对应的缩略图文件（{@code saves/0.msav} → {@code saves/0.msav.png}）。 */
    public static File previewFileFor(File saveFile){
        return new File(saveFile.getParentFile(), saveFile.getName() + ".png");
    }

    /** @return 该存档是否已有缩略图。 */
    public static boolean hasPreview(File saveFile){
        File file = previewFileFor(saveFile);
        return file.exists() && file.length() > 0;
    }

    /** 删除该存档的缩略图。 */
    public static void deletePreview(File saveFile){
        File file = previewFileFor(saveFile);
        if(file.exists() && !file.delete()){
            System.err.println("无法删除存档缩略图: " + file);
        }
    }

    /**
     * 渲染当前世界并写成该存档的缩略图。
     * @return 是否成功（headless / GL 未就绪 / 图集缺失时返回 false，不抛异常）
     */
    public static boolean savePreview(File saveFile){
        if(Vars.world == null || Core.batch == null || Core.atlas == null) return false;

        TextureRegion white = Core.atlas.findRegion("white");
        if(white == null) return false;

        int worldWidth = Vars.world.width(), worldHeight = Vars.world.height();
        if(worldWidth <= 0 || worldHeight <= 0) return false;

        SpriteBatch batch = Core.batch;
        Matrix4 oldProj = new Matrix4(batch.getProjectionMatrix());
        Matrix4 oldTrans = new Matrix4(batch.getTransformMatrix());
        Color oldColor = batch.getColor().cpy();
        int oldSrcFunc = batch.getBlendSrcFunc(), oldDstFunc = batch.getBlendDstFunc();
        boolean oldBlending = batch.isBlendingEnabled();
        //调用方可能正处在 batch.begin() 状态（例如在 UI 绘制中途存档），先收尾再自己开一轮
        boolean wasDrawing = batch.isDrawing();
        if(wasDrawing) batch.end();

        FrameBuffer buffer = null;
        Pixmap raw = null, flipped = null;
        Color tmp = new Color();

        try{
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, size, size, false);
            buffer.begin();
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();

            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0f, 0f, size, size));
            batch.setTransformMatrix(new Matrix4().idt());
            batch.enableBlending();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            //一格在缩略图里占多少像素（世界比缩略图大时小于 1，会出现重叠，视觉上等价于降采样）
            float sx = (float)size / worldWidth, sy = (float)size / worldHeight;
            //世界比缩略图大得多时按步长抽样，避免 100×100 的图也要画一万多次
            int stepX = Math.max(1, (int)(worldWidth / (float)size));
            int stepY = Math.max(1, (int)(worldHeight / (float)size));
            float drawW = sx * stepX, drawH = sy * stepY;

            for(int y = 0; y < worldHeight; y += stepY){
                for(int x = 0; x < worldWidth; x += stepX){
                    Tile tile = Vars.world.tile(x, y);
                    if(tile == null) continue;

                    Color.rgba8888ToColor(tmp, MapIO.colorFor(tile.floor(), tile.block(), tile.overlay(), tile.getTeam()));
                    batch.setColor(tmp);
                    //世界 y 向上、正交投影也是 y 向上，所以不用翻转；FBO 读回时再统一翻正
                    batch.draw(white, x * sx, y * sy, drawW, drawH);
                }
            }

            batch.flush();
            batch.end();

            //保持 FBO 绑定状态读回像素
            raw = Pixmap.createFromFrameBuffer(0, 0, size, size);
            buffer.end();

            flipped = flipVertically(raw);

            File parent = saveFile.getParentFile();
            if(parent != null && !parent.exists()) parent.mkdirs();
            PixmapIO.writePNG(Gdx.files.absolute(previewFileFor(saveFile).getAbsolutePath()), flipped);
            return true;
        }catch(Exception e){
            //GL 未就绪（headless）时不要炸掉存档流程，退化成"没有缩略图"
            System.err.println("DBG 存档缩略图生成失败: " + e);
            return false;
        }finally{
            if(raw != null) raw.dispose();
            if(flipped != null) flipped.dispose();
            if(buffer != null) buffer.dispose();

            batch.setProjectionMatrix(oldProj);
            batch.setTransformMatrix(oldTrans);
            batch.setColor(oldColor);
            if(oldBlending){
                batch.enableBlending();
            }else{
                batch.disableBlending();
            }
            batch.setBlendFunction(oldSrcFunc, oldDstFunc);
            if(wasDrawing && !batch.isDrawing()) batch.begin();
        }
    }

    /** @return 上下翻转后的像素图（FBO 读回是自下而上的）。 */
    private static Pixmap flipVertically(Pixmap src){
        int w = src.getWidth(), h = src.getHeight();
        Pixmap dst = new Pixmap(w, h, src.getFormat());

        java.nio.ByteBuffer sb = src.getPixels(), db = dst.getPixels();
        int stride = w * 4;
        byte[] row = new byte[stride];
        for(int y = 0; y < h; y++){
            sb.position(y * stride);
            sb.get(row);
            db.position((h - 1 - y) * stride);
            db.put(row);
        }
        return dst;
    }
}
