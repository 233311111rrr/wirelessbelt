package wirelessbelt;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * 無線傳送帶：發送端(sender) 與 接收端(receiver)，連結數量不限(多對多)。
 * 發送端：從任意邊接收物品，符合過濾條件的物品進入 500 容量緩衝，
 *         並輪流無線傳給所有已連結的目標：接收端，或任何能接收該物品的建築(砲塔、核心、容器...)；
 *         不符合的物品直接穿過，由其他方向相鄰的輸送帶輸出。
 * 接收端：收到物品後，從任意邊輸出到相鄰的輸送帶/方塊。
 */
public class WirelessBelt extends Block{
    /** 傳輸距離(格)。原版橋接傳送帶為 4 格，這裡是 80 倍 = 320 格 */
    public float range = 4f * 80f;
    /** 每搬運 1 個物品所需 tick。2.5 約為橋接傳送帶的 2 倍速度(請實測後微調) */
    public float transferTime = 2.5f;

    public final boolean sender;

    public Color senderColor = Color.valueOf("ff9d3a");
    public Color receiverColor = Color.valueOf("3ad8ff");

    public WirelessBelt(String name, boolean sender){
        super(name);
        this.sender = sender;

        size = 2;
        update = true;
        solid = true;
        hasItems = true;
        hasPower = true;
        itemCapacity = 500;
        configurable = true;
        saveConfig = true;
        unloadable = false;
        noUpdateDisabled = true;
        instantTransfer = sender;
        group = BlockGroup.transportation;
        alwaysUnlocked = true;

        //電力 100 單位/秒 (consumePower 以每 tick 為單位)
        consumePower(100f / 60f);

        requirements(Category.distribution, ItemStack.with(
            Items.silicon, 80,
            Items.titanium, 60,
            Items.graphite, 40,
            Items.lead, 60
        ));

        //若在某星球的建造選單看不到，確認此處包含該星球
        shownPlanets.add(Planets.serpulo);

        buildType = WirelessBuild::new;

        //切換連結：已連結則解除，未連結則建立
        config(Integer.class, (WirelessBuild b, Integer pos) -> {
            if(WirelessBelt.this.sender) b.toggleLink(pos);
        });
        //清除全部連結(設定面板的取消按鈕)
        config(Boolean.class, (WirelessBuild b, Boolean v) -> b.clearLinks());
        //設定指定傳送的物品
        config(Item.class, (WirelessBuild b, Item item) -> b.sortItem = item);
        configClear((WirelessBuild b) -> b.sortItem = null);
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.range, range, StatUnit.blocks);
        //每個格子各自計算傳輸速度，總速度 = 單格速度 x 格數
        stats.add(Stat.itemsMoved, 60f / transferTime * size * size, StatUnit.itemsSecond);
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range * tilesize, Pal.accent);
    }

    public class WirelessBuild extends Building{
        /** 已連結的對端位置(發送端存接收端，接收端存發送端)，數量不限 */
        public IntSeq links = new IntSeq();
        /** 發送端：指定傳送的物品，null = 全部傳送 */
        public Item sortItem;

        public float anim, checkTimer;
        /** 每個格子各自的傳輸計時與輸出方向輪替 */
        public float[] tileAcc = new float[WirelessBelt.this.size * WirelessBelt.this.size];
        public int[] tileDump = new int[WirelessBelt.this.size * WirelessBelt.this.size];
        public Seq<Tile> myTiles = new Seq<>();
        public Item lastItem;
        public int passIndex, rr, itemRR;

        public Color color(){
            return sender ? senderColor : receiverColor;
        }

        /** 第 i 個連結對象，無效則回傳 null。不會修改狀態。 */
        public Building linkAt(int i){
            Building b = world.build(links.get(i));
            if(b == null || !b.isValid() || b.team != team) return null;
            if(b instanceof WirelessBuild){
                //無線方塊之間必須是另一種(收/送)
                return b.block == block ? null : b;
            }
            //一般建築(砲塔、核心...)只有發送端可以直接連結
            return sender ? b : null;
        }

        public boolean canLink(Building other){
            if(!sender || other == null || other == this || other.team != team) return false;
            if(other instanceof WirelessBuild){
                if(other.block == block) return false; //發送端不能連發送端
            }else if(!other.block.hasItems){
                return false; //一般建築必須有物品欄(砲塔、核心、容器、工廠...)
            }
            return within(other, range * tilesize);
        }

        /** 已連結則解除，否則建立連結(僅發送端呼叫) */
        public void toggleLink(int p){
            Building nb = world.build(p);
            if(links.contains(p)){
                links.removeValue(p);
                if(nb instanceof WirelessBuild) ((WirelessBuild)nb).links.removeValue(pos());
                //解除連結：紅色閃光
                if(nb != null) flash(nb, Pal.remove);
            }else if(canLink(nb)){
                links.add(p);
                //接收端會記住發送端；一般建築不需要
                if(nb instanceof WirelessBuild){
                    WirelessBuild r = (WirelessBuild)nb;
                    if(!r.links.contains(pos())) r.links.add(pos());
                }
                //建立連結：發送端顏色閃光，方便確認有點到
                flash(nb, senderColor);
            }
        }

        /** 清除這個方塊的所有連結(同時從對端移除) */
        public void clearLinks(){
            for(int i = 0; i < links.size; i++){
                Building b = world.build(links.get(i));
                if(b instanceof WirelessBuild) ((WirelessBuild)b).links.removeValue(pos());
                if(b != null) flash(b, Pal.remove);
            }
            links.clear();
        }

        /** 目標閃一下光；特效出任何問題都不能影響連結本身 */
        void flash(Building b, Color c){
            try{
                Fx.healBlockFull.at(b.x, b.y, 0f, c, b.block);
            }catch(Throwable ignored){}
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            if(other == this) return true;
            if(sender){
                if(links.contains(other.pos()) || canLink(other)){
                    configure(other.pos());
                    return false;
                }
            }else if(other instanceof WirelessBuild){
                WirelessBuild s = (WirelessBuild)other;
                if(s.links.contains(pos()) || s.canLink(this)){
                    //選到接收端後點發送端，也可以切換連結
                    s.configure(pos());
                    return false;
                }
            }
            return true;
        }

        @Override
        public void onRemoved(){
            super.onRemoved();
            for(int i = 0; i < links.size; i++){
                Building b = world.build(links.get(i));
                if(b instanceof WirelessBuild) ((WirelessBuild)b).links.removeValue(pos());
            }
            links.clear();
        }

        // ---------- 物品進出 ----------

        boolean buffers(Item item){
            return (sortItem == null || item == sortItem) && links.size > 0;
        }

        /** 不符合條件的物品：找出其他方向可接收的相鄰方塊(輪流分配) */
        Building passTarget(Item item, Building source, boolean advance){
            int n = proximity.size;
            if(n == 0) return null;
            for(int i = 0; i < n; i++){
                int idx = (passIndex + i) % n;
                Building b = proximity.get(idx);
                if(b == source || b.team != team || b.block instanceof WirelessBelt) continue;
                if(source.block.instantTransfer && b.block.instantTransfer) continue;
                if(b.acceptItem(this, item)){
                    if(advance) passIndex = (idx + 1) % n;
                    return b;
                }
            }
            return null;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(!sender || source.team != team) return false;
            if(buffers(item)) return items.total() < itemCapacity;
            return passTarget(item, source, false) != null;
        }

        @Override
        public void handleItem(Building source, Item item){
            if(!sender || buffers(item)){
                items.add(item, 1);
                return;
            }
            Building t = passTarget(item, source, true);
            if(t != null) t.handleItem(this, item);
        }

        /** 輪流挑選下一個能接收這個物品的目標(接收端未滿，或一般建築接受該物品) */
        Building findTarget(Item item){
            int n = links.size;
            for(int i = 0; i < n; i++){
                int idx = (rr + i) % n;
                Building b = linkAt(idx);
                if(b == null) continue;
                boolean ok;
                if(b instanceof WirelessBuild){
                    ok = b.items.total() < itemCapacity;
                }else{
                    ok = b.acceptItem(this, item);
                }
                if(ok){
                    rr = (idx + 1) % n;
                    return b;
                }
            }
            return null;
        }

        /** 嘗試送出一個物品，成功回傳 true。依序檢查緩衝裡的每種物品(砲塔可能只吃其中幾種) */
        boolean sendOne(){
            int total = content.items().size;
            for(int i = 0; i < total; i++){
                Item it = content.item((itemRR + i) % total);
                if(!items.has(it)) continue;
                Building t = findTarget(it);
                if(t == null) continue;
                items.remove(it, 1);
                t.handleItem(this, it);
                lastItem = it;
                itemRR = (it.id + 1) % total;
                return true;
            }
            return false;
        }

        // ---------- 更新 ----------

        /** 從單一格子往它相鄰的建築輸出一個物品(每個格子獨立) */
        boolean dumpFromTile(Tile t, int ti){
            int total = content.items().size;
            for(int d = 0; d < 4; d++){
                int di = (tileDump[ti] + d) % 4;
                Tile n = t.nearby(di);
                if(n == null) continue;
                Building b = n.build;
                if(b == null || b == this || b.team != team) continue;
                for(int k = 0; k < total; k++){
                    Item it = content.item((itemRR + k) % total);
                    if(!items.has(it) || !canDump(b, it) || !b.acceptItem(this, it)) continue;
                    b.handleItem(this, it);
                    items.remove(it, 1);
                    tileDump[ti] = (di + 1) % 4;
                    itemRR = (it.id + 1) % total;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void updateTile(){
            //定期清除失效連結
            checkTimer += Time.delta;
            if(checkTimer > 60f){
                checkTimer = 0f;
                for(int i = links.size - 1; i >= 0; i--){
                    if(linkAt(i) == null) links.removeIndex(i);
                }
            }

            //每個格子都是獨立的傳輸通道，各自計時，不共用速度
            if(sender){
                if(links.size > 0 && efficiency > 0f && items.total() > 0){
                    for(int i = 0; i < tileAcc.length; i++){
                        tileAcc[i] += edelta();
                        int guard = 0;
                        while(tileAcc[i] >= transferTime && items.total() > 0 && guard++ < 4){
                            if(!sendOne()){
                                tileAcc[i] = Math.min(tileAcc[i], transferTime);
                                break;
                            }
                            anim = 1f;
                            tileAcc[i] -= transferTime;
                        }
                    }
                }else{
                    for(int i = 0; i < tileAcc.length; i++) tileAcc[i] = Math.min(tileAcc[i], transferTime);
                }
            }else{
                if(myTiles.size == 0) tile.getLinkedTilesAs(block, myTiles);

                if(items.total() > 0 && efficiency > 0f){
                    for(int i = 0; i < tileAcc.length && i < myTiles.size; i++){
                        tileAcc[i] += edelta();
                        int guard = 0;
                        while(tileAcc[i] >= transferTime && items.total() > 0 && guard++ < 4){
                            if(dumpFromTile(myTiles.get(i), i)){
                                tileAcc[i] -= transferTime;
                                anim = 1f;
                            }else{
                                tileAcc[i] = Math.min(tileAcc[i], transferTime);
                                break;
                            }
                        }
                    }
                }else{
                    for(int i = 0; i < tileAcc.length; i++) tileAcc[i] = Math.min(tileAcc[i], transferTime);
                }
            }

            anim = Math.max(0f, anim - Time.delta * 0.03f);
        }

        // ---------- 繪製 / 動畫 ----------

        @Override
        public void draw(){
            super.draw();

            if(anim < 0.01f) return;

            //傳輸中：本體發光
            Draw.z(Layer.block + 0.1f);
            Draw.color(color());
            Draw.alpha(anim * 0.25f * (0.7f + Mathf.absin(Time.time, 6f, 0.3f)));
            Fill.square(x, y, block.size * tilesize / 2f - 2f);

            //傳輸動畫只由發送端畫，連線到每一個接收端
            if(sender){
                for(int k = 0; k < links.size; k++){
                    Building r = linkAt(k);
                    if(r == null) continue;

                    float dx = r.x - x, dy = r.y - y;
                    float len = Mathf.len(dx, dy);
                    int dots = Math.max(3, (int)(len / 14f));

                    Draw.z(Layer.power);
                    Lines.stroke(1.5f);
                    Draw.color(color(), Color.white, 0.4f);
                    Draw.alpha(anim * 0.35f);
                    Lines.line(x, y, r.x, r.y);

                    for(int i = 0; i < dots; i++){
                        float f = (i / (float)dots + Time.time * 0.02f) % 1f;
                        float px = x + dx * f, py = y + dy * f;
                        Draw.color(color(), Color.white, 0.5f);
                        Draw.alpha(anim);
                        Fill.circle(px, py, 1.3f + Mathf.absin(Time.time + i * 5f, 4f, 0.8f));
                        if(lastItem != null && i % 3 == 0){
                            Draw.color();
                            Draw.alpha(anim);
                            Draw.rect(lastItem.fullIcon, px, py, 4f, 4f);
                        }
                    }
                }
            }
            Draw.reset();
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            drawLinks();
        }

        /** 點選(設定)狀態下顯示：自己與每個已連結目標的圈圈與箭頭 */
        @Override
        public void drawConfigure(){
            drawLinks();
        }

        void drawLinks(){
            float sin = Mathf.absin(Time.time, 6f, 1f);
            Drawf.dashCircle(x, y, range * tilesize, color());
            Drawf.circles(x, y, block.size * tilesize / 2f + 1f + sin - 2f, color());
            for(int i = 0; i < links.size; i++){
                Building p = linkAt(i);
                if(p == null) continue;
                Color pc = p instanceof WirelessBuild ? ((WirelessBuild)p).color() : senderColor;
                Drawf.circles(p.x, p.y, p.block.size * tilesize / 2f + 1f + sin - 2f, pc);
                Drawf.arrow(sender ? x : p.x, sender ? y : p.y, sender ? p.x : x, sender ? p.y : y,
                    block.size * tilesize / 2f + sin, 4f + sin, color());
            }
        }

        // ---------- UI ----------

        @Override
        public void buildConfiguration(Table table){
            if(sender){
                ItemSelection.buildTable(WirelessBelt.this, table, content.items(), () -> sortItem, this::configure);
                table.row();
            }
            //取消全部連結：不管點選邏輯怎麼樣，都能保證解除
            table.button(Icon.cancel, Styles.cleari, () -> configure(Boolean.TRUE)).size(40f);
        }

        @Override
        public Object config(){
            return sortItem;
        }

        // ---------- 存檔 ----------

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(links.size);
            for(int i = 0; i < links.size; i++) write.i(links.get(i));
            write.s(sortItem == null ? -1 : sortItem.id);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            links.clear();
            if(revision >= 1){
                int n = read.i();
                for(int i = 0; i < n; i++) links.add(read.i());
            }else{
                //舊版存檔：link / from 各一個
                int a = read.i();
                int b = read.i();
                if(a != -1) links.add(a);
                if(b != -1 && b != a) links.add(b);
            }
            int id = read.s();
            sortItem = id == -1 ? null : content.item(id);
        }
    }
}
