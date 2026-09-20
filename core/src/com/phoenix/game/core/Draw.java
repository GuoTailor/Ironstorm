package com.phoenix.game.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * create by GYH on 2024/10/31
 */

public class Draw{

    private static final Color[] carr = new Color[3];
    private static float actualZ;
    private static Color retColor = new Color(), retPackedColor = new Color();

    public static float scl = 1f;
    public static float xscl = 1f, yscl = 1f;

    public static ShaderProgram getShader() {
        return Core.batch.getShader();
    }

    public static void shader(ShaderProgram shader) {
        shader(shader, true);
    }

    public static void shader(ShaderProgram shader, boolean apply) {
        Core.batch.setShader(shader);
    }

    public static void shader() {
        Core.batch.setShader(null);
    }

    public static Color getColor() {
        return Core.batch.getColor();
    }


    public static void tint(Color a, Color b, float s) {
        Tmp.c1.set(a).lerp(b, s);
        Core.batch.setColor(Tmp.c1.r, Tmp.c1.g, Tmp.c1.b, Core.batch.getColor().a);
    }

    public static void tint(Color color) {
        Core.batch.setColor(color.r, color.g, color.b, Core.batch.getColor().a);
    }

    public static void colorMul(Color color, float mul) {
        color(color.r * mul, color.g * mul, color.b * mul, 1.0F);
    }

    public static void color(Color color) {
        Core.batch.setColor(color);
    }

    public static void color(Color color, float alpha) {
        Core.batch.setColor(color.r, color.g, color.b, alpha);
    }

    public static void color(float color) {
        Core.batch.setPackedColor(color);
    }

    public static void color(Color a, Color b, float s) {
        Core.batch.setColor(Tmp.c1.set(a).lerp(b, s));
    }

    /** 三段渐变：s&lt;0.5 在 a→b 之间，否则在 b→c 之间（对应 arc 的 {@code Draw.color(a,b,c,s)}）。 */
    public static void color(Color a, Color b, Color c, float s) {
        if(s < 0.5f){
            Core.batch.setColor(Tmp.c1.set(a).lerp(b, s * 2f));
        }else{
            Core.batch.setColor(Tmp.c1.set(b).lerp(c, s * 2f - 1f));
        }
    }

    public static void color() {
        Core.batch.setPackedColor(Color.WHITE_FLOAT_BITS);
    }

    public static void color(float r, float g, float b) {
        Core.batch.setColor(r, g, b, 1.0F);
    }

    public static void color(float r, float g, float b, float a) {
        Core.batch.setColor(r, g, b, a);
    }

    public static void colorl(float l) {
        color(l, l, l);
    }

    public static void colorl(float l, float a) {
        color(l, l, l, a);
    }


    public static void alpha(float alpha) {
        Core.batch.setColor(Core.batch.getColor().r, Core.batch.getColor().g, Core.batch.getColor().b, alpha);
    }

    public static void fbo(Texture texture, int worldWidth, int worldHeight, int tilesize) {
        float ww = (float)(worldWidth * tilesize);
        float wh = (float)(worldHeight * tilesize);
        float x = Core.camera.position.x + (float)tilesize / 2.0F;
        float y = Core.camera.position.y + (float)tilesize / 2.0F;
        float u = (x - Core.camera.viewportWidth / 2.0F) / ww;
        float v = (y - Core.camera.viewportHeight / 2.0F) / wh;
        float u2 = (x + Core.camera.viewportWidth / 2.0F) / ww;
        float v2 = (y + Core.camera.viewportHeight / 2.0F) / wh;
        Tmp.tr1.setRegion(texture);
        Tmp.tr1.setRegion(u, v2, u2, v);
        rect(Tmp.tr1, Core.camera.position.x, Core.camera.position.y, Core.camera.viewportWidth, Core.camera.viewportHeight);
    }

    public static void rect(TextureRegion region, float x, float y) {
        rect(region, x, y, (float)region.getRegionWidth() * scl, (float)region.getRegionHeight() * scl);
    }

    public static void rect(TextureRegion region, float x, float y, float w, float h) {
        //画精灵前先把几何会话收尾，保证「精灵 → 几何 → 精灵」的绘制顺序严格成立
        Drawf.end();
        Core.batch.draw(region, x - w / 2.0F, y - h / 2.0F, w, h);
    }

    public static void rect(TextureRegion region, float x, float y, float rotation) {
        rect(region, x, y, (float)region.getRegionWidth() * scl, (float)region.getRegionHeight() * scl, rotation);
    }

    public static void rect(TextureRegion region, float x, float y, float w, float h, float rotation) {
        Drawf.end();
        Core.batch.draw(region, x - w / 2.0F, y - h / 2.0F, w / 2.0F, h / 2.0F, w, h, 1, 1, rotation);
    }

    public static void rect(TextureRegion region, float x, float y, float w, float h, float originX, float originY, float rotation) {
        Drawf.end();
        Core.batch.draw(region, x - w / 2.0F, y - h / 2.0F, originX, originY, w, h, 1, 1, rotation);
    }

    public static void flush() {
        //几何会话先落盘，再 flush 精灵批次（批次 flush 会重新绑定自己的 shader 与贴图）
        Drawf.end();
        Core.batch.flush();
    }

    public static TextureRegion wrap(Texture texture) {
        Tmp.tr2.setRegion(texture);
        return Tmp.tr2;
    }
}
