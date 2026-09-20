package io.anuke.mindustry.nmka;
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

import java.nio.ByteBuffer;

public class Pixmaps {
    private static Pixmap drawPixmap;

    public Pixmaps() {
    }

    public static void flip(Pixmap pixmap) {
        ByteBuffer pixels = pixmap.getPixels();
        int numBytes = pixmap.getWidth() * pixmap.getHeight() * 4;
        byte[] lines = new byte[numBytes];
        int numBytesPerLine = pixmap.getWidth() * 4;

        for(int i = 0; i < pixmap.getHeight(); ++i) {
            pixels.position((pixmap.getHeight() - i - 1) * numBytesPerLine);
            pixels.get(lines, i * numBytesPerLine, numBytesPerLine);
        }

        pixels.clear();
        pixels.put(lines);
    }

    public static Pixmap copy(Pixmap input) {
        Pixmap pixmap = new Pixmap(input.getWidth(), input.getHeight(), Format.RGBA8888);
        pixmap.drawPixmap(input, 0, 0);
        return pixmap;
    }

    public static Pixmap scale(Pixmap input, float scale) {
        return scale(input, scale, scale);
    }

    public static Pixmap scale(Pixmap input, float scalex, float scaley) {
        Pixmap pixmap = new Pixmap((int)((float)input.getWidth() * scalex), (int)((float)input.getHeight() * scaley), Format.RGBA8888);

        for(int x = 0; x < pixmap.getWidth(); ++x) {
            for(int y = 0; y < pixmap.getHeight(); ++y) {
                pixmap.drawPixel(x, y, input.getPixel((int)((float)x / scalex), (int)((float)y / scaley)));
            }
        }

        return pixmap;
    }


    public static Pixmap outline(Pixmap input, Color color) {
        Pixmap pixmap = copy(input);
        pixmap.setColor(color);

        for(int x = 0; x < pixmap.getWidth(); ++x) {
            for(int y = 0; y < pixmap.getHeight(); ++y) {
                if (empty(input.getPixel(x, y)) && (!empty(input.getPixel(x, y + 1)) || !empty(input.getPixel(x, y - 1)) || !empty(input.getPixel(x - 1, y)) || !empty(input.getPixel(x + 1, y)))) {
                    pixmap.drawPixel(x, y);
                }
            }
        }

        return pixmap;
    }

    public static Pixmap zoom(Pixmap input, int scale) {
        Pixmap pixmap = new Pixmap(input.getWidth(), input.getHeight(), Format.RGBA8888);

        for(int x = 0; x < pixmap.getWidth(); ++x) {
            for(int y = 0; y < pixmap.getHeight(); ++y) {
                pixmap.drawPixel(x, y, input.getPixel(x / scale + pixmap.getWidth() / 2 / scale, y / scale + pixmap.getHeight() / 2 / scale));
            }
        }

        return pixmap;
    }

    public static Pixmap resize(Pixmap input, int width, int height) {
        Pixmap pixmap = new Pixmap(width, height, Format.RGBA8888);
        pixmap.drawPixmap(input, width / 2 - input.getWidth() / 2, height / 2 - input.getHeight() / 2);
        return pixmap;
    }

    public static Pixmap resize(Pixmap input, int width, int height, int backgroundColor) {
        Pixmap pixmap = new Pixmap(width, height, Format.RGBA8888);
        pixmap.setColor(backgroundColor);
        pixmap.fill();
        pixmap.drawPixmap(input, width / 2 - input.getWidth() / 2, height / 2 - input.getHeight() / 2);
        return pixmap;
    }

    public static Pixmap crop(Pixmap input, int x, int y, int width, int height) {
        Pixmap pixmap = new Pixmap(width, height, Format.RGBA8888);
        pixmap.drawPixmap(input, 0, 0, x, y, width, height);
        return pixmap;
    }

    public static Pixmap rotate(Pixmap input, float angle) {
        Vector2 vector = new Vector2();
        Pixmap pixmap = new Pixmap(input.getHeight(), input.getWidth(), Format.RGBA8888);

        for(int x = 0; x < input.getWidth(); ++x) {
            for(int y = 0; y < input.getHeight(); ++y) {
                vector.set((float)x - (float)input.getWidth() / 2.0F + 0.5F, (float)y - (float)input.getHeight() / 2.0F);
                vector.rotate(-angle);
                int px = (int)(vector.x + (float)input.getWidth() / 2.0F + 0.01F);
                int py = (int)(vector.y + (float)input.getHeight() / 2.0F + 0.01F);
                pixmap.drawPixel(px - input.getWidth() / 2 + pixmap.getWidth() / 2, py - input.getHeight() / 2 + pixmap.getHeight() / 2, input.getPixel(x, y));
            }
        }

        return pixmap;
    }

    private static boolean empty(int i) {
        return (i & 255) == 0;
    }


    public static Pixmap huePixmap(int width, int height) {
        Pixmap pixmap = new Pixmap(width, height, Format.RGBA8888);
        Color color = new Color(1.0F, 1.0F, 1.0F, 1.0F);

        for(int x = 0; x < width; ++x) {
            color.fromHsv((float)x / (float)width, 1.0F, 1.0F);
            pixmap.setColor(color);

            for(int y = 0; y < height; ++y) {
                pixmap.drawPixel(x, y);
            }
        }

        return pixmap;
    }

    public static Texture hueTexture(int width, int height) {
        return new Texture(huePixmap(width, height));
    }

    public static Pixmap blankPixmap() {
        Pixmap pixmap = new Pixmap(1, 1, Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        return pixmap;
    }

    public static Texture blankTexture() {
        Texture texture = new Texture(blankPixmap());
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return texture;
    }

    public static TextureRegion blankTextureRegion() {
        return new TextureRegion(blankTexture());
    }

    public static void drawPixel(Texture texture, int x, int y, int color) {
        if (drawPixmap == null) {
            drawPixmap = new Pixmap(1, 1, Format.RGBA8888);
        }

        drawPixmap.setColor(color);
        drawPixmap.fill();
        texture.draw(drawPixmap, x, y);
    }
}
