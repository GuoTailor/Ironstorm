package io.anuke.mindustry;


import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ObjectMap;

import java.awt.image.BufferedImage;

public class ImagePacker{
    static ObjectMap<String, TextureRegion> regionCache = new ObjectMap<>();
    static ObjectMap<TextureRegion, BufferedImage> imageCache = new ObjectMap<>();

}
