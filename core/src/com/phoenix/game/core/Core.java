package com.phoenix.game.core;

import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.I18NBundle;

/**
 * create by GYH on 2024/10/31
 */
public class Core {
    public static Application app;
    public static Graphics graphics;
    public static Audio audio;
    public static Input input;
    public static Files files;
    public static Net net;
    public static Camera camera;
    public static I18NBundle bundle;
    public static SpriteBatch batch;
    public static AssetManager assets;
    public static TextureAtlas atlas;
    public static Skin skin;
    public static GL20 gl;
    public static GL20 gl20;
    public static GL30 gl30;
//    public static World world;
//    public static ContentLoader content;
}
