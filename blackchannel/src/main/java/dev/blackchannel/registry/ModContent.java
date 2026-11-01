package dev.blackchannel.registry;

import dev.blackchannel.BlackChannelMod;
import dev.blackchannel.block.NodeBlock;
import dev.blackchannel.block.NodeBlockEntity;
import dev.blackchannel.block.LoggerBlock;
import dev.blackchannel.block.LoggerBlockEntity;
import dev.blackchannel.item.DefibItem;
import dev.blackchannel.item.DeviceItem;
import dev.blackchannel.item.LoggerItem;
import dev.blackchannel.item.TagItem;
import dev.blackchannel.item.ToolItem;
import dev.blackchannel.menu.Menus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.extensions.IForgeMenuType;

public final class ModContent {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BlackChannelMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BlackChannelMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BlackChannelMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, BlackChannelMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlackChannelMod.MODID);

    public static final RegistryObject<Block> RACK = BLOCKS.register("uplink_rack", () -> new NodeBlock(NodeBlock.Kind.RACK, MapColor.COLOR_BLACK));
    public static final RegistryObject<Block> DISH = BLOCKS.register("sigint_dish", () -> new NodeBlock(NodeBlock.Kind.DISH, MapColor.COLOR_LIGHT_BLUE));
    public static final RegistryObject<Block> FARADAY = BLOCKS.register("faraday_anchor", () -> new NodeBlock(NodeBlock.Kind.FARADAY, MapColor.COLOR_LIGHT_GRAY));
    public static final RegistryObject<Block> JAMMER = BLOCKS.register("signal_jammer", () -> new NodeBlock(NodeBlock.Kind.JAMMER, MapColor.COLOR_RED));
    public static final RegistryObject<Block> BEACON = BLOCKS.register("team_beacon", () -> new NodeBlock(NodeBlock.Kind.BEACON, MapColor.COLOR_GREEN));
    public static final RegistryObject<Block> DECOY = BLOCKS.register("decoy_beacon", () -> new NodeBlock(NodeBlock.Kind.DECOY, MapColor.COLOR_ORANGE));
    public static final RegistryObject<Block> LOGGER = BLOCKS.register("trace_logger", LoggerBlock::new);

    public static final RegistryObject<BlockEntityType<NodeBlockEntity>> NODE = BLOCK_ENTITIES.register("node",
            () -> BlockEntityType.Builder.of(NodeBlockEntity::new,
                    RACK.get(), DISH.get(), FARADAY.get(), JAMMER.get(), BEACON.get(), DECOY.get()).build(null));
    public static final RegistryObject<BlockEntityType<LoggerBlockEntity>> LOGGER_BE = BLOCK_ENTITIES.register("trace_logger",
            () -> BlockEntityType.Builder.of(LoggerBlockEntity::new, LOGGER.get()).build(null));

    public static final RegistryObject<Item> RACK_ITEM = blockItem(RACK);
    public static final RegistryObject<Item> DISH_ITEM = blockItem(DISH);
    public static final RegistryObject<Item> FARADAY_ITEM = blockItem(FARADAY);
    public static final RegistryObject<Item> JAMMER_ITEM = blockItem(JAMMER);
    public static final RegistryObject<Item> BEACON_ITEM = blockItem(BEACON);
    public static final RegistryObject<Item> DECOY_ITEM = blockItem(DECOY);
    public static final RegistryObject<Item> LOGGER_ITEM = ITEMS.register("trace_logger",
            () -> new LoggerItem(LOGGER.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> DEFIB = ITEMS.register("defibrillator",
            () -> new DefibItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TAG = ITEMS.register("dog_tag",
            () -> new TagItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> PHONE = ITEMS.register("burner_phone",
            () -> new DeviceItem(DeviceItem.Kind.PHONE, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> LAPTOP = ITEMS.register("field_laptop",
            () -> new DeviceItem(DeviceItem.Kind.LAPTOP, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> VPN = tool("vpn_chip", ToolItem.Kind.VPN, 16, Rarity.UNCOMMON);
    public static final RegistryObject<Item> DEEP_VPN = tool("deep_vpn", ToolItem.Kind.VPN2, 16, Rarity.RARE);
    public static final RegistryObject<Item> IDS = tool("ids_module", ToolItem.Kind.IDS, 16, Rarity.UNCOMMON);
    public static final RegistryObject<Item> HARDEN = tool("emp_hardening", ToolItem.Kind.HARDEN, 16, Rarity.UNCOMMON);
    public static final RegistryObject<Item> EXPLOIT = tool("exploit_firmware", ToolItem.Kind.EXPLOIT, 16, Rarity.UNCOMMON);
    public static final RegistryObject<Item> ZERO = tool("zero_day", ToolItem.Kind.ZERO, 16, Rarity.EPIC);
    public static final RegistryObject<Item> CONTRACT = tool("false_contract", ToolItem.Kind.CONTRACT, 16, Rarity.UNCOMMON);
    public static final RegistryObject<Item> CELL = tool("battery_cell", ToolItem.Kind.CELL, 16, Rarity.COMMON);
    public static final RegistryObject<Item> SIM = tool("burner_sim", ToolItem.Kind.SIM, 16, Rarity.COMMON);
    public static final RegistryObject<Item> SAT = tool("satellite_firmware", ToolItem.Kind.SAT, 16, Rarity.RARE);
    public static final RegistryObject<Item> HASH = tool("hashcoin", ToolItem.Kind.HASH, 64, Rarity.COMMON);
    public static final RegistryObject<Item> SAMPLE = tool("signal_sample", ToolItem.Kind.SAMPLE, 16, Rarity.UNCOMMON);

    public static final RegistryObject<MenuType<?>> PHONE_MENU = MENUS.register("phone",
            () -> IForgeMenuType.create((id, inv, buf) -> new Menus.PhoneMenu(id, inv)));
    public static final RegistryObject<MenuType<?>> LAPTOP_MENU = MENUS.register("laptop",
            () -> IForgeMenuType.create((id, inv, buf) -> new Menus.LaptopMenu(id, inv)));
    public static final RegistryObject<MenuType<?>> RACK_MENU = MENUS.register("rack",
            () -> IForgeMenuType.create((id, inv, buf) -> new Menus.RackMenu(id, inv, buf.readBlockPos())));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.blackchannel"))
            .icon(() -> new ItemStack(PHONE.get()))
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> out.accept(e.get())))
            .build());

    private ModContent() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
    }

    private static RegistryObject<Item> blockItem(RegistryObject<Block> block) {
        return ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static RegistryObject<Item> tool(String name, ToolItem.Kind kind, int stack, Rarity rarity) {
        return ITEMS.register(name, () -> new ToolItem(kind, new Item.Properties().stacksTo(stack).rarity(rarity)));
    }
}
