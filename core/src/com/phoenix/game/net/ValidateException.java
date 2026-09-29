package com.phoenix.game.net;

import com.phoenix.game.entities.type.Player;

/**
 * Thrown when a client sends invalid information.
 * <p>与原版逐字一致（仅包名 {@code mindustry.net} → {@code com.phoenix.game.net}，
 * {@code mindustry.entities.type.Player} → {@code com.phoenix.game.entities.type.Player}）。
 */
public class ValidateException extends RuntimeException{
    public final Player player;

    public ValidateException(Player player, String s){
        super(s);
        this.player = player;
    }
}
