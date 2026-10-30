package dev.blackchannel;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SWAT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SWAT_BREAKS;
    public static final ForgeConfigSpec.BooleanValue SWAT_FIRE;
    public static final ForgeConfigSpec.BooleanValue BOMB_ENABLED;
    public static final ForgeConfigSpec.BooleanValue RUMOR_ENABLED;
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
    public static final ForgeConfigSpec.DoubleValue TRACE_VPN_MUL;
    public static final ForgeConfigSpec.DoubleValue TRACE_VPN_DEEP;
    public static final ForgeConfigSpec.IntValue TRACE_STALE;
    public static final ForgeConfigSpec.IntValue TRACE_FAIL;
    public static final ForgeConfigSpec.IntValue TRACE_FAIL_VPN;
    public static final ForgeConfigSpec.IntValue TRACE_FAIL_DEEP;
    public static final ForgeConfigSpec.IntValue TRACE_COST;
    public static final ForgeConfigSpec.IntValue DEANON_COST;
    public static final ForgeConfigSpec.IntValue SWAT_COST;
    public static final ForgeConfigSpec.IntValue SWAT_CONTRACT_COST;
    public static final ForgeConfigSpec.IntValue BOMB_COST;
    public static final ForgeConfigSpec.IntValue UAV_COST;
    public static final ForgeConfigSpec.IntValue UAV_RANGE;
    public static final ForgeConfigSpec.IntValue UAV_ERROR;
    public static final ForgeConfigSpec.IntValue OPTIC_COST;
    public static final ForgeConfigSpec.IntValue PEEK_COST;
    public static final ForgeConfigSpec.IntValue OPTIC_CD;
    public static final ForgeConfigSpec.IntValue PEEK_CD;
    public static final ForgeConfigSpec.IntValue DISH_RADAR_BONUS;
    public static final ForgeConfigSpec.IntValue STRUCTURE_RADAR;
    public static final ForgeConfigSpec.IntValue STRUCTURE_PHONE;
    public static final ForgeConfigSpec.IntValue LOGGER_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CACHE_COMMON;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CACHE_UNCOMMON;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CACHE_RARE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CACHE_DUNGEON;
    public static final ForgeConfigSpec.DoubleValue SWAT_POWER;
    public static final ForgeConfigSpec.DoubleValue FLOOD_VOLUME;
    public static final ForgeConfigSpec.IntValue FLOOD_SOUND_TICKS;
    public static final ForgeConfigSpec.IntValue FLOOD_CHAT_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLOOD_LINES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLOOD_SOUNDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLOOD_IMAGES;
    public static final ForgeConfigSpec.IntValue MATCH_DURATION_MINUTES;
    public static final ForgeConfigSpec.IntValue FINAL_DURATION_MINUTES;
    public static final ForgeConfigSpec.IntValue SCORE_KILL;
    public static final ForgeConfigSpec.IntValue SCORE_BED;
    public static final ForgeConfigSpec.IntValue SCORE_BEACON;
    public static final ForgeConfigSpec.IntValue REVIVE_STAND_SECONDS;
    public static final ForgeConfigSpec.IntValue DOWNED_DURATION_TICKS;

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
        b.comment("Погрешность TRACE. Ключ traceScatter, не старый traceBaseError: тот файл больше не читается, чтобы дефолт 500 доехал до уже созданных конфигов. 500 — намёк на биом. Обычный VPN умножает базу на traceVpnMul, глубокий на traceVpnDeep. Старый ключ traceVpnError больше не читается. Своя тарелка ужимает число. Старая отметка добавляет traceStaleError.");
        TRACE_BASE = b.defineInRange("traceScatter", 500, 0, 2000);
        TRACE_VPN_MUL = b.defineInRange("traceVpnMul", 1.5D, 1.0D, 8.0D);
        TRACE_VPN_DEEP = b.defineInRange("traceVpnDeepMul", 2.0D, 1.0D, 8.0D);
        TRACE_STALE = b.defineInRange("traceStaleError", 40, 0, 2000);
        TRACE_FAIL = b.defineInRange("traceFailPercent", 5, 0, 100);
        TRACE_FAIL_VPN = b.defineInRange("traceFailVpnPercent", 12, 0, 100);
        TRACE_FAIL_DEEP = b.defineInRange("traceFailDeepPercent", 35, 0, 100);
        TRACE_COST = b.defineInRange("traceCost", 2, 0, 5000);
        DEANON_COST = b.defineInRange("deanonCost", 4, 0, 5000);
        SWAT_COST = b.defineInRange("swatCost", 40, 0, 5000);
        SWAT_CONTRACT_COST = b.defineInRange("swatContractCost", 8, 0, 5000);
        BOMB_COST = b.defineInRange("bombCost", 3, 0, 5000);
        UAV_COST = b.defineInRange("uavCost", 3, 0, 5000);
        UAV_RANGE = b.defineInRange("uavRange", 96, 8, 256);
        UAV_ERROR = b.defineInRange("uavError", 30, 0, 2000);
        OPTIC_COST = b.defineInRange("opticCost", 6, 0, 5000);
        PEEK_COST = b.defineInRange("peekCost", 8, 0, 5000);
        OPTIC_CD = b.defineInRange("opticCooldownSeconds", 45, 0, 3600);
        PEEK_CD = b.defineInRange("peekCooldownSeconds", 60, 0, 3600);
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
        b.comment("Поставленный блок приманки пишет позывные в этом радиусе. Координат не даёт.");
        LOGGER_RADIUS = b.defineInRange("loggerRadius", 50, 8, 128);
        DISH_RADAR_BONUS = b.defineInRange("dishRadarBonus", 80, 0, 512);
        b.comment("Дальность, на которой радар видит стойки, тарелки, глушилки и чужие радиоузлы. Игроков это не удлиняет.");
        STRUCTURE_RADAR = b.defineInRange("structureRadar", 360, 32, 2048);
        STRUCTURE_PHONE = b.defineInRange("structureRadarPhone", 180, 16, 2048);
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

        b.push("flood");
        b.comment("Текст, звуки и картинки флуда. Пустой floodLines оставляет короткие встроенные фразы. Звук — id события, например minecraft:block.note_block.bit. Картинка — путь ресурса. Свои PNG кладутся в config/blackchannel/flood, не больше 4 файлов по 48 КБ.");
        FLOOD_LINES = b.defineList("floodLines", List.of(
                "НЕИЗВЕСТНЫЙ НОМЕР",
                "ЛИНИЯ ЗАНЯТА",
                "ПОТОК ПАКЕТОВ",
                "ОБРАТНЫЙ ВЫЗОВ СОРВАН",
                "SIM ЗАБЛОКИРОВАНА",
                "ПЕТЛЯ МАРШРУТА",
                "КАНАЛ ПЕРЕГРУЖЕН",
                "ПОВТОРИТЕ КОД",
                "СООБЩЕНИЕ НЕ ДОСТАВЛЕНО",
                "ВАМ ЗВОНИЛИ 14 РАЗ",
                "СРОЧНО ПЕРЕЗВОНИТЕ",
                "БАЛАНС ОТРИЦАТЕЛЬНЫЙ"
        ), o -> o instanceof String);
        FLOOD_SOUNDS = b.defineList("floodSounds", List.of(
                "minecraft:block.note_block.bit",
                "minecraft:block.note_block.pling",
                "minecraft:block.note_block.bell",
                "minecraft:entity.experience_orb.pickup",
                "minecraft:block.amethyst_block.chime",
                "minecraft:entity.villager.no"
        ), o -> o instanceof String);
        FLOOD_IMAGES = b.defineList("floodImages", List.of(
                "blackchannel:textures/flood/noise.png",
                "blackchannel:textures/flood/warn.png"
        ), o -> o instanceof String);
        FLOOD_VOLUME = b.defineInRange("floodSoundVolume", 0.7D, 0.0D, 1.0D);
        FLOOD_SOUND_TICKS = b.defineInRange("floodSoundTicks", 10, 2, 200);
        FLOOD_CHAT_TICKS = b.defineInRange("floodChatTicks", 15, 5, 200);
        b.pop();

        b.push("caches");
        b.comment("Лут схронов. Строка: modid:item или modid:item*count или modid:item*min-max. Нет предмета в сборке — строка пропускается. Тёмной материи, антиматерии и стержня урана в списке нет: добавь сам, если хочешь.");
        CACHE_COMMON = b.defineList("cacheCommon", List.of(
                "minecraft:bread*4-8",
                "minecraft:iron_ingot*3-8",
                "minecraft:copper_ingot*3-8",
                "minecraft:redstone*4-10",
                "minecraft:coal*4-8",
                "minecraft:gunpowder*2-5"
        ), o -> o instanceof String);
        CACHE_UNCOMMON = b.defineList("cacheUncommon", List.of(
                "blackchannel:field_laptop",
                "blackchannel:signal_scanner",
                "blackchannel:battery_cell",
                "blackchannel:trace_logger",
                "electrodynamics:circuitbasic",
                "electrodynamics:platesteel*2-4"
        ), o -> o instanceof String);
        CACHE_RARE = b.defineList("cacheRare", List.of(
                "blackchannel:uplink_rack",
                "blackchannel:sigint_dish",
                "sbw_ew_addon:radio",
                "sbw_ew_addon:portable_jammer",
                "ballistix:missiletier1*1-2",
                "ballistix:rocketlauncher"
        ), o -> o instanceof String);
        CACHE_DUNGEON = b.defineList("cacheDungeon", List.of(
                "minecraft:iron_ingot*2-6",
                "minecraft:redstone*2-8",
                "minecraft:bread*1-4",
                "blackchannel:battery_cell",
                "minecraft:gunpowder*1-3"
        ), o -> o instanceof String);
        b.pop();

        b.push("world");
        RUMOR_ENABLED = b.define("rumors", false);
        b.pop();

        b.push("match");
        b.comment("Матч идёт matchDuration минут, потом finalDuration минут — кровати и маяки отключены, смерть перманентная. Победитель — команда с максимумом очков.");
        MATCH_DURATION_MINUTES = b.defineInRange("matchDurationMinutes", 30, 5, 120);
        FINAL_DURATION_MINUTES = b.defineInRange("finalDurationMinutes", 10, 1, 30);
        SCORE_KILL = b.defineInRange("scoreKill", 1, 0, 100);
        SCORE_BED = b.defineInRange("scoreBed", 2, 0, 100);
        SCORE_BEACON = b.defineInRange("scoreBeacon", 2, 0, 100);
        REVIVE_STAND_SECONDS = b.defineInRange("reviveStandSeconds", 5, 1, 30);
        DOWNED_DURATION_TICKS = b.defineInRange("downedDurationTicks", 200, 40, 1200);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
