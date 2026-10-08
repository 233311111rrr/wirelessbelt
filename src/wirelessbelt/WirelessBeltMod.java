package wirelessbelt;

import mindustry.mod.*;

public class WirelessBeltMod extends Mod{

    @Override
    public void loadContent(){
        //內部名稱會自動加上 "wirelessbelt-" 前綴
        new WirelessBelt("wb-sender", true);
        new WirelessBelt("wb-receiver", false);
    }
}
