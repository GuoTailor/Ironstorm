package com.phoenix.game.type;

/** 液体 + 数量。参照原版 mindustry.type.LiquidStack 移植。 */
public class LiquidStack{
    public Liquid liquid;
    public float amount;

    public LiquidStack(Liquid liquid, float amount){
        this.liquid = liquid;
        this.amount = amount;
    }

    /** serialization only*/
    protected LiquidStack(){

    }

    @Override
    public String toString(){
        return "LiquidStack{" +
        "liquid=" + liquid +
        ", amount=" + amount +
        '}';
    }
}
