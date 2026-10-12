package wirelessbelt;

import mindustry.content.*;
import mindustry.mod.*;
import mindustry.type.*;

public class WirelessBeltMod extends Mod{

    @Override
    public void loadContent(){
        //找不到貼圖時自己畫一份，避免顯示 "oh no"
        SpriteFallback.ensure();

        //容量 = 原版電池(1000) x 倍率；尺寸取最接近倍率面積的整數邊長
        new HeatBattery("battery-x5", 2, 5f, false, ItemStack.with(
            Items.copper, 25, Items.lead, 250, Items.silicon, 40
        ));
        new HeatBattery("battery-x10", 3, 10f, true, ItemStack.with(
            Items.lead, 500, Items.silicon, 120, Items.titanium, 80, Items.metaglass, 40
        ));
        new HeatBattery("battery-x20", 4, 20f, true, ItemStack.with(
            Items.lead, 1000, Items.silicon, 300, Items.titanium, 200, Items.metaglass, 100, Items.plastanium, 40
        ));
        new HeatBattery("battery-x30", 5, 30f, true, ItemStack.with(
            Items.lead, 1600, Items.silicon, 500, Items.titanium, 400, Items.metaglass, 200, Items.plastanium, 100, Items.surgeAlloy, 60
        ));

        new UltraDrill("ultra-drill");

        //無線傳送帶：發送端 / 接收端
        new WirelessBelt("wb-sender", true);
        new WirelessBelt("wb-receiver", false);
    }
}
