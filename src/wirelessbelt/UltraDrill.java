package wirelessbelt;

import mindustry.content.*;
import mindustry.type.*;
import mindustry.world.blocks.production.*;

/**
 * 超高速鑽頭。以原版爆破鑽頭(Blast Drill: 4x4, drillTime 280, 耗電 3/tick)為基準：
 *  - 速度 5 倍，耗電 20 倍
 *  - 輸入水：速度再 x2，耗電再 x1.5
 */
public class UltraDrill extends Drill{
    public static final float basePower = 3f * 20f;   //每 tick，= 3600 電力/秒
    public static final float waterPowerMult = 1.5f;
    public static final float waterSpeedMult = 2f;

    public UltraDrill(String name){
        super(name);

        requirements(Category.production, ItemStack.with(
            Items.silicon, 400,
            Items.titanium, 300,
            Items.thorium, 250,
            Items.plastanium, 120,
            Items.surgeAlloy, 80
        ));

        size = 4;
        tier = 5;
        //速度 5 倍：基礎時間與「硬度加成」一起縮小 5 倍，所有礦物都剛好 5 倍
        drillTime = 280f / 5f;
        hardnessDrillMultiplier = 50f / 5f;
        itemCapacity = 30;
        alwaysUnlocked = true;
        shownPlanets.add(Planets.serpulo);

        //水加速：原版的加速倍率是 liquidBoostIntensity 的平方
        liquidBoostIntensity = (float)Math.sqrt(waterSpeedMult);
        consumeLiquid(Liquids.water, 0.2f).boost();

        //耗電：有水時再 x1.5
        consumePowerDynamic((Drill.DrillBuild b) ->
            basePower * (b.optionalEfficiency > 0.01f ? waterPowerMult : 1f));
    }
}
