package wirelessbelt;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.power.*;

import static mindustry.Vars.*;

/**
 * 大型電池。
 * heats = false (x5)：普通電池，不發熱。
 * heats = true  (x10 以上)：放電時發熱；熱度高時輸出下降，到頂停止輸出；
 *                           輸入水(冷卻 1.5 倍)或冷卻液(3 倍)可加速降溫，且有液體時不發熱。
 */
public class HeatBattery extends Battery{
    /** 原版電池容量 */
    public static final float baseCapacity = 1000f;

    public final boolean heats;
    public final float mult;

    /** 每放出 1 單位電力增加的熱度(熱度範圍 0~1) */
    public float heatPerUnit = 0.0001f;
    /** 每 tick 自然降溫量 */
    public float passiveCool = 0.0004f;
    /** 熱度超過此值開始降低輸出，到 1 時完全停止 */
    public float derateStart = 0.5f;
    /** 過熱停止後，降到此熱度才恢復輸出 */
    public float resumeHeat = 0.7f;
    /** 降溫時每 tick 消耗的液體量 */
    public float liquidUse = 0.08f;
    public float waterCool = 1.5f, cryoCool = 3f;

    public Color emptyColor = Color.valueOf("f8c266");
    public Color fullColor = Color.valueOf("fb9567");
    public Color hotColor = Color.valueOf("ff3b1f");

    public HeatBattery(String name, int size, float mult, boolean heats, ItemStack[] reqs){
        super(name);
        this.size = size;
        this.mult = mult;
        this.heats = heats;

        requirements(Category.power, reqs);
        consumePowerBuffered(baseCapacity * mult);
        baseExplosiveness = Math.max(1f, mult * 0.5f);
        alwaysUnlocked = true;
        shownPlanets.add(Planets.serpulo);

        if(heats){
            update = true;          //Battery 預設不更新，發熱版需要
            noUpdateDisabled = false;
            hasLiquids = true;
            liquidCapacity = 30f;
        }

        buildType = HeatBuild::new;
    }

    @Override
    public void setBars(){
        super.setBars();
        if(heats){
            addBar("heat", (HeatBuild e) -> new Bar(
                () -> Core.bundle.get("bar.wirelessbelt-heat"),
                () -> hotColor,
                () -> e.heat
            ));
        }
    }

    public class HeatBuild extends BatteryBuild{
        public float heat, lastStatus, dutyAcc;
        public boolean overheated;

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return heats
                && (liquid == Liquids.water || liquid == Liquids.cryofluid)
                && (liquids.current() == liquid || liquids.currentAmount() < 0.2f);
        }

        @Override
        public void updateTile(){
            if(!heats) return;

            float cap = block.consPower.capacity;
            //這個 tick 放出的電量(狀態下降 = 放電)
            float discharged = Math.max(0f, lastStatus - power.status) * cap;
            lastStatus = power.status;

            //冷卻：有液體就消耗液體，並且不發熱
            boolean cooled = false;
            float coolMult = 1f;
            Liquid l = liquids.current();
            if(l != null && (heat > 0.001f || discharged > 0f)){
                float use = liquidUse * Time.delta;
                if(liquids.get(l) >= use){
                    liquids.remove(l, use);
                    cooled = true;
                    coolMult = l == Liquids.cryofluid ? cryoCool : waterCool;
                }
            }

            if(!cooled) heat += discharged * heatPerUnit;
            heat -= passiveCool * coolMult * Time.delta;
            heat = Mathf.clamp(heat);

            //過熱停止 / 恢復
            if(heat >= 1f){
                overheated = true;
            }else if(overheated && heat <= resumeHeat){
                overheated = false;
            }

            //輸出比例：熱度越高越低。用「快速開關」模擬降低輸出(關閉時也暫停充電)
            float duty = overheated ? 0f : (heat <= derateStart ? 1f : 1f - (heat - derateStart) / (1f - derateStart));
            dutyAcc += duty;
            if(dutyAcc >= 1f){
                dutyAcc -= 1f;
                enabled = true;
            }else{
                enabled = false;
            }
        }

        @Override
        public void draw(){
            float s = block.size * tilesize;

            Draw.z(Layer.block);
            Draw.color(emptyColor, fullColor, power.status);
            Fill.square(x, y, s / 2f - 1f);

            if(heats && heat > 0.01f){
                Draw.color(hotColor);
                Draw.alpha(heat * (0.35f + (overheated ? 0.25f * Mathf.absin(Time.time, 5f, 1f) : 0f)));
                Fill.square(x, y, s / 2f - 1f);
            }

            Draw.color();
            Draw.rect(block.region, x, y);
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(heat);
            write.bool(overheated);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            heat = read.f();
            overheated = read.bool();
        }
    }
}
