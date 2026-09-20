package io.anuke.mindustry.nmka;
//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


public class Angles {
    public Angles() {
    }


    public static float trnsx(float angle, float len) {
        return (float) (len * Math.cos(((float)Math.PI / 180F) * angle));
    }

    public static float trnsy(float angle, float len) {
        return (float) (len * Math.sin(((float)Math.PI / 180F) * angle));
    }

}
