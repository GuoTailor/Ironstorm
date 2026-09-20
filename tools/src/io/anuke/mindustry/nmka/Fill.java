package io.anuke.mindustry.nmka;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.core.Core;

public class Fill {
    private static float[] vertices = new float[20];
    private static TextureRegion circleRegion;
    public static TextureRegion region;

    public Fill() {
    }

    public static void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {

        float color = Core.batch.getPackedColor();
        float mcolor = 0;
        float u = region.getU();
        float v = region.getV();
        vertices[0] = x1;
        vertices[1] = y1;
        vertices[2] = color;
        vertices[3] = u;
        vertices[4] = v;

        vertices[5] = x2;
        vertices[6] = y2;
        vertices[7] = color;
        vertices[8] = u;
        vertices[9] = v;

        vertices[10] = x3;
        vertices[11] = y3;
        vertices[12] = color;
        vertices[13] = u;
        vertices[14] = v;

        vertices[15] = x4;
        vertices[16] = y4;
        vertices[17] = color;
        vertices[18] = u;
        vertices[19] = v;
        Core.batch.draw(region.getTexture(), vertices, 0, vertices.length);
    }

    public static void tri(float x1, float y1, float x2, float y2, float x3, float y3) {
        quad(x1, y1, x2, y2, x3, y3, x3, y3);
    }

    public static void poly(float x, float y, int sides, float radius) {
        poly(x, y, sides, radius, 0.0F);
    }

    public static void poly(float x, float y, int sides, float radius, float rotation) {
        float space = 360.0F / (float)sides;

        for(int i = 0; i < sides - 2; i += 3) {
            float px = Angles.trnsx(space * (float)i + rotation, radius);
            float py = Angles.trnsy(space * (float)i + rotation, radius);
            float px2 = Angles.trnsx(space * (float)(i + 1) + rotation, radius);
            float py2 = Angles.trnsy(space * (float)(i + 1) + rotation, radius);
            float px3 = Angles.trnsx(space * (float)(i + 2) + rotation, radius);
            float py3 = Angles.trnsy(space * (float)(i + 2) + rotation, radius);
            float px4 = Angles.trnsx(space * (float)(i + 3) + rotation, radius);
            float py4 = Angles.trnsy(space * (float)(i + 3) + rotation, radius);
            quad(x + px, y + py, x + px2, y + py2, x + px3, y + py3, x + px4, y + py4);
        }

        int mod = sides % 3;
        if (mod != 0) {
            for(int i = sides - mod - 1; i < sides; ++i) {
                float px = Angles.trnsx(space * (float)i + rotation, radius);
                float py = Angles.trnsy(space * (float)i + rotation, radius);
                float px2 = Angles.trnsx(space * (float)(i + 1) + rotation, radius);
                float py2 = Angles.trnsy(space * (float)(i + 1) + rotation, radius);
                tri(x, y, x + px, y + py, x + px2, y + py2);
            }

        }
    }


    public static void rect(float x, float y, float w, float h) {
        Core.batch.draw(region, x - w / 2.0F, y - h / 2.0F,0.0F, 0.0F, w, h, 1, 1, 0.0F);
    }

    public static void crect(float x, float y, float w, float h) {
        Core.batch.draw(region, x, y,0.0F, 0.0F, w, h, 1, 1, 0.0F);
    }

    public static void square(float x, float y, float radius) {
        rect(x, y, radius * 2.0F, radius * 2.0F);
    }


    public static void dispose() {
        circleRegion = null;
    }
}
