package com.phoenix.game.content;

import com.phoenix.game.entities.effect.Fire;
import com.phoenix.game.entities.effect.Puddle;
import com.phoenix.game.entities.type.Player;
import com.phoenix.game.type.TypeID;

/**
 * 可同步实体类型集合。参照 Mindustry mindustry.content.TypeIDs 移植。
 * <p>id 顺序 = 本类的创建顺序（由 {@code ContentLoader} 注册分配），与原版加载顺序一致。
 */
public class TypeIDs implements com.phoenix.game.ctype.ContentList{
    public static TypeID fire, puddle, player;

    @Override
    public void load(){
        fire = new TypeID("fire", Fire::new);
        puddle = new TypeID("puddle", Puddle::new);
        player = new TypeID("player", Player::new);
    }
}
