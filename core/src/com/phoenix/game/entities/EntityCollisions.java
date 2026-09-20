package com.phoenix.game.entities;


import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.phoenix.game.Vars;
import com.phoenix.game.entities.traits.Entity;
import com.phoenix.game.entities.traits.SolidTrait;
import com.phoenix.game.entities.type.BaseEntity;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.world.Tile;

import static com.phoenix.game.Vars.tilesize;

public class EntityCollisions {
    //range for tile collision scanning
    private static final int r = 1;
    //move in 1-unit chunks
    private static final float seg = 1f;

    //tile collisions
    private Rectangle tmp = new Rectangle();
    private Vector2 vector = new Vector2();
    private Vector2 l1 = new Vector2();
    private Rectangle r1 = new Rectangle();
    private Rectangle r2 = new Rectangle();
    private Rectangle r3 = new Rectangle();

    //entity collisions
    private Array<SolidTrait> arrOut = new Array<>();

    public void move(SolidTrait entity, float deltax, float deltay){

        //记录移动前的位置，供插值/提前量计算使用
        entity.lastPosition().set(entity.getX(), entity.getY());

        boolean movedx = false;

        while(Math.abs(deltax) > 0 || !movedx){
            movedx = true;
            moveDelta(entity, Math.min(Math.abs(deltax), seg) * Mathf.sign(deltax), 0, true);

            if(Math.abs(deltax) >= seg){
                deltax -= seg * Mathf.sign(deltax);
            }else{
                deltax = 0f;
            }
        }

        boolean movedy = false;

        while(Math.abs(deltay) > 0 || !movedy){
            movedy = true;
            moveDelta(entity, 0, Math.min(Math.abs(deltay), seg) * Mathf.sign(deltay), false);

            if(Math.abs(deltay) >= seg){
                deltay -= seg * Mathf.sign(deltay);
            }else{
                deltay = 0f;
            }
        }
    }

    public void moveDelta(SolidTrait entity, float deltax, float deltay, boolean x){

        Rectangle rect = r1;
        entity.hitboxTile(rect);
        entity.hitboxTile(r2);
        rect.x += deltax;
        rect.y += deltay;

        //注意：本项目瓦片 n 覆盖 [n*tilesize, (n+1)*tilesize)（角点约定，与原版的中心约定不同），
        //所以索引用向下取整、碰撞框中心要 +tilesize/2
        int tilex = Vars.world == null ? 0 : Vars.world.toTile(rect.x + rect.width / 2);
        int tiley = Vars.world == null ? 0 : Vars.world.toTile(rect.y + rect.height / 2);

        for(int dx = -r; dx <= r; dx++){
            for(int dy = -r; dy <= r; dy++){
                int wx = dx + tilex, wy = dy + tiley;
                if(solid(wx, wy) && entity.collidesGrid(wx, wy)){
                    tmp.setSize(tilesize).setCenter(wx * tilesize + tilesize / 2f, wy * tilesize + tilesize / 2f);

                    if(tmp.overlaps(rect)){
                        Vector2 v = Geometry.overlap(rect, tmp, x);
                        rect.x += v.x;
                        rect.y += v.y;
                    }
                }
            }
        }

        entity.setX(entity.getX() + rect.x - r2.x);
        entity.setY(entity.getY() + rect.y - r2.y);
    }

    public boolean overlapsTile(Rectangle rect){
        rect.getCenter(vector);
        int r = 1;

        //瓦片是角点约定（覆盖 [n*tilesize, (n+1)*tilesize)）
        int tilex = Vars.world == null ? 0 : Vars.world.toTile(vector.x);
        int tiley = Vars.world == null ? 0 : Vars.world.toTile(vector.y);

        for(int dx = -r; dx <= r; dx++){
            for(int dy = -r; dy <= r; dy++){
                int wx = dx + tilex, wy = dy + tiley;
                if(solid(wx, wy)){
                    r2.setSize(tilesize).setCenter(wx * tilesize + tilesize / 2f, wy * tilesize + tilesize / 2f);

                    if(r2.overlaps(rect)){
                        return true;
                    }
                }
            }
        }
        return false;
    }


    private static boolean solid(int x, int y){
        Tile tile = Vars.world == null ? null : Vars.world.tile(x, y);
        return tile != null && tile.solid();
    }

    /** 记录一组实体的上一帧位置（每帧调用一次）。 */
    public void updatePhysics(Array<? extends Entity> group){
        for(Entity entity : group){
            if(entity instanceof SolidTrait){
                SolidTrait solid = (SolidTrait)entity;
                solid.lastPosition().set(solid.getX(), solid.getY());
            }
        }
    }

    /** 两组实心实体之间的碰撞检测与回调。注意：已被移除的实体（如已回收的子弹）会被跳过。 */
    public void collideGroups(Array<? extends Entity> groupa, Array<? extends Entity> groupb){
        for(Entity entity : groupa){
            if(!(entity instanceof SolidTrait) || removed(entity)) continue;

            SolidTrait solid = (SolidTrait)entity;

            solid.hitbox(r1);
            r1.x += (solid.lastPosition().x - solid.getX());
            r1.y += (solid.lastPosition().y - solid.getY());

            solid.hitbox(r2);
            r2.merge(r1);

            for(Entity other : groupb){
                if(entity == other || !(other instanceof SolidTrait) || removed(other)) continue;

                SolidTrait sc = (SolidTrait)other;
                sc.hitbox(r3);

                if(r2.overlaps(r3)){
                    checkCollide(solid, sc);
                }
            }
        }
    }

    /** @return 该实体是否已被移除（死亡的子弹会被放回对象池，不能再参与碰撞）。 */
    private static boolean removed(Entity entity){
        return entity instanceof BaseEntity && ((BaseEntity)entity).isDead();
    }

    private void checkCollide(SolidTrait a, SolidTrait b){
        a.hitbox(r1);
        b.hitbox(r2);

        r1.x += (a.lastPosition().x - a.getX());
        r1.y += (a.lastPosition().y - a.getY());
        r2.x += (b.lastPosition().x - b.getX());
        r2.y += (b.lastPosition().y - b.getY());

        float vax = a.getX() - a.lastPosition().x;
        float vay = a.getY() - a.lastPosition().y;
        float vbx = b.getX() - b.lastPosition().x;
        float vby = b.getY() - b.lastPosition().y;

        if(a.collides(b) && b.collides(a)){
            l1.set(a.getX(), a.getY());

            boolean collide = r1.overlaps(r2) || collide(r1.x, r1.y, r1.width, r1.height, vax, vay,
                    r2.x, r2.y, r2.width, r2.height, vbx, vby, l1);

            if(collide){
                a.collision(b, l1.x, l1.y);
                b.collision(a, l1.x, l1.y);
            }
        }
    }


    private boolean collide(float x1, float y1, float w1, float h1, float vx1, float vy1,
                            float x2, float y2, float w2, float h2, float vx2, float vy2, Vector2 out){
        float px = vx1, py = vy1;

        vx1 -= vx2;
        vy1 -= vy2;

        float xInvEntry, yInvEntry;
        float xInvExit, yInvExit;

        if(vx1 > 0.0f){
            xInvEntry = x2 - (x1 + w1);
            xInvExit = (x2 + w2) - x1;
        }else{
            xInvEntry = (x2 + w2) - x1;
            xInvExit = x2 - (x1 + w1);
        }

        if(vy1 > 0.0f){
            yInvEntry = y2 - (y1 + h1);
            yInvExit = (y2 + h2) - y1;
        }else{
            yInvEntry = (y2 + h2) - y1;
            yInvExit = y2 - (y1 + h1);
        }

        float xEntry, yEntry;
        float xExit, yExit;

        xEntry = xInvEntry / vx1;
        xExit = xInvExit / vx1;

        yEntry = yInvEntry / vy1;
        yExit = yInvExit / vy1;

        float entryTime = Math.max(xEntry, yEntry);
        float exitTime = Math.min(xExit, yExit);

        if(entryTime > exitTime || xExit < 0.0f || yExit < 0.0f || xEntry > 1.0f || yEntry > 1.0f){
            return false;
        }else{
            float dx = x1 + w1 / 2f + px * entryTime;
            float dy = y1 + h1 / 2f + py * entryTime;

            out.set(dx, dy);

            return true;
        }
    }

}
