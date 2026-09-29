package com.phoenix.game.math.geom;

/**
 * 整数二维点。参照 {@code arc.math.geom.Point2} 移植。
 * <p>libgdx 没有整数点类型（只有 float 的 {@code Vector2}/{@code GridPoint2}），
 * 而原版大量用 {@code Point2.pack(x, y)} 把瓦片坐标压进一个 int（寻路目标、方块位置等），故保留。
 */
public class Point2{

    public int x;
    public int y;

    public Point2(){
    }

    public Point2(int x, int y){
        set(x, y);
    }

    public Point2(Point2 point){
        set(point);
    }

    /** @return 把 x,y 各取低 16 位压进一个 int。 */
    public static int pack(int x, int y){
        return ((x & 0xffff) << 16) | (y & 0xffff);
    }

    public static Point2 unpack(int packed){
        return new Point2(x(packed), y(packed));
    }

    public static short x(int packed){
        return (short)((packed >> 16) & 0xffff);
    }

    public static short y(int packed){
        return (short)(packed & 0xffff);
    }

    public int pack(){
        return pack(x, y);
    }

    public Point2 set(Point2 point){
        return set(point.x, point.y);
    }

    public Point2 set(int x, int y){
        this.x = x;
        this.y = y;
        return this;
    }

    public float dst2(Point2 point){
        return dst2(point.x, point.y);
    }

    public float dst2(int x, int y){
        int dx = this.x - x;
        int dy = this.y - y;
        return dx * dx + dy * dy;
    }

    public float dst(Point2 point){
        return dst(point.x, point.y);
    }

    public float dst(int x, int y){
        return (float)Math.sqrt(dst2(x, y));
    }

    public Point2 add(Point2 point){
        return add(point.x, point.y);
    }

    public Point2 add(int x, int y){
        this.x += x;
        this.y += y;
        return this;
    }

    public Point2 sub(Point2 point){
        return sub(point.x, point.y);
    }

    public Point2 sub(int x, int y){
        this.x -= x;
        this.y -= y;
        return this;
    }

    public Point2 cpy(){
        return new Point2(this);
    }

    public boolean equals(int x, int y){
        return this.x == x && this.y == y;
    }

    public static boolean equals(int x1, int y1, int x2, int y2){
        return x1 == x2 && y1 == y2;
    }

    @Override
    public boolean equals(Object other){
        if(this == other) return true;
        if(!(other instanceof Point2)) return false;
        Point2 point = (Point2)other;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode(){
        return pack(x, y);
    }

    @Override
    public String toString(){
        return "(" + x + "," + y + ")";
    }
}
