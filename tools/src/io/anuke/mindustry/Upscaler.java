package io.anuke.mindustry;


import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.phoenix.game.core.Core;
import io.anuke.mindustry.nmka.Fill;
import io.anuke.mindustry.nmka.IconSize;
import io.anuke.mindustry.nmka.Pixmaps;

public class Upscaler{
    public static void main(String[] args){
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        new Lwjgl3Application(new ApplicationListener(){
            @Override
            public void create() {
                scale();
            }

            @Override
            public void resize(int width, int height) {

            }

            @Override
            public void render() {

            }

            @Override
            public void pause() {

            }

            @Override
            public void resume() {

            }

            @Override
            public void dispose() {

            }

        }, config);
    }

    static void scale(){
        Core.batch = new SpriteBatch(4096, null);
        Core.atlas = new TextureAtlas();
        Texture texture = Pixmaps.blankTexture();
        Core.atlas.addRegion("white", texture,0, 0, texture.getWidth(), texture.getHeight());
        Core.files = Gdx.files;
        Core.gl = Gdx.gl;
        FileHandle file = Core.files.local("");

        FileHandle[] list = file.list();

        Fill.region = Core.atlas.findRegion("white");
        for(IconSize size : IconSize.values()){
            String suffix = size == IconSize.def ? "" : "-" + size.name();
            SquareMarcher marcher = new SquareMarcher(size.size);

            for(FileHandle img : list){
                if(img.extension().equals("png")){
                    Pixmap pixmap = new Pixmap(img);
                    pixmap.setColor(0);
                    pixmap.setBlending(Pixmap.Blending.SourceOver);
                    pixmap.setFilter(Pixmap.Filter.BiLinear);
                    marcher.render(pixmap, img.sibling(img.nameWithoutExtension() + suffix + ".png"));
                }
            }
        }
        Core.app = Gdx.app;
        Core.app.exit();
    }
}
