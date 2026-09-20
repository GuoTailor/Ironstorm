package io.anuke.mindustry;


import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Draw;
import com.phoenix.game.core.Tmp;
import io.anuke.mindustry.nmka.Fill;

import static com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels;


public class SquareMarcher{
    final int resolution;
    FrameBuffer buffer;

    SquareMarcher(int resolution){
        this.resolution = resolution;
        this.buffer = new FrameBuffer(Pixmap.Format.RGBA8888, resolution, resolution, false, false);
    }


    void render(Pixmap pixmap, FileHandle file){
        boolean[][] grid = new boolean[pixmap.getWidth()][pixmap.getHeight()];

        for(int x = 0; x < pixmap.getWidth(); x++){
            for(int y = 0; y < pixmap.getHeight(); y++){
                Tmp.c1.set(pixmap.getPixel(x, y));
                grid[x][pixmap.getHeight() - 1 - y] = Tmp.c1.a > 0.01f;
            }
        }

        float xscl = resolution / (float)pixmap.getWidth(), yscl = resolution / (float)pixmap.getHeight();
        float scl = xscl;

        Draw.flush();
        Core.batch.getProjectionMatrix().setToOrtho2D(-xscl / 2f, -yscl / 2f, resolution, resolution);



        buffer.begin();
        Core.batch.begin();
//        try {
//            Field drawing = SpriteBatch.class.getDeclaredField("drawing");
//            drawing.setAccessible(true);
//            drawing.setBoolean(Core.batch, true);
//        } catch (NoSuchFieldException | IllegalAccessException e) {
//            throw new RuntimeException(e);
//        }
        Core.gl.glClearColor(Color.CLEAR.r, Color.CLEAR.g, Color.CLEAR.b, Color.CLEAR.a);
        Core.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Draw.color(Color.WHITE);

        for(int x = -1; x < pixmap.getWidth(); x++){
            for(int y = -1; y < pixmap.getHeight(); y++){
                int index = index(x, y, pixmap.getWidth(), pixmap.getHeight(), grid);

                float leftx = x * xscl, boty = y * yscl, rightx = x * xscl + xscl, topy = y * xscl + yscl,
                midx = x * xscl + xscl / 2f, midy = y * yscl + yscl / 2f;

                switch(index){
                    case 0:
                        break;
                    case 1:
                        Fill.tri(
                        leftx, midy,
                        leftx, topy,
                        midx, topy
                        );
                        break;
                    case 2:
                        Fill.tri(
                        midx, topy,
                        rightx, topy,
                        rightx, midy
                        );
                        break;
                    case 3:
                        Fill.crect(leftx, midy, scl, scl / 2f);
                        break;
                    case 4:
                        Fill.tri(
                        midx, boty,
                        rightx, boty,
                        rightx, midy
                        );
                        break;
                    case 5:
                        //ambiguous

                        //7
                        Fill.tri(
                        leftx, midy,
                        midx, midy,
                        midx, boty
                        );

                        //13
                        Fill.tri(
                        midx, topy,
                        midx, midy,
                        rightx, midy
                        );

                        Fill.crect(leftx, midy, scl / 2f, scl / 2f);
                        Fill.crect(midx, boty, scl / 2f, scl / 2f);

                        break;
                    case 6:
                        Fill.crect(midx, boty, scl / 2f, scl);
                        break;
                    case 7:
                        //invert triangle
                        Fill.tri(
                        leftx, midy,
                        midx, midy,
                        midx, boty
                        );

                        //3
                        Fill.crect(leftx, midy, scl, scl / 2f);

                        Fill.crect(midx, boty, scl / 2f, scl / 2f);
                        break;
                    case 8:
                        Fill.tri(
                        leftx, boty,
                        leftx, midy,
                        midx, boty
                        );
                        break;
                    case 9:
                        Fill.crect(leftx, boty, scl / 2f, scl);
                        break;
                    case 10:
                        //ambiguous

                        //11
                        Fill.tri(
                        midx, boty,
                        midx, midy,
                        rightx, midy
                        );

                        //14
                        Fill.tri(
                        leftx, midy,
                        midx, midy,
                        midx, topy
                        );

                        Fill.crect(midx, midy, scl / 2f, scl / 2f);
                        Fill.crect(leftx, boty, scl / 2f, scl / 2f);

                        break;
                    case 11:
                        //invert triangle

                        Fill.tri(
                        midx, boty,
                        midx, midy,
                        rightx, midy
                        );

                        //3
                        Fill.crect(leftx, midy, scl, scl / 2f);

                        Fill.crect(leftx, boty, scl / 2f, scl / 2f);
                        break;
                    case 12:
                        Fill.crect(leftx, boty, scl, scl / 2f);
                        break;
                    case 13:
                        //invert triangle

                        Fill.tri(
                        midx, topy,
                        midx, midy,
                        rightx, midy
                        );

                        //12
                        Fill.crect(leftx, boty, scl, scl / 2f);

                        Fill.crect(leftx, midy, scl / 2f, scl / 2f);
                        break;
                    case 14:
                        //invert triangle

                        Fill.tri(
                        leftx, midy,
                        midx, midy,
                        midx, topy
                        );

                        //12
                        Fill.crect(leftx, boty, scl, scl / 2f);

                        Fill.crect(midx, midy, scl / 2f, scl / 2f);
                        break;
                    case 15:
                        Fill.square(midx, midy, scl / 2f);
                        break;
                }
            }
        }

        Draw.flush();
        Core.batch.end();
        saveScreenshot(file, 0, 0, resolution, resolution);

        buffer.end();
    }

    public static void saveScreenshot(FileHandle file, int x, int y, int width, int height) {
        Pixmap pixmap = getFrameBufferPixmap(x, y, width, height, true);
        PixmapIO.writePNG(file, pixmap);
        pixmap.dispose();
    }

    public static Pixmap getFrameBufferPixmap(int x, int y, int w, int h, boolean flip) {
        byte[] lines = getFrameBufferPixels(x, y, w, h, flip);
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        BufferUtils.copy(lines, 0, pixmap.getPixels(), lines.length);
        return pixmap;
    }


    int index(int x, int y, int w, int h, boolean[][] grid){
        int botleft = sample(grid, x, y);
        int botright = sample(grid, x + 1, y);
        int topright = sample(grid, x + 1, y + 1);
        int topleft = sample(grid, x, y + 1);
        return (botleft << 3) | (botright << 2) | (topright << 1) | topleft;
    }

    int sample(boolean[][] grid, int x, int y){
        return (x < 0 || y < 0 || x >= grid.length || y >= grid.length) ? 0 : grid[x][y] ? 1 : 0;
    }
}
