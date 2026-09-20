package com.phoenix.game.world.blocks;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.phoenix.game.Vars;
import com.phoenix.game.content.Blocks;
import com.phoenix.game.core.Core;
import com.phoenix.game.core.Time;
import com.phoenix.game.entities.type.TileEntity;
import com.phoenix.game.game.Team;
import com.phoenix.game.math.Mathf;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Block;
import com.phoenix.game.world.Tile;
import com.phoenix.game.world.modules.ItemModule;

import static com.phoenix.game.Vars.tilesize;

/**
 * 在建占位方块。参照 Mindustry mindustry.world.blocks.BuildBlock 移植。
 * <p>放置/拆除都不是瞬间完成的：先在目标格铺一个 {@code buildN}（N = 尺寸），
 * 由建造单位每帧往 {@link BuildEntity#progress} 上推进度，进度满 1 才换成真正的方块（或彻底移除）。
 * 材料不是一次扣光，而是跟着进度从核心库存里**逐份扣**（{@code accumulator}），
 * 拆除同理按比例返还 —— 这是原版"半途取消不会白亏/白赚"的实现方式。
 * <p>按尺寸分档是因为占位方块必须和目标同尺寸（多格建筑要占满同样多的格子）。
 * <p>未移植：建造粒子（原版 Fx.placeBlock/breakBlock）、进度用的 blockbuild shader
 * （这里退化成按进度给目标贴图加不透明度）、取消/改配置的网络同步。
 */
public class BuildBlock extends Block{
    /** 支持的最大尺寸（与原版一致）。 */
    public static final int maxSize = 9;
    private static final BuildBlock[] buildBlocks = new BuildBlock[maxSize];

    public BuildBlock(int size){
        super("build" + size);
        this.size = size;
        update = true;
        health = 20;
        //必须可拆：否则点它无法取消一个在建/待拆的方块
        destructible = true;
        entityType = BuildEntity::new;

        buildBlocks[size - 1] = this;
    }

    /** @return 指定尺寸的在建占位方块。 */
    public static BuildBlock get(int size){
        if(size > maxSize) throw new IllegalArgumentException("Don't place BuildBlocks of size greater than " + maxSize);
        return buildBlocks[size - 1];
    }

    /** 占位方块本身没有贴图，但要让渲染器走到 draw/drawLayer 去画目标方块，所以不能隐藏。 */
    @Override
    public boolean isHidden(){
        return false;
    }

    /** @return 该在建格当前代表的目标方块（正在建的，或拆除前的）；没有则 null。 */
    private static Block targetOf(Tile tile){
        if(!(tile.entity instanceof BuildBlock.BuildEntity)) return null;
        BuildBlock.BuildEntity e = (BuildBlock.BuildEntity)tile.entity;
        Block target = e.cblock != null ? e.cblock : e.previous;
        return target == Blocks.air ? null : target;
    }

    /**
     * 显示名跟随目标方块（对应原版 {@code BuildBlock.getDisplayName}）。
     * <p>否则悬停一个"正在建造的钻头"会显示占位方块的名字（build2），既没有翻译也是错的。
     */
    @Override
    public String getDisplayName(Tile tile){
        Block target = targetOf(tile);
        return target != null ? target.getDisplayName(tile) : super.getDisplayName(tile);
    }

    /** 详情面板图标同样跟随目标方块（对应原版 {@code BuildBlock.getDisplayIcon}）。 */
    @Override
    public TextureRegion getDisplayIcon(Tile tile){
        Block target = targetOf(tile);
        return target != null ? target.getDisplayIcon(tile) : super.getDisplayIcon(tile);
    }

    /** 实心与否跟随它代表的方块（在建的建筑不该比目标更"实"）。 */
    @Override
    public boolean isSolidFor(Tile tile){
        if(tile.entity instanceof BuildEntity){
            BuildEntity e = (BuildEntity)tile.entity;
            Block target = e.cblock != null ? e.cblock : e.previous;
            if(target != null) return target.solid || target.isSolidFor(tile);
        }
        return false;
    }

    /** 底层：还留着上一个方块（拆到一半时仍应看得见它）。 */
    @Override
    public void draw(Tile tile){
        if(!(tile.entity instanceof BuildEntity)) return;
        BuildEntity e = (BuildEntity)tile.entity;

        //拆除中且目标就是原方块：原方块贴图不该继续显示（它就是正在被拆的那个）
        if(e.cblock != null && e.previous == e.cblock) return;
        if(e.previous == null || e.previous == Blocks.air) return;

        drawRegion(e.previous, tile, 1f);
    }

    /** 叠层：按进度画目标方块（进度越高越不透明），这是"建造进度条"的视觉替代。 */
    @Override
    public void drawLayer(Tile tile){
        if(!(tile.entity instanceof BuildEntity)) return;
        BuildEntity e = (BuildEntity)tile.entity;

        Block target = e.cblock != null ? e.cblock : e.previous;
        if(target == null || target == Blocks.air) return;

        drawRegion(target, tile, Mathf.clamp(e.progress));
    }

    private void drawRegion(Block block, Tile tile, float alpha){
        TextureRegion region = block.region;
        if(region == null) return;

        float size = block.size * tilesize;
        float cx = tile.worldx() + block.offset() + tilesize / 2f;
        float cy = tile.worldy() + block.offset() + tilesize / 2f;

        Core.batch.setColor(1f, 1f, 1f, alpha);
        Core.batch.draw(region, cx - size / 2f, cy - size / 2f, size / 2f, size / 2f,
            size, size, 1f, 1f, block.rotate ? tile.rotation() * 90f : 0f);
        Core.batch.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * 建造完成：换成真正的方块，并按进度保留血量比例（与原版 onConstructFinish 一致）。
     * @param config 要下发的配置值；&lt;0 表示没有配置。有配置时**不调** {@code playerPlaced}
     *   （对应原版 skipConfig：蓝图粘贴的桥要按蓝图里的目标连，而不是连到"上次放置的桥"）
     */
    public static void constructed(Tile tile, Block block, Team team, int rotation, int config){
        if(tile == null || block == null) return;

        float healthf = tile.entity == null ? 1f : tile.entity.healthf();
        tile.setBlock(block, team, rotation);
        if(tile.entity != null){
            tile.entity.health(block.health * healthf);
        }

        //建成粒子（对应原版 Fx.placeBlock；原版把方块边长当 e.rotation 用来缩放方框）
        com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.placeBlock,
            block.centerX(tile), block.centerY(tile), block.size);

        if(config >= 0){
            tile.configure(config);
        }else{
            block.playerPlaced(tile);
        }

        //服务端：建成后必须立刻广播 —— 这一格从此不再是"在建方块"，
        //不会被周期性的在建方块广播覆盖到，不补这一发客机会永远停在最后一帧进度上
        if(Vars.isServer()){
            Vars.netServer.broadcastBlockState(tile);
        }
    }

    /** 拆除完成：清掉这一格。 */
    public static void deconstructFinish(Tile tile){
        if(tile == null) return;
        Block block = tile.block();
        float cx = block.centerX(tile), cy = block.centerY(tile);
        tile.remove();
        com.phoenix.game.entities.Effects.effect(com.phoenix.game.content.Fx.breakBlock, block.color, cx, cy, block.size);

        //服务端：拆除完成同样要广播移除
        if(Vars.isServer()){
            Vars.netServer.broadcastBlockRemove(tile);
        }
    }

    /**
     * 在建方块实体。参照原版 {@code BuildBlock.BuildEntity}。
     * <p>三个数组的语义容易看混，这里写清楚：
     * <ul>
     *     <li>{@code accumulator}：**当前欠着核心的材料**。推进时按进度往里加，真扣的时候再减掉；</li>
     *     <li>{@code totalAccumulator}：**已经扣掉的材料总量**，用来封顶，避免多扣。</li>
     * </ul>
     */
    public class BuildEntity extends TileEntity{
        /** 正在建造的目标方块；拆除无配方方块时为 null。 */
        public Block cblock;
        /** 建造进度 0~1。 */
        public float progress;
        /** 建造总成本（材料价值之和），进度推进速度 = 1 / buildCost。 */
        public float buildCost;
        /** 这一格原本的方块（建造时用来"替换"，拆除时就是被拆对象）。 */
        public Block previous;

        private float[] accumulator;
        private float[] totalAccumulator;

        /**
         * 推进建造进度。
         * @param inventory 材料来源（本工程里就是队伍共享库存）
         * @param amount 本帧的进度增量（未扣材料前）
         * @return true 表示本帧建成
         */
        public boolean construct(ItemModule inventory, float amount){
            if(cblock == null){
                kill();
                return false;
            }

            if(accumulator == null || accumulator.length != cblock.requirements.length){
                setConstruct(previous, cblock);
            }

            //第一步：算出"材料够不够推进这么多"
            float maxProgress = checkRequired(inventory, amount, false);

            for(int i = 0; i < cblock.requirements.length; i++){
                int reqamount = cblock.requirements[i].amount;
                accumulator[i] += Math.min(reqamount * maxProgress, reqamount - totalAccumulator[i] + 0.00001f);
                totalAccumulator[i] = Math.min(totalAccumulator[i] + reqamount * maxProgress, reqamount);
            }

            //第二步：真扣（这一步才知道实际能扣多少，再反过来修正进度）
            maxProgress = checkRequired(inventory, maxProgress, true);
            progress = Mathf.clamp(progress + maxProgress);

            if(progress >= 1f){
                return true;
            }
            return false;
        }

        /** 推进拆除进度，并按比例把材料还给库存。 */
        public void deconstruct(ItemModule inventory, float amount){
            float refund = Vars.state.rules.deconstructRefundMultiplier;

            if(cblock != null){
                ItemStack[] requirements = cblock.requirements;
                if(accumulator == null || accumulator.length != requirements.length){
                    setDeconstruct(cblock);
                }

                //不能拆得比已建的还多
                float clamped = Math.min(amount, progress);

                for(int i = 0; i < requirements.length; i++){
                    int reqamount = requirements[i].amount;
                    accumulator[i] += Math.min(clamped * refund * reqamount, refund * reqamount - totalAccumulator[i]);
                    totalAccumulator[i] = Math.min(totalAccumulator[i] + reqamount * clamped * refund, reqamount);

                    int accumulated = (int)accumulator[i];
                    if(clamped > 0f && accumulated > 0){
                        if(inventory != null){
                            inventory.add(requirements[i].item, accumulated);
                        }
                        accumulator[i] -= accumulated;
                    }
                }
            }

            progress = Mathf.clamp(progress - amount);

            if(progress <= 0f){
                deconstructFinish(tile);
            }
        }

        /**
         * 按"现在该扣多少"和库存情况修正可推进量，并在 remove 时真扣材料。
         * <p>对应原版 {@code BuildEntity.checkRequired}。
         */
        private float checkRequired(ItemModule inventory, float amount, boolean remove){
            float maxProgress = amount;
            if(inventory == null) return maxProgress;

            for(int i = 0; i < cblock.requirements.length; i++){
                int reqamount = cblock.requirements[i].amount;
                int required = (int)accumulator[i];

                if(inventory.get(cblock.requirements[i].item) == 0 && reqamount != 0){
                    //这种材料一个都没有：完全无法推进
                    maxProgress = 0f;
                }else if(required > 0){
                    int maxUse = Math.min(required, inventory.get(cblock.requirements[i].item));
                    float fraction = maxUse / (float)required;

                    maxProgress = Math.min(maxProgress, maxProgress * fraction);
                    accumulator[i] -= maxUse;

                    if(remove){
                        inventory.remove(cblock.requirements[i].item, maxUse);
                    }
                }
            }

            return maxProgress;
        }

        /** 初始化为"建造"状态。 */
        public void setConstruct(Block previous, Block block){
            this.cblock = block;
            this.previous = previous;
            //注意不重置 progress：重算数组（比如中途换了目标方块）不该把已建的部分清零
            this.accumulator = new float[block.requirements.length];
            this.totalAccumulator = new float[block.requirements.length];
            this.buildCost = block.buildCost;
        }

        /** 初始化为"拆除"状态：progress 从 1 往回退，退到 0 就拆掉。 */
        public void setDeconstruct(Block previous){
            if(previous == null) return;
            this.previous = previous;
            this.progress = 1f;
            if(previous.buildCost >= 0.01f){
                this.cblock = previous;
                this.buildCost = previous.buildCost;
            }else{
                //没有配方成本的方块（自然岩壁）给个默认拆除时长
                this.cblock = null;
                this.buildCost = 20f;
            }
            this.accumulator = new float[previous.requirements.length];
            this.totalAccumulator = new float[previous.requirements.length];
        }

        /** @return 本帧的进度推进量（对应原版 {@code 1f / buildCost * delta * buildPower}）。 */
        public float progressStep(float buildPower){
            float cost = buildCost <= 0.01f ? 1f : buildCost;
            return Time.delta() * buildPower / cost;
        }

        @Override
        public void write(java.io.DataOutputStream out) throws java.io.IOException{
            super.write(out);
            out.writeShort(cblock == null ? -1 : Blocks.all.indexOf(cblock, true));
            out.writeShort(previous == null ? -1 : Blocks.all.indexOf(previous, true));
            out.writeFloat(progress);
            out.writeFloat(buildCost);
            //记账数组也要存：否则半途存档再读档会把"已扣过的材料"当成没扣，重复扣一遍
            writeFloats(out, accumulator);
            writeFloats(out, totalAccumulator);
        }

        @Override
        public void read(java.io.DataInputStream in, byte revision) throws java.io.IOException{
            super.read(in, revision);
            int cblockIndex = in.readShort();
            int previousIndex = in.readShort();
            progress = in.readFloat();
            buildCost = in.readFloat();
            cblock = cblockIndex < 0 || cblockIndex >= Blocks.all.size ? null : Blocks.all.get(cblockIndex);
            previous = previousIndex < 0 || previousIndex >= Blocks.all.size ? null : Blocks.all.get(previousIndex);
            accumulator = readFloats(in);
            totalAccumulator = readFloats(in);
        }
    }
}
