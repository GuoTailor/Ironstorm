package com.phoenix.game.content;

import com.phoenix.game.math.Mathf;
import com.phoenix.game.struct.Array;
import com.phoenix.game.type.ItemStack;
import com.phoenix.game.world.Block;

import static com.phoenix.game.content.Blocks.*;

/**
 * 科技树（方块解锁关系）。参照 Mindustry mindustry.content.TechTree 移植。
 * <p>结构：{@link TechNode} 是一棵树的节点（方块 + 建造需求 + 子节点），{@link #root} 是根，
 * {@link #all} 是全部节点的平铺列表。节点构造时用 {@code context} 串起父子关系。
 * <p>建造需求由方块自身的 {@code requirements} 按 {@code 40 + round(pow(amount, 1.25) * 6, 10)} 放大而来。
 *
 * <p><b>与本项目的差异（逐条对应原版写法）</b>：
 * <ul>
 *   <li><b>字段改名</b>：原版 {@code coreShard} → 本项目 {@code Blocks.core}（内部名同为 core-shard）；
 *       {@code container} → {@code Blocks.storage}（内部名同为 container）；
 *       {@code mechanicalDrill} → {@code Blocks.copperDrill}（见 {@code 转换.md}）；
 *       {@code siliconSmelter} → {@code Blocks.siliconCrafter}（内部名同为 silicon-smelter）；
 *       {@code dartPad}/{@code deltaPad}/{@code tauPad}/{@code omegaPad} →
 *       {@code Blocks.mechPad}/{@code mechPadDelta}/{@code mechPadTau}/{@code mechPadOmega}。</li>
 *   <li><b>此前标为"未移植"的方块（T4 核实后全部早已在 {@code Blocks} 注册，节点已照原版补回）</b>：
 *       {@code message}、{@code wave}、{@code leadDrill}（原版 {@code pneumaticDrill}，内部名同为
 *       pneumatic-drill）、{@code cultivator}、{@code waterExtractor}、{@code oilExtractor}、
 *       {@code sporePress}、{@code coalCentrifuge}、{@code incinerator}、{@code melter}、
 *       {@code separator}、{@code cryofluidMixer}、{@code mechanicalPump} 及整条液体管道子树
 *       （conduit/liquidJunction/liquidRouter/liquidTank/bridgeConduit/pulseConduit/phaseConduit/
 *       platedConduit/rotaryPump/thermalPump）、{@code powerNodeLarge}、{@code turbineGenerator}、
 *       {@code differentialGenerator}、{@code draugFactory}/{@code spiritFactory}/{@code phantomFactory}/
 *       {@code revenantFactory}、{@code javelinPad}/{@code tridentPad}/{@code glaivePad}。</li>
 *   <li>{@code Mathf.round(value, step)}（原版四舍五入到 step 的整数倍）：T4 已补进 {@code Mathf}，
 *       本类直接调用。</li>
 * </ul>
 */
public class TechTree implements com.phoenix.game.ctype.ContentList{
    public static Array<TechNode> all;
    public static TechNode root;

    @Override
    public void load(){
        TechNode.context = null;
        all = new Array<>();

        //原版是 node(coreShard, ...)；本项目该字段叫 Blocks.core（内部名同为 core-shard）
        root = node(core, () -> {

            node(conveyor, () -> {

                node(junction, () -> {
                    node(router, () -> {
                        node(launchPad, () -> {
                            node(launchPadLarge, () -> {

                            });
                        });

                        node(distributor);
                        node(sorter, () -> {
                            node(invertedSorter);
                            node(message);
                            node(overflowGate, () -> {
                                node(underflowGate);
                            });
                        });
                        //原版是 node(container, ...)；本项目该字段叫 Blocks.storage（内部名同为 container）
                        node(storage, () -> {
                            node(unloader);
                            node(vault, () -> {

                            });
                        });

                        node(itemBridge, () -> {
                            node(titaniumConveyor, () -> {
                                node(phaseConveyor, () -> {
                                    node(massDriver, () -> {

                                    });
                                });

                                node(armoredConveyor, () -> {

                                });
                            });
                        });
                    });
                });
            });

            node(duo, () -> {
                node(scatter, () -> {
                    node(hail, () -> {

                        node(salvo, () -> {
                            node(swarmer, () -> {
                                node(cyclone, () -> {
                                    node(spectre, () -> {

                                    });
                                });
                            });

                            node(ripple, () -> {
                                node(fuse, () -> {

                                });
                            });
                        });
                    });
                });

                node(scorch, () -> {
                    node(arc, () -> {
                        node(wave, () -> {

                        });

                        node(lancer, () -> {
                            node(meltdown, () -> {

                            });

                            node(shockMine, () -> {

                            });
                        });
                    });
                });


                node(copperWall, () -> {
                    node(copperWallLarge, () -> {
                        node(titaniumWall, () -> {
                            node(titaniumWallLarge);

                            node(door, () -> {
                                node(doorLarge);
                            });
                            node(plastaniumWall, () -> {
                                node(plastaniumWallLarge, () -> {

                                });
                            });
                            node(thoriumWall, () -> {
                                node(thoriumWallLarge);
                                node(surgeWall, () -> {
                                    node(surgeWallLarge);
                                    node(phaseWall, () -> {
                                        node(phaseWallLarge);
                                    });
                                });
                            });
                        });
                    });
                });
            });

            //原版是 node(mechanicalDrill, ...)；本项目该字段叫 Blocks.copperDrill
            node(copperDrill, () -> {
                node(graphitePress, () -> {
                    //原版是 node(pneumaticDrill, ...)；本项目该字段叫 Blocks.leadDrill（内部名同为 pneumatic-drill）
                    node(leadDrill, () -> {
                        node(cultivator, () -> {

                        });

                        node(laserDrill, () -> {
                            node(blastDrill, () -> {

                            });

                            node(waterExtractor, () -> {
                                node(oilExtractor, () -> {

                                });
                            });
                        });
                    });

                    node(pyratiteMixer, () -> {
                        node(blastMixer, () -> {

                        });
                    });

                    //原版是 node(siliconSmelter, ...)；本项目该字段叫 Blocks.siliconCrafter
                    node(siliconCrafter, () -> {

                        node(sporePress, () -> {
                            node(coalCentrifuge, () -> {

                            });
                            node(multiPress, () -> {

                            });

                            node(plastaniumCompressor, () -> {
                                node(phaseWeaver, () -> {

                                });
                            });
                        });

                        node(kiln, () -> {
                            node(incinerator, () -> {
                                node(melter, () -> {
                                    node(surgeSmelter, () -> {

                                    });

                                    node(separator, () -> {
                                        node(pulverizer, () -> {

                                        });
                                    });

                                    node(cryofluidMixer, () -> {

                                    });
                                });
                            });
                        });
                    });
                });

                node(mechanicalPump, () -> {
                    node(conduit, () -> {
                        node(liquidJunction, () -> {
                            node(liquidRouter, () -> {
                                node(liquidTank);

                                node(bridgeConduit);

                                node(pulseConduit, () -> {
                                    node(phaseConduit, () -> {

                                    });

                                    node(platedConduit, () -> {

                                    });
                                });

                                node(rotaryPump, () -> {
                                    node(thermalPump, () -> {

                                    });
                                });
                            });
                        });
                    });
                });

                node(combustionGenerator, () -> {
                    node(powerNode, () -> {
                        node(powerNodeLarge, () -> {
                            node(diode, () -> {
                                node(surgeTower, () -> {

                                });
                            });
                        });

                        node(battery, () -> {
                            node(batteryLarge, () -> {

                            });
                        });

                        node(mender, () -> {
                            node(mendProjector, () -> {
                                node(forceProjector, () -> {
                                    node(overdriveProjector, () -> {

                                    });
                                });

                                node(repairPoint, () -> {

                                });
                            });
                        });

                        node(turbineGenerator, () -> {
                            node(thermalGenerator, () -> {
                                node(differentialGenerator, () -> {
                                    node(thoriumReactor, () -> {
                                        node(impactReactor, () -> {

                                        });

                                        node(rtgGenerator, () -> {

                                        });
                                    });
                                });
                            });
                        });

                        node(solarPanel, () -> {
                            node(largeSolarPanel, () -> {

                            });
                        });
                    });

                    node(draugFactory, () -> {
                        node(spiritFactory, () -> {
                            node(phantomFactory);
                        });

                        node(daggerFactory, () -> {
                            node(commandCenter, () -> {});
                            node(crawlerFactory, () -> {
                                node(titanFactory, () -> {
                                    node(fortressFactory, () -> {

                                    });
                                });
                            });

                            node(wraithFactory, () -> {
                                node(ghoulFactory, () -> {
                                    node(revenantFactory, () -> {

                                    });
                                });
                            });
                        });
                    });

                    //原版是 node(dartPad/deltaPad/tauPad/omegaPad, ...)；本项目机甲平台字段名不同
                    //（dartPad → Blocks.mechPad、deltaPad → mechPadDelta、tauPad → mechPadTau、omegaPad → mechPadOmega）
                    node(mechPad, () -> {
                        node(mechPadDelta, () -> {

                            node(javelinPad, () -> {
                                node(tridentPad, () -> {
                                    node(glaivePad);
                                });
                            });

                            node(mechPadTau, () -> {
                                node(mechPadOmega, () -> {

                                });
                            });
                        });
                    });
                });
            });
        });
    }

    private static TechNode node(Block block, Runnable children){
        ItemStack[] requirements = new ItemStack[block.requirements.length];
        for(int i = 0; i < requirements.length; i++){
            requirements[i] = new ItemStack(block.requirements[i].item, 40 + Mathf.round(Mathf.pow(block.requirements[i].amount, 1.25f) * 6, 10));
        }

        return new TechNode(block, requirements, children);
    }

    private static TechNode node(Block block){
        return node(block, () -> {});
    }

    public static TechNode create(Block parent, Block block){
        TechNode.context = all.find(t -> t.block == parent);
        return node(block, () -> {});
    }

    public static class TechNode{
        static TechNode context;

        public final Block block;
        public final ItemStack[] requirements;
        public final Array<TechNode> children = new Array<>();

        TechNode(TechNode ccontext, Block block, ItemStack[] requirements, Runnable children){
            if(ccontext != null){
                ccontext.children.add(this);
            }

            this.block = block;
            this.requirements = requirements;

            context = this;
            children.run();
            context = ccontext;
            all.add(this);
        }

        TechNode(Block block, ItemStack[] requirements, Runnable children){
            this(context, block, requirements, children);
        }
    }
}
