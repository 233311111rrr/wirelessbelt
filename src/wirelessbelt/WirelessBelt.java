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
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

/**
 * 無線傳送帶：發送端(sender) 與 接收端(receiver)，連結數量不限(多對多)。
 * 發送端：從任意邊接收物品，符合過濾條件的物品進入 500 容量緩衝，
 *         並輪流無線傳給所有已連結且未滿的接收端；
 *         不符合的物品直接穿過，由其他方向相鄰的輸送帶輸出。
 * 接收端：收到物品後，從任意邊輸出到相鄰的輸送帶/方塊。
 */
public class WirelessBelt extends Block{
    /** 傳輸距離(格)。原版橋接傳送帶為 4 格，這裡是 80 倍 = 320 格 */
    public float range = 4f * 80f;
    /** 每搬運 1 個物品所需 tick。2.5 約為橋接傳送帶的 2 倍速度(請實測後微調) */
    public float transferTime = 1f;

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
        //設定指定傳送的物品
        config(Item.class, (WirelessBuild b, Item item) -> b.sortItem = item);
        configClear((WirelessBuild b) -> b.sortItem = null);
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.range, range, StatUnit.blocks);
        stats.add(Stat.itemsMoved, 60f / transferTime, StatUnit.itemsSecond);
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

        public float moveAcc, anim, checkTimer;
        public Item lastItem;
        public int passIndex, rr;

        public Color color(){
            return sender ? senderColor : receiverColor;
        }

        /** 第 i 個連結對象，無效則回傳 null。不會修改狀態。 */
        public WirelessBuild linkAt(int i){
            Building b = world.build(links.get(i));
            if(!(b instanceof WirelessBuild) || !b.isValid() || b.team != team) return null;
            WirelessBuild w = (WirelessBuild)b;
            if(w.block == block) return null; //必須是另一種(收/送)
            return w;
        }

        public boolean canLink(Building other){
            if(!sender || other == null || other == this) return false;
            if(!(other instanceof WirelessBuild) || other.team != team) return false;
            WirelessBuild r = (WirelessBuild)other;
            if(r.block == block) return false;
            return within(other, range * tilesize);
        }

        /** 已連結則解除，否則建立連結(僅發送端呼叫) */
        public void toggleLink(int p){
            Building nb = world.build(p);
            if(links.contains(p)){
                links.removeValue(p);
                if(nb instanceof WirelessBuild) ((WirelessBuild)nb).links.removeValue(pos());
            }else if(canLink(nb)){
                links.add(p);
                WirelessBuild r = (WirelessBuild)nb;
                if(!r.links.contains(pos())) r.links.add(pos());
            }
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

        /** 輪流挑選下一個還有空間的接收端 */
        WirelessBuild nextReceiver(){
            int n = links.size;
            for(int i = 0; i < n; i++){
                int idx = (rr + i) % n;
                WirelessBuild r = linkAt(idx);
                if(r != null && r.items.total() < itemCapacity){
                    rr = (idx + 1) % n;
                    return r;
                }
            }
            return null;
        }

        // ---------- 更新 ----------

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

            if(sender){
                if(links.size > 0 && efficiency > 0f && items.total() > 0){
                    moveAcc += edelta();
                    int guard = 0;
                    while(moveAcc >= transferTime && items.total() > 0 && guard++ < 8){
                        WirelessBuild r = nextReceiver();
                        if(r == null){
                            moveAcc = Math.min(moveAcc, transferTime);
                            break;
                        }
                        Item it = items.take();
                        if(it == null) break;
                        r.handleItem(this, it);
                        lastItem = it;
                        anim = 1f;
                        moveAcc -= transferTime;
                    }
                }else{
                    moveAcc = Math.min(moveAcc, transferTime);
                }
                anim = Math.max(0f, anim - Time.delta * 0.03f);
            }else{
                if(items.total() > 0 && efficiency > 0f){
                    moveAcc += edelta();
                    int guard = 0;
                    while(moveAcc >= transferTime && items.total() > 0 && guard++ < 8){
                        if(dump()){
                            moveAcc -= transferTime;
                            anim = 1f;
                        }else{
                            moveAcc = Math.min(moveAcc, transferTime);
                            break;
                        }
                    }
                }else{
                    moveAcc = Math.min(moveAcc, transferTime);
                }
                anim = Math.max(0f, anim - Time.delta * 0.03f);
            }
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
                    WirelessBuild r = linkAt(k);
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
            float sin = Mathf.absin(Time.time, 6f, 1f);
            Drawf.dashCircle(x, y, range * tilesize, color());
            Drawf.circles(x, y, block.size * tilesize / 2f + 1f + sin - 2f, color());
            for(int i = 0; i < links.size; i++){
                WirelessBuild p = linkAt(i);
                if(p == null) continue;
                Drawf.circles(p.x, p.y, block.size * tilesize / 2f + 1f + sin - 2f, p.color());
                Drawf.arrow(sender ? x : p.x, sender ? y : p.y, sender ? p.x : x, sender ? p.y : y,
                    block.size * tilesize / 2f + sin, 4f + sin, color());
            }
        }

        // ---------- UI ----------

        @Override
        public void buildConfiguration(Table table){
            if(sender){
                ItemSelection.buildTable(WirelessBelt.this, table, content.items(), () -> sortItem, this::configure);
            }
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
