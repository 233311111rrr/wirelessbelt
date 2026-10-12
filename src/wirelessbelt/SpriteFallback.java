package wirelessbelt;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;

/**
 * 後備貼圖：如果圖集裡找不到方塊貼圖(畫面顯示 "oh no")，就在執行時自己畫一份並註冊進圖集。
 * 找得到正常的 png 貼圖時不會做任何事。
 */
public class SpriteFallback{

    static int c(int r, int g, int b){
        return Color.rgba8888(r / 255f, g / 255f, b / 255f, 1f);
    }

    static final int CLEAR = 0;

    static void rect(Pixmap pm, int x0, int y0, int x1, int y1, int col){
        for(int y = Math.max(0, y0); y <= Math.min(pm.height - 1, y1); y++){
            for(int x = Math.max(0, x0); x <= Math.min(pm.width - 1, x1); x++){
                pm.set(x, y, col);
            }
        }
    }

    static void disc(Pixmap pm, int cx, int cy, int rad, int col){
        for(int y = cy - rad; y <= cy + rad; y++){
            for(int x = cx - rad; x <= cx + rad; x++){
                if(x < 0 || y < 0 || x >= pm.width || y >= pm.height) continue;
                if((x - cx) * (x - cx) + (y - cy) * (y - cy) <= rad * rad) pm.set(x, y, col);
            }
        }
    }

    static void register(String name, Pixmap pm){
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.nearest);
        Core.atlas.addRegion(name, new TextureRegion(tex));
    }

    public static void ensure(){
        if(Core.atlas == null) return;

        battery("wirelessbelt-battery-x5", 2, c(80, 220, 130), false);
        battery("wirelessbelt-battery-x10", 3, c(255, 215, 70), true);
        battery("wirelessbelt-battery-x20", 4, c(255, 140, 40), true);
        battery("wirelessbelt-battery-x30", 5, c(255, 70, 70), true);
        drill("wirelessbelt-ultra-drill");
        belt("wirelessbelt-wb-sender", c(255, 157, 58), false);
        belt("wirelessbelt-wb-receiver", c(58, 216, 255), true);
    }

    /** 無線傳送帶 2x2：發送端箭頭朝外(上)，接收端箭頭朝內(下) */
    static void belt(String name, int accent, boolean inward){
        if(Core.atlas.has(name)) return;

        int s = 64, m = s / 2;
        Pixmap pm = new Pixmap(s, s);
        rect(pm, 0, 0, s - 1, s - 1, c(38, 40, 48));
        rect(pm, 2, 2, s - 3, s - 3, c(72, 76, 88));
        rect(pm, 5, 5, s - 6, s - 6, c(46, 48, 58));
        rect(pm, 14, 14, s - 15, s - 15, c(accent >> 24 & 0xff, accent >> 16 & 0xff, accent >> 8 & 0xff));
        int dark = c(30, 32, 40);
        //箭頭：桿 + 三角頭(發送端朝上，接收端朝下)
        if(inward){
            rect(pm, m - 3, m - 12, m + 3, m + 4, dark);
            for(int i = 0; i < 10; i++){
                int y = m + 14 - i;
                rect(pm, m - i, y, m + i, y, dark);
            }
        }else{
            rect(pm, m - 3, m - 4, m + 3, m + 12, dark);
            for(int i = 0; i < 10; i++){
                int y = m - 14 + i;
                rect(pm, m - i, y, m + i, y, dark);
            }
        }
        //邊緣小方塊，代表每格都能輸入/輸出
        int e = c(accent >> 24 & 0xff, accent >> 16 & 0xff, accent >> 8 & 0xff);
        for(int i = 8; i < s - 8; i += 8){
            rect(pm, i, 1, i + 3, 2, e);
            rect(pm, i, s - 3, i + 3, s - 2, e);
            rect(pm, 1, i, 2, i + 3, e);
            rect(pm, s - 3, i, s - 2, i + 3, e);
        }
        register(name, pm);
    }

    static void battery(String name, int size, int tier, boolean heats){
        if(Core.atlas.has(name)) return;

        int s = size * 32, f = 5;
        Pixmap pm = new Pixmap(s, s);
        rect(pm, 0, 0, s - 1, s - 1, c(36, 38, 46));
        rect(pm, 2, 2, s - 3, s - 3, c(78, 82, 96));
        rect(pm, f, f, s - f - 1, s - f - 1, CLEAR);          //中間透明，露出電量顏色
        for(int i = 1; i < size; i++){
            int p = i * 32;
            rect(pm, p - 1, f, p, s - f - 1, c(60, 63, 76));
            rect(pm, f, p - 1, s - f - 1, p, c(60, 63, 76));
        }
        rect(pm, 2, 2, 7, 7, tier);
        rect(pm, s - 8, 2, s - 3, 7, tier);
        rect(pm, 2, s - 8, 7, s - 3, tier);
        rect(pm, s - 8, s - 8, s - 3, s - 3, tier);
        if(heats){
            int mid = s / 2, cy = c(58, 216, 255);
            rect(pm, mid - 3, 0, mid + 3, 3, cy);
            rect(pm, mid - 3, s - 4, mid + 3, s - 1, cy);
            rect(pm, 0, mid - 3, 3, mid + 3, cy);
            rect(pm, s - 4, mid - 3, s - 1, mid + 3, cy);
        }
        register(name, pm);
    }

    static void drill(String name){
        int s = 128, m = s / 2;

        if(!Core.atlas.has(name)){
            Pixmap pm = new Pixmap(s, s);
            rect(pm, 0, 0, s - 1, s - 1, c(34, 36, 44));
            rect(pm, 3, 3, s - 4, s - 4, c(84, 88, 104));
            rect(pm, 9, 9, s - 10, s - 10, c(52, 55, 68));
            disc(pm, m, m, 44, c(26, 28, 36));
            disc(pm, m, m, 38, c(40, 42, 52));
            int or = c(255, 157, 58);
            rect(pm, 5, 5, 12, 12, or);
            rect(pm, s - 13, 5, s - 6, 12, or);
            rect(pm, 5, s - 13, 12, s - 6, or);
            rect(pm, s - 13, s - 13, s - 6, s - 6, or);
            register(name, pm);
        }

        if(!Core.atlas.has(name + "-rotator")){
            Pixmap pm = new Pixmap(s, s);
            int blade = c(190, 196, 210), edge = c(235, 238, 246);
            //四片刀刃(十字形)
            rect(pm, m - 6, m - 48, m + 6, m + 48, blade);
            rect(pm, m - 48, m - 6, m + 48, m + 6, blade);
            rect(pm, m - 2, m - 44, m + 2, m + 44, edge);
            rect(pm, m - 44, m - 2, m + 44, m + 2, edge);
            register(name + "-rotator", pm);
        }

        if(!Core.atlas.has(name + "-top")){
            Pixmap pm = new Pixmap(s, s);
            disc(pm, m, m, 13, c(66, 70, 86));
            disc(pm, m, m, 9, c(255, 157, 58));
            disc(pm, m, m, 4, c(255, 214, 150));
            register(name + "-top", pm);
        }
    }
}
