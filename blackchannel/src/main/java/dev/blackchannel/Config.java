package dev.blackchannel;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SWAT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SWAT_BREAKS;
    public static final ForgeConfigSpec.BooleanValue SWAT_FIRE;
    public static final ForgeConfigSpec.BooleanValue BOMB_ENABLED;
    public static final ForgeConfigSpec.BooleanValue RUMOR_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BOUNTY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue HOOK_EMP;
    public static final ForgeConfigSpec.BooleanValue HOOK_JAMMER;
    public static final ForgeConfigSpec.BooleanValue HOOK_DRONE;
    public static final ForgeConfigSpec.BooleanValue LOG_OPS;

    public static final ForgeConfigSpec.IntValue TRACE_CD;
    public static final ForgeConfigSpec.IntValue DEANON_CD;
    public static final ForgeConfigSpec.IntValue SWAT_CD;
    public static final ForgeConfigSpec.IntValue BOMB_CD;
    public static final ForgeConfigSpec.IntValue SWAT_DELAY;
    public static final ForgeConfigSpec.IntValue EMP_SECONDS;
    public static final ForgeConfigSpec.IntValue EMP_RADIUS;
    public static final ForgeConfigSpec.IntValue DRONE_EMP_RADIUS;
    public static final ForgeConfigSpec.IntValue INTEL_STALE;
    public static final ForgeConfigSpec.IntValue MEMORY;
    public static final ForgeConfigSpec.IntValue MESH_RANGE;
    public static final ForgeConfigSpec.IntValue UPLINK_RANGE;
    public static final ForgeConfigSpec.IntValue DISH_RANGE;
    public static final ForgeConfigSpec.IntValue JAMMER_RANGE;
    public static final ForgeConfigSpec.IntValue FARADAY_RANGE;
    public static final ForgeConfigSpec.IntValue RADAR_PHONE;
    public static final ForgeConfigSpec.IntValue RADAR_LAPTOP;
    public static final ForgeConfigSpec.IntValue CRYPTO_PER_MIN;
    public static final ForgeConfigSpec.IntValue RACK_MINT_RANGE;
    public static final ForgeConfigSpec.IntValue TRACE_BASE;
    public static final ForgeConfigSpec.IntValue TRACE_VPN;
    public static final ForgeConfigSpec.DoubleValue SWAT_POWER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("ops");
        b.comment("Ложный огневой вызов бьёт по сохранённой отметке, не по телу игрока. Это не ядерный удар.");
        SWAT_ENABLED = b.define("swatEnabled", true);
        SWAT_BREAKS = b.define("swatBreaksBlocks", true);
        SWAT_FIRE = b.define("swatSetsFire", false);
        SWAT_POWER = b.defineInRange("swatPower", 3.0, 0.0, 8.0);
        SWAT_DELAY = b.defineInRange("swatDelaySeconds", 50, 5, 300);
        SWAT_CD = b.defineInRange("swatCooldownSeconds", 180, 0, 3600);
        BOMB_ENABLED = b.define("bombEnabled", true);
        BOMB_CD = b.defineInRange("bombCooldownSeconds", 75, 0, 3600);
        TRACE_CD = b.defineInRange("traceCooldownSeconds", 40, 0, 3600);
        b.comment("Погрешность TRACE: база плюс добавка за каждый уровень VPN. 8 — почти GPS. 150–250 — район. 500 — намёк на биом, базу по следу не найти. Своя тарелка всё ещё ужимает число.");
        TRACE_BASE = b.defineInRange("traceBaseError", 8, 0, 2000);
        TRACE_VPN = b.defineInRange("traceVpnError", 50, 0, 2000);
        DEANON_CD = b.defineInRange("deanonCooldownSeconds", 60, 0, 3600);
        INTEL_STALE = b.defineInRange("intelStaleSeconds", 240, 30, 3600);
        MEMORY = b.defineInRange("memorySeconds", 180, 10, 3600);
        LOG_OPS = b.define("logOps", true);
        b.pop();

        b.push("signal");
        MESH_RANGE = b.defineInRange("meshRange", 96, 16, 512);
        UPLINK_RANGE = b.defineInRange("uplinkRange", 72, 8, 512);
        DISH_RANGE = b.defineInRange("dishRange", 720, 64, 4096);
        JAMMER_RANGE = b.defineInRange("jammerRange", 32, 4, 128);
        FARADAY_RANGE = b.defineInRange("faradayRange", 8, 2, 32);
        RADAR_PHONE = b.defineInRange("radarPhone", 52, 8, 256);
        RADAR_LAPTOP = b.defineInRange("radarLaptop", 110, 16, 512);
        CRYPTO_PER_MIN = b.defineInRange("cryptoPerMinute", 4, 0, 64);
        b.comment("Один чекан на владельца в этом радиусе. Дальняя база чеканит отдельно. Потолок кошелька 5000.");
        RACK_MINT_RANGE = b.defineInRange("rackMintRange", 96, 16, 512);
        b.pop();

        b.push("integration");
        b.comment("ЭМИ Ballistix и побочный импульс ЭМИ-пушки EW гасят телефоны. Глушилки EW глушат канал. Дроны Superb Warfare видны на радаре.");
        HOOK_EMP = b.define("hookEmp", true);
        HOOK_JAMMER = b.define("hookEwJammers", true);
        HOOK_DRONE = b.define("hookDrones", true);
        EMP_SECONDS = b.defineInRange("empShutdownSeconds", 28, 1, 180);
        EMP_RADIUS = b.defineInRange("ballistixEmpRadius", 40, 4, 128);
        DRONE_EMP_RADIUS = b.defineInRange("droneEmpCollateralRadius", 16, 4, 64);
        b.pop();

        b.push("world");
        RUMOR_ENABLED = b.define("rumors", false);
        BOUNTY_ENABLED = b.define("bounties", true);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
