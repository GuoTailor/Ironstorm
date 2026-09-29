package com.phoenix.game.world.blocks;

import com.badlogic.gdx.math.GridPoint2;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.math.geom.Geometry;
import com.phoenix.game.math.geom.Point2;
import com.phoenix.game.util.ArcAnnotate.*;
import com.phoenix.game.util.Eachable;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.BuildRequest;
import com.phoenix.game.world.Edges;
import com.phoenix.game.world.Tile;

import java.util.Arrays;

/**
 * 自动拼接。参照 Mindustry mindustry.world.blocks.Autotiler 移植。
 * <p>传送带/管道这类"要和邻居连成一条线"的方块实现本接口，{@link #buildBlending} 按
 * 四邻（右/后/左，相对朝向）是否存在同类方块，算出一张拼接贴图的分组号与镜像标记。
 * <p>实现类只需要提供 {@link #blends(Tile, int, int, int, int, Block)}（判断"这一格能不能和那边拼"）。
 * <p>索引约定：本项目 {@link Geometry#d4} 的元素顺序与原版**相反**（原版从 +x 起逆时针，
 * 本项目从 +y 起顺时针），同一个"相对方向"在原版是 {@code rotation - direction}，
 * 在本项目要写成 {@code rotation + direction}（见 {@code Conveyor.blends}）。
 * <p>刻意偏离（B 类）：原版 {@code getTiling} 把 {@code req.worldContext} 透传给 {@code buildBlending}，
 * 本项目 {@link BuildRequest} 没有该字段，故固定传 true。而且本项目的 {@link #getTiling} **目前没有调用方**
 * （原版由 {@code drawRequestRegion} 走它；本项目建造预览在 {@code InputHandler} 自行合成贴图、
 * 蓝图预览 {@code Schematics.getBuffer} 直接画方块贴图），所以 {@code worldContext} 这条链整段无消费方。
 */
public interface Autotiler{
    class AutotilerHolder{
        static final int[] blendresult = new int[3];
        static final BuildRequest[] directionals = new BuildRequest[4];
    }

    default @Nullable int[] getTiling(BuildRequest req, Eachable<BuildRequest> list){
        if(req.tile() == null) return null;
        BuildRequest[] directionals = AutotilerHolder.directionals;

        Arrays.fill(directionals, null);
        list.each(other -> {
            if(other.breaking || other == req) return;

            int i = 0;
            for(GridPoint2 point : Geometry.d4){
                int x = req.x + point.x, y = req.y + point.y;
                if(x >= other.x - (other.block.size - 1) / 2 && x <= other.x + (other.block.size / 2) && y >= other.y - (other.block.size - 1) / 2 && y <= other.y + (other.block.size / 2)){
                    directionals[i] = other;
                }
                i++;
            }
        });

        //TODO（B 类）原版传 req.worldContext（BuildRequest 未移植该字段），暂固定 true；
        //     且本方法当前无调用方（无 drawRequestRegion），worldContext 无消费方
        return buildBlending(req.tile(), req.rotation, directionals, true);
    }

    default int[] buildBlending(Tile tile, int rotation, BuildRequest[] directional, boolean world){
        int[] blendresult = AutotilerHolder.blendresult;
        blendresult[0] = 0;
        blendresult[1] = blendresult[2] = 1;
        int num =
        (blends(tile, rotation, directional, 2, world) && blends(tile, rotation, directional, 1, world) && blends(tile, rotation, directional, 3, world)) ? 0 :
        (blends(tile, rotation, directional, 1, world) && blends(tile, rotation, directional, 3, world)) ? 1 :
        (blends(tile, rotation, directional, 1, world) && blends(tile, rotation, directional, 2, world)) ? 2 :
        (blends(tile, rotation, directional, 3, world) && blends(tile, rotation, directional, 2, world)) ? 3 :
        blends(tile, rotation, directional, 1, world) ? 4 :
        blends(tile, rotation, directional, 3, world) ? 5 :
        -1;
        transformCase(num, blendresult);
        return blendresult;
    }

    default void transformCase(int num, int[] bits){
        if(num == 0){
            bits[0] = 3;
        }else if(num == 1){
            bits[0] = 4;
        }else if(num == 2){
            bits[0] = 2;
        }else if(num == 3){
            bits[0] = 2;
            bits[2] = -1;
        }else if(num == 4){
            bits[0] = 1;
            bits[2] = -1;
        }else if(num == 5){
            bits[0] = 1;
        }
    }

    default boolean blends(Tile tile, int rotation, @Nullable BuildRequest[] directional, int direction, boolean checkWorld){
        //原版是 rotation - direction（d4 从 +x 起逆时针）；本项目 d4 从 +y 起顺时针，索引旋向相反，故用 +
        int realDir = Mathf.mod(rotation + direction, 4);
        if(directional != null && directional[realDir] != null){
            BuildRequest req = directional[realDir];
            if(blends(tile, rotation, req.x, req.y, req.rotation, req.block)){
                return true;
            }
        }
        return checkWorld && blends(tile, rotation, direction);
    }

    default boolean blends(Tile tile, int rotation, int direction){
        Tile other = tile.getNearby(Mathf.mod(rotation + direction, 4));
        if(other != null) other = other.link();
        return other != null && other.getTeam() == tile.getTeam() && blends(tile, rotation, other.x, other.y, other.rotation(), other.block());
    }

    default boolean blendsArmored(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock){
        return (Point2.equals(tile.x + Geometry.d4[rotation & 3].x, tile.y + Geometry.d4[rotation & 3].y, otherx, othery)
                || ((!otherblock.rotate && Edges.getFacingEdge(otherblock, otherx, othery, tile) != null &&
                Edges.getFacingEdge(otherblock, otherx, othery, tile).relativeTo(tile) == rotation) || (otherblock.rotate && Point2.equals(otherx + Geometry.d4[otherrot & 3].x, othery + Geometry.d4[otherrot & 3].y, tile.x, tile.y))));
    }

    default boolean lookingAt(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock){
        return (Point2.equals(tile.x + Geometry.d4[rotation & 3].x, tile.y + Geometry.d4[rotation & 3].y, otherx, othery)
        || (!otherblock.rotate || Point2.equals(otherx + Geometry.d4[otherrot & 3].x, othery + Geometry.d4[otherrot & 3].y, tile.x, tile.y)));
    }

    boolean blends(Tile tile, int rotation, int otherx, int othery, int otherrot, Block otherblock);
}
