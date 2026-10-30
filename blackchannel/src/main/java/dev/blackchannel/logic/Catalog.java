package dev.blackchannel.logic;

import dev.blackchannel.registry.ModContent;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Supplier;

public final class Catalog {
    public record Entry(String id, Supplier<Item> item, int price, int count) {}

    private Catalog() {}

    public static List<Entry> entries() {
        return List.of(
                new Entry("cell", ModContent.CELL::get, 6, 1),
                new Entry("tracker", ModContent.TRACKER::get, 8, 2),
                new Entry("scanner", ModContent.SCANNER::get, 14, 1),
                new Entry("sim", ModContent.SIM::get, 8, 1),
                new Entry("exploit", ModContent.EXPLOIT::get, 12, 1),
                new Entry("contract", ModContent.CONTRACT::get, 16, 1),
                new Entry("vpn", ModContent.VPN::get, 28, 1),
                new Entry("ids", ModContent.IDS::get, 24, 1),
                new Entry("harden", ModContent.HARDEN::get, 22, 1),
                new Entry("decoy", ModContent.DECOY_ITEM::get, 12, 1),
                new Entry("faraday", ModContent.FARADAY_ITEM::get, 26, 1),
                new Entry("jammer", ModContent.JAMMER_ITEM::get, 30, 1),
                new Entry("sat", ModContent.SAT::get, 36, 1),
                new Entry("zero", ModContent.ZERO::get, 42, 1),
                new Entry("deep", ModContent.DEEP_VPN::get, 48, 1)
        );
    }

    public static Entry byId(String id) {
        for (Entry entry : entries()) if (entry.id.equals(id)) return entry;
        return null;
    }
}
