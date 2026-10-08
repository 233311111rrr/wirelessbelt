package wirelessbelt;

import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
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
 * 無線傳送帶：發送端(sender) 與 接收端(receiver) 一對一綁定。
 * 發送端：從任意邊接收物品，符合過濾條件的物品進入 500 容量緩衝並無線傳給接收端；
 *         不符合的物品直接穿過，由其他方向相鄰的輸送帶輸出。
 * 接收端：收到物品後，從任意邊輸出到相鄰的輸送帶/方塊。
 */
public class WirelessBelt extends Block{
    /** 傳輸距離(格)。原版橋接傳送帶為 4 格，5 倍 = 20 格 */
    public float range = 4f * 5f;
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

        //設定發送端連結的接收端座標(-1 = 解除)
        config(Integer.class, (WirelessBuild b, Integer pos) -> {
            if(WirelessBelt.this.sender) b.setLink(pos);
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
        /** 發送端：已綁定接收端的位置，-1 為未綁定 */
        public int link = -1;
        /** 接收端：已綁定發送端的位置，-1 為未綁定 */
        public int from = -1;
        /** 發送端：指定傳送的物品，null = 全部傳送 */
        public Item sortItem;

        public float moveAcc, anim;
        public Item lastItem;
        public int passIndex;

        public Color color(){
            return sender ? senderColor : receiverColor;
        }

        /** 目前有效的對端(發送端回傳接收端，接收端回傳發送端)，無效則回傳 null。不會修改狀態。 */
        public WirelessBuild partner(){
            int p = sender ? link : from;
            if(p == -1) return null;
            Building b = world.build(p);
            if(!(b instanceof WirelessBuild) || !b.isValid() || b.team != team) return null;
            WirelessBuild w = (WirelessBuild)b;
            if(w.block == block) return null; //必須是另一種(收/送)
            if(sender ? w.from != pos() : w.link != pos()) return null;
            return w;
        }

        public boolean canLink(Building other){
            if(!sender || other == null || other == this) return false;
            if(!(other instanceof WirelessBuild) || other.team != team) return false;
            WirelessBuild r = (WirelessBuild)other;
            if(r.block == block) return false;
            if(!within(other, range * tilesize)) return false;
            //一個接收端只能被一個發送端佔用
            if(r.from != -1 && r.from != pos()){
                Building s = world.build(r.from);
                if(s instanceof WirelessBuild && s.isValid() && ((WirelessBuild)s).link == r.pos()) return false;
            }
            return true;
        }

        public void setLink(int newPos){
            //解除舊連結
            if(link != -1){
                Building old = world.build(link);
                if(old instanceof WirelessBuild && ((WirelessBuild)old).from == pos()){
                    ((WirelessBuild)old).from = -1;
                }
                link = -1;
            }
            if(newPos == -1) return;
            Building nb = world.build(newPos);
            if(canLink(nb)){
                link = newPos;
                ((WirelessBuild)nb).from = pos();
            }
        }

        @Override
        public boolean onConfigureBuildTapped(Building other){
            if(sender){
                if(link == other.pos()){
                    configure(-1);
                    return false;
                }
                if(canLink(other)){
                    configure(other.pos());
                    return false;
                }
            }else if(other instanceof WirelessBuild && ((WirelessBuild)other).canLink(this)){
                //選到接收端後點發送端，也可以建立連結
                other.configure(pos());
                return false;
            }
            return true;
        }

        @Override
        public void onRemoved(){
            super.onRemoved();
            if(sender){
                if(link != -1) setLink(-1);
            }else if(from != -1){
                Building s = world.build(from);
                if(s instanceof WirelessBuild && ((WirelessBuild)s).link == pos()){
                    ((WirelessBuild)s).link = -1;
                }
            }
        }

        // ---------- 物品進出 ----------

        boolean buffers(Item item){
            return (sortItem == null || item == sortItem) && partner() != null;
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

        // ---------- 更新 ----------

        @Override
        public void updateTile(){
            WirelessBuild p = partner();
            if(p == null){
                //清除失效連結
                if(sender && link != -1) link = -1;
                if(!sender && from != -1) from = -1;
            }

            if(sender){
                if(p != null && efficiency > 0f && items.total() > 0){
                    moveAcc += edelta();
                    int guard = 0;
                    while(moveAcc >= transferTime && items.total() > 0 && guard++ < 8){
                        if(p.items.total() >= itemCapacity){
                            moveAcc = Math.min(moveAcc, transferTime);
                            break;
                        }
                        Item it = items.take();
                        if(it == null) break;
                        p.handleItem(this, it);
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

            //傳輸動畫只由發送端畫一次
            if(sender){
                WirelessBuild r = partner();
                if(r != null){
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
            WirelessBuild p = partner();
            if(p != null){
                Drawf.circles(p.x, p.y, block.size * tilesize / 2f + 1f + sin - 2f, p.color());
                Drawf.arrow(sender ? x : p.x, sender ? y : p.y, sender ? p.x : x, sender ? p.y : y,
                    block.size * tilesize / 2f + sin, 4f + sin, color());
            }
        }

        // ---------- UI ----------

        @Override
        public void buildConfiguration(Table table){
            if(sender){
                ItemSelection.buildTable(table, content.items(), () -> sortItem, this::configure);
            }
        }

        @Override
        public Object config(){
            return sortItem;
        }

        // ---------- 存檔 ----------

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(link);
            write.i(from);
            write.s(sortItem == null ? -1 : sortItem.id);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            link = read.i();
            from = read.i();
            int id = read.s();
            sortItem = id == -1 ? null : content.item(id);
        }
    }
}
