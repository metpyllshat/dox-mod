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
    public static final ForgeConfigSpec.BooleanValue RADAR_NEEDS_UPLINK;
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
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CACHE_EARLY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CACHE_MID;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CACHE_LATE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CACHE_AIRDROP;
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
    public static final ForgeConfigSpec.IntValue PVP_FREE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue LOOT_SCALING;
    public static final ForgeConfigSpec.DoubleValue LOOT_SCALE_MAX;
    public static final ForgeConfigSpec.IntValue CACHE_ITEMS_MIN;
    public static final ForgeConfigSpec.IntValue CACHE_ITEMS_MAX;
    public static final ForgeConfigSpec.BooleanValue AIRDROP_ENABLED;
    public static final ForgeConfigSpec.IntValue AIRDROP_FIRST_MINUTE;
    public static final ForgeConfigSpec.IntValue AIRDROP_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.IntValue AIRDROP_ITEMS;
    public static final ForgeConfigSpec.IntValue BEACON_SHIELD_SECONDS;
    public static final ForgeConfigSpec.BooleanValue TRACE_SCALE_MAP;
    public static final ForgeConfigSpec.IntValue TRACE_MAP_REF;
    public static final ForgeConfigSpec.BooleanValue TRACE_AVERAGE;
    public static final ForgeConfigSpec.DoubleValue TRACE_BEAM_DEGREES;
    public static final ForgeConfigSpec.DoubleValue LEAK_PLAYERS_AT;
    public static final ForgeConfigSpec.DoubleValue LEAK_BEACONS_AT;
    public static final ForgeConfigSpec.IntValue LEAK_SCATTER_PERCENT;
    public static final ForgeConfigSpec.IntValue TOWER_COUNT;
    public static final ForgeConfigSpec.BooleanValue TOWER_LOOT;
    public static final ForgeConfigSpec.IntValue TOWER_PELENG_RADIUS;
    public static final ForgeConfigSpec.IntValue TOWER_SECTOR_RADIUS;
    public static final ForgeConfigSpec.IntValue TOWER_CHAT_RADIUS;
    public static final ForgeConfigSpec.IntValue TOWER_INTERCEPT_RADIUS;
    public static final ForgeConfigSpec.IntValue TOWER_CRYPTO_PER_MIN;
    public static final ForgeConfigSpec.IntValue TOWER_HACK_SECONDS;
    public static final ForgeConfigSpec.IntValue TOWER_DISABLE_SECONDS;
    public static final ForgeConfigSpec.IntValue TOWER_CAM_COST;
    public static final ForgeConfigSpec.IntValue TOWER_SCAN_COST;
    public static final ForgeConfigSpec.IntValue TOWER_SECTOR_SECONDS;
    public static final ForgeConfigSpec.BooleanValue EVENT_ENABLED;
    public static final ForgeConfigSpec.IntValue EVENT_FIRST_MINUTE;
    public static final ForgeConfigSpec.IntValue EVENT_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.BooleanValue AUCTION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DEATH_DROP_CONSUMABLES;
    public static final ForgeConfigSpec.IntValue DEATH_CRYPTO_LOSS_PERCENT;
    public static final ForgeConfigSpec.IntValue NODE_MINUTES_BEFORE_END;

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
        b.comment("Радар видит игроков только при живой сети: стойка-аплинк рядом, тарелка или спутник. "
                + "Без сети телефон ловит только чужие узлы и структуры. false — старое поведение, "
                + "когда хватало одного заряженного телефона.");
        RADAR_NEEDS_UPLINK = b.define("radarNeedsUplink", true);
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
        b.comment("Лут схронов. Строка: modid:item, modid:item*count или modid:item*min-max, "
                + "плюс необязательный вес через собаку: modid:item*1-2@25. Вес по умолчанию 10. "
                + "Нет предмета в сборке — строка пропускается. Лут выбирается по весам, а не по категориям: "
                + "чем больше вес, тем чаще предмет. Радиоактивных изотопов (уран, плутоний, полоний) здесь нет: "
                + "они убивают прямо в руках. Защита от радиации — хазмат и антидот, они есть в поздней волне. "
                + "Предметам с NBT (оружие и навесное TaCZ) можно задать теги в фигурных скобках: "
                + "tacz:modern_kinetic_gun{GunId=tacz:ak47}@7, tacz:attachment{AttachmentId=tacz:scope_t1}.");
        b.comment("Ранняя волна: первая треть матча. Пистолеты, дробовики, еда, железо, мелочь. "
                + "Оружие TACZ задаётся NBT: tacz:modern_kinetic_gun{GunId=tacz:glock_17}, "
                + "патроны так же: tacz:ammo{AmmoId=tacz:9mm}*24-48.");
        CACHE_EARLY = b.defineList("cacheEarly", List.of(
                "tacz:modern_kinetic_gun{GunId=tacz:glock_17}@9",
                "tacz:modern_kinetic_gun{GunId=tacz:m1911}@8",
                "tacz:modern_kinetic_gun{GunId=tacz:p320}@6",
                "tacz:modern_kinetic_gun{GunId=tacz:cz75}@6",
                "tacz:modern_kinetic_gun{GunId=tacz:uzi}@6",
                "tacz:modern_kinetic_gun{GunId=tacz:taurus943}@5",
                "tacz:modern_kinetic_gun{GunId=tacz:db_short}@6",
                "tacz:modern_kinetic_gun{GunId=tacz:m870}@5",
                "tacz:modern_kinetic_gun{GunId=tacz:sks_tactical}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:kar98}@3",
                "tacz:ammo{AmmoId=tacz:9mm}*48-96@16",
                "tacz:ammo{AmmoId=tacz:45acp}*32-64@11",
                "tacz:ammo{AmmoId=tacz:12g}*16-32@10",
                "tacz:ammo{AmmoId=tacz:762x39}*20-40@8",
                "tacz:ammo{AmmoId=tacz:22wmr}*24-48@5",
                "tacz:ammo{AmmoId=tacz:792x57}*5-10@3",
                "minecraft:bread*2-4@14",
                "minecraft:iron_ingot*6-14@16",
                "minecraft:gunpowder*8-14@20",
                "minecraft:redstone*6-12@10",
                "minecraft:sugar*3-6@6",
                "superbwarfare:medical_kit@6",
                "superbwarfare:heal_clip@7",
                "superbwarfare:ge_helmet_m_35@5",
                "superbwarfare:us_helmet_pasgt@5",
                "superbwarfare:crowbar@4",
                "electrodynamics:circuitbasic*2-4@7",
                "electrodynamics:platesteel*2-4@7",
                "blackchannel:field_laptop@3",
                "blackchannel:defibrillator@4",
                "minecraft:copper_ingot*24-44@20",
                "minecraft:coal*8-16@9",
                "minecraft:flint*4-8@6",
                "minecraft:golden_apple*1-2@4",
                "tacz:attachment{AttachmentId=tacz:sight_t1}@5",
                "tacz:attachment{AttachmentId=tacz:sight_rmr_dot}@4",
                "tacz:attachment{AttachmentId=tacz:sight_acro_rifle}@4",
                "tacz:attachment{AttachmentId=tacz:muzzle_silencer_phantom_s1}@3"
        ), o -> o instanceof String);
        b.comment("Средняя волна: вторая треть матча. Автоматы, пистолеты-пулемёты, броня, взрывчатка, РЭБ.");
        CACHE_MID = b.defineList("cacheMid", List.of(
                "tacz:modern_kinetic_gun{GunId=tacz:ak47}@7",
                "tacz:modern_kinetic_gun{GunId=tacz:m4a1}@6",
                "tacz:modern_kinetic_gun{GunId=tacz:m16a4}@5",
                "tacz:modern_kinetic_gun{GunId=tacz:hk416d}@5",
                "tacz:modern_kinetic_gun{GunId=tacz:g36k}@5",
                "tacz:modern_kinetic_gun{GunId=tacz:scar_l}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:aug}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:type_81}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:qbz_95}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:hk_mp5a5}@6",
                "tacz:modern_kinetic_gun{GunId=tacz:ump45}@5",
                "tacz:modern_kinetic_gun{GunId=tacz:vector45}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:p90}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:fn_fal}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:hk_g3}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:mk14}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:spas_12}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:m1014}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:deagle}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:rhino357}@3",
                "tacz:ammo{AmmoId=tacz:556x45}*40-80@15",
                "tacz:ammo{AmmoId=tacz:762x39}*40-80@13",
                "tacz:ammo{AmmoId=tacz:9mm}*48-96@10",
                "tacz:ammo{AmmoId=tacz:45acp}*32-64@9",
                "tacz:ammo{AmmoId=tacz:308}*40-80@8",
                "tacz:ammo{AmmoId=tacz:12g}*16-32@8",
                "tacz:ammo{AmmoId=tacz:57x28}*20-40@4",
                "tacz:ammo{AmmoId=tacz:58x42}*20-40@4",
                "tacz:ammo{AmmoId=tacz:50ae}*7-14@3",
                "superbwarfare:medical_kit*1-2@8",
                "superbwarfare:heal_clip*1-2@8",
                "superbwarfare:field_doctor@3",
                "superbwarfare:us_chest_iotv@5",
                "superbwarfare:ru_chest_6b43@5",
                "superbwarfare:ru_helmet_6b47@5",
                "superbwarfare:c4_bomb@3",
                "superbwarfare:detonator@3",
                "superbwarfare:hand_grenade*1-2@6",
                "superbwarfare:rgo_grenade*1-2@6",
                "superbwarfare:m18_smoke_grenade*1-2@5",
                "superbwarfare:claymore_mine*1-2@4",
                "ballistix:grenadeshrapnel*1-2@5",
                "ballistix:grenadeincendiary*1-2@4",
                "ballistix:grenadechemical*1-2@3",
                "sbw_ew_addon:portable_jammer@4",
                "sbw_ew_addon:direction_finder@4",
                "sbw_ew_addon:radio@4",
                "electrodynamics:nightvisiongoggles@3",
                "electrodynamics:hydraulicboots@3",
                "electrodynamics:servoleggings@3",
                "electrodynamics:electricbaton@4",
                "electrodynamics:combatarmorchestplate@4",
                "electrodynamics:combatarmorhelmet@4",
                "electrodynamics:mechanizedcrossbow@3",
                "nuclearscience:iodinetablet@3",
                "nuclearscience:antidote@3",
                "nuclearscience:geigercounter@3",
                "blackchannel:deep_vpn@3",
                "blackchannel:defibrillator@5",
                "minecraft:copper_block*4-7@10",
                "minecraft:iron_block*2-3@7",
                "minecraft:iron_ingot*12-24@14",
                "minecraft:gunpowder*12-24@14",
                "minecraft:redstone*10-18@8",
                "tacz:attachment{AttachmentId=tacz:scope_acog_ta31}@4",
                "tacz:attachment{AttachmentId=tacz:scope_elcan_4x}@4",
                "tacz:attachment{AttachmentId=tacz:muzzle_silencer_knight_qd}@4",
                "tacz:attachment{AttachmentId=tacz:extended_mag_2}@4",
                "tacz:attachment{AttachmentId=tacz:grip_vertical_military}@4",
                "tacz:attachment{AttachmentId=tacz:laser_peq15}@3"
        ), o -> o instanceof String);
        b.comment("Поздняя волна: последняя треть матча. Снайперки, пулемёты, РПГ, хазмат, топ-броня.");
        CACHE_LATE = b.defineList("cacheLate", List.of(
                "tacz:modern_kinetic_gun{GunId=tacz:ai_awp}@5",
                "tacz:modern_kinetic_gun{GunId=tacz:m700}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:m107}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:m95}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:mk14}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:scar_h}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:m249}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:fn_evolys}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:minigun}@2",
                "tacz:modern_kinetic_gun{GunId=tacz:rpg7}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:m320}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:aa12}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:taurus500}@3",
                "tacz:modern_kinetic_gun{GunId=tacz:timeless50}@1",
                "tacz:ammo{AmmoId=tacz:338}*10-20@8",
                "tacz:ammo{AmmoId=tacz:50bmg}*5-10@6",
                "tacz:ammo{AmmoId=tacz:30_06}*10-20@7",
                "tacz:ammo{AmmoId=tacz:308}*20-40@8",
                "tacz:ammo{AmmoId=tacz:556x45}*60-120@8",
                "tacz:ammo{AmmoId=tacz:12g}*24-48@6",
                "tacz:ammo{AmmoId=tacz:rpg_rocket}*1-2@4",
                "tacz:ammo{AmmoId=tacz:40mm}*2-4@4",
                "tacz:ammo{AmmoId=tacz:500mag}*5-10@3",
                "tacz:ammo{AmmoId=tacz:357mag}*6-12@3",
                "superbwarfare:thermal_imaging_goggles@4",
                "superbwarfare:parachute@4",
                "superbwarfare:repair_tool@5",
                "superbwarfare:medical_kit*2-3@6",
                "nuclearscience:hazmathelmet@4",
                "nuclearscience:hazmatplate@4",
                "nuclearscience:hazmatlegs@4",
                "nuclearscience:hazmatboots@4",
                "nuclearscience:reinforcedhazmathelmet@2",
                "nuclearscience:reinforcedhazmatplate@2",
                "electrodynamics:compositearmorchestplate@3",
                "electrodynamics:compositearmorhelmet@3",
                "electrodynamics:compositearmorleggings@3",
                "electrodynamics:compositearmorboots@3",
                "electrodynamics:jetpack@2",
                "electrodynamics:railgunkinetic@2",
                "electrodynamics:seismicscanner@3",
                "electrodynamics:circuitadvanced*2-4@6",
                "ballistix:rocketlauncher@3",
                "ballistix:missiletier1*1@3",
                "ballistix:defuser@3",
                "sbw_ew_addon:electromagnetic_gun@2",
                "sbw_ew_addon:advanced_drone@2",
                "minecraft:iron_ingot*20-40@12",
                "minecraft:gunpowder*16-32@12",
                "minecraft:copper_ingot*32-64@12",
                "minecraft:netherite_ingot@2",
                "minecraft:diamond_block@2",
                "minecraft:enchanted_golden_apple@1",
                "blackchannel:satellite_firmware@2",
                "blackchannel:deep_vpn@4",
                "tacz:attachment{AttachmentId=tacz:scope_standard_8x}@4",
                "tacz:attachment{AttachmentId=tacz:scope_vudu}@3",
                "tacz:attachment{AttachmentId=tacz:muzzle_silencer_ursus}@3",
                "tacz:attachment{AttachmentId=tacz:sniper_extended_mag_3}@3",
                "tacz:attachment{AttachmentId=tacz:ammo_mod_he}@3"
        ), o -> o instanceof String);
        b.comment("Аирдроп: сбрасывается по расписанию в случайную точку карты, координаты видят все. Только верхушка лута.");
        CACHE_AIRDROP = b.defineList("cacheAirdrop", List.of(
                "tacz:modern_kinetic_gun{GunId=tacz:ai_awp}@6",
                "tacz:modern_kinetic_gun{GunId=tacz:m107}@5",
                "tacz:modern_kinetic_gun{GunId=tacz:m95}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:minigun}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:rpg7}@5",
                "tacz:modern_kinetic_gun{GunId=tacz:aa12}@4",
                "tacz:modern_kinetic_gun{GunId=tacz:timeless50}@3",
                "tacz:ammo{AmmoId=tacz:338}*30-60@8",
                "tacz:ammo{AmmoId=tacz:50bmg}*10-20@6",
                "tacz:ammo{AmmoId=tacz:rpg_rocket}*2-3@6",
                "tacz:ammo{AmmoId=tacz:556x45}*90-150@7",
                "tacz:ammo{AmmoId=tacz:308}*60-100@6",
                "tacz:ammo{AmmoId=tacz:40mm}*3-6@5",
                "superbwarfare:thermal_imaging_goggles@6",
                "superbwarfare:medical_kit*2-3@8",
                "electrodynamics:jetpack@4",
                "electrodynamics:railgunplasma@3",
                "electrodynamics:compositearmorchestplate@5",
                "nuclearscience:reinforcedhazmatplate@4",
                "nuclearscience:reinforcedhazmathelmet@4",
                "ballistix:missiletier2*1-2@5",
                "ballistix:rocketlauncher@4",
                "sbw_ew_addon:electromagnetic_gun@3",
                "sbw_ew_addon:advanced_drone@3",
                "minecraft:netherite_ingot*1-2@3",
                "minecraft:enchanted_golden_apple@3",
                "minecraft:elytra@2",
                "blackchannel:satellite_firmware@4",
                "blackchannel:deep_vpn@5",
                "tacz:attachment{AttachmentId=tacz:scope_mk5hd}@4",
                "tacz:attachment{AttachmentId=tacz:muzzle_silencer_wraith}@4"
        ), o -> o instanceof String);
        b.comment("Лут в чужие сундуки и данжи внутри зоны: формат строк тот же.");
        CACHE_DUNGEON = b.defineList("cacheDungeon", List.of(
                "minecraft:bread*1-4@12",
                "minecraft:iron_ingot*2-6@10",
                "minecraft:redstone*2-8@8",
                "minecraft:gunpowder*1-3@8",
                "blackchannel:battery_cell@6",
                "tacz:ammo{AmmoId=tacz:9mm}*8-16@8",
                "tacz:ammo{AmmoId=tacz:556x45}*8-16@8"
        ), o -> o instanceof String);
        b.comment("Сколько предметов выпадает в одном схроне: случайное число от минимума до максимума.");
        b.comment("Базовые значения выше — это эталон: 8 игроков, 14 схронов на карте 1000×1000. "
                + "На больших катках лут домножается на коэффициент от числа игроков и размера карты, "
                + "иначе на 16 игроков те же 14 схронов кормят вдвое больше народу. false — масштаб не применяется.");
        LOOT_SCALING = b.define("lootScaling", true);
        b.comment("Верхний предел коэффициента: во столько раз максимум дороже может стать схрон.");
        LOOT_SCALE_MAX = b.defineInRange("lootScaleMax", 3.0D, 1.0D, 6.0D);
        CACHE_ITEMS_MIN = b.defineInRange("cacheItemsMin", 3, 1, 12);
        CACHE_ITEMS_MAX = b.defineInRange("cacheItemsMax", 6, 1, 12);
        b.comment("Аирдропы: сброс в случайную точку карты с координатами для всех. Первый — через airDropFirstMinute минут, дальше каждые airDropIntervalMinutes.");
        AIRDROP_ENABLED = b.define("airdropEnabled", true);
        AIRDROP_FIRST_MINUTE = b.defineInRange("airdropFirstMinute", 15, 1, 180);
        AIRDROP_INTERVAL_MINUTES = b.defineInRange("airdropIntervalMinutes", 20, 5, 180);
        AIRDROP_ITEMS = b.defineInRange("airdropItems", 5, 1, 12);
        b.pop();

        b.push("world");
        RUMOR_ENABLED = b.define("rumors", false);
        b.pop();

        b.push("match");
        b.comment("Рабочая длина катки — полтора-два часа, по умолчанию 90 минут. Матч идёт matchDuration минут, потом finalDuration минут — кровати и маяки отключены, смерть перманентная. Победитель — команда с максимумом очков.");
        MATCH_DURATION_MINUTES = b.defineInRange("matchDurationMinutes", 90, 5, 180);
        b.comment("Отсутствие PvP в начале матча, в секундах: игроки не могут ранить друг друга, удар даже не проходит. Мобы, падение, огонь и лава бьют как обычно. Отсчёт от старта матча, 0 — PvP сразу.");
        PVP_FREE_SECONDS = b.defineInRange("pvpFreeSeconds", 90, 0, 7200);
        FINAL_DURATION_MINUTES = b.defineInRange("finalDurationMinutes", 10, 1, 30);
        b.comment("Очки — это не цель катки, а только способ выбрать победителя, если после финала живы несколько отрядов. Цель — сломать все чужие маяки и выжить.");
        SCORE_KILL = b.defineInRange("scoreKill", 2, 0, 100);
        SCORE_BED = b.defineInRange("scoreBed", 3, 0, 100);
        b.comment("Сломанный маяк стоит дорого: это и есть главная цель катки.");
        SCORE_BEACON = b.defineInRange("scoreBeacon", 8, 0, 100);
        REVIVE_STAND_SECONDS = b.defineInRange("reviveStandSeconds", 5, 1, 30);
        DOWNED_DURATION_TICKS = b.defineInRange("downedDurationTicks", 200, 40, 1200);
        b.comment("Сколько секунд от старта маяк отряда нельзя сломать: он стоит в бункере под землёй, а это защита от раннего набега. 0 — без щита.");
        BEACON_SHIELD_SECONDS = b.defineInRange("beaconShieldSeconds", 600, 0, 7200);
        b.comment("Смерть забирает расходники: патроны, еду, аптечки, порох и блоки падают сумкой на месте падения, союзник может подобрать. Оружие, броня, терминалы, SIM и крипта остаются.");
        DEATH_DROP_CONSUMABLES = b.define("deathDropConsumables", true);
        b.comment("Сколько процентов кошелька теряется при окончательной смерти (в спектатор). 0 — не терять.");
        DEATH_CRYPTO_LOSS_PERCENT = b.defineInRange("deathCryptoLossPercent", 25, 0, 100);
        b.pop();

        b.push("intel");
        b.comment("TRACE даёт пеленг: направление на источник с угловой погрешностью. Чтобы получить точку, нужно два-три замера с разных мест — они пересекаются.");
        b.comment("Погрешность TRACE берётся из traceScatter и домножается на корень из (сторона карты / traceMapRefBlocks). false — погрешность одинаковая на любой карте.");
        TRACE_SCALE_MAP = b.define("traceScaleWithMap", true);
        b.comment("Размер карты, для которого настроен traceScatter. На 2500 при 1000 погрешность вырастает в 1.58 раза.");
        TRACE_MAP_REF = b.defineInRange("traceMapRefBlocks", 1000, 200, 8000);
        b.comment("Ширина пеленга в градусах на карте traceMapRefBlocks: половина угла, в котором лежит цель. 10 — на 800 блоках это ±140.");
        TRACE_BEAM_DEGREES = b.defineInRange("traceBeamDegrees", 10.0D, 2.0D, 40.0D);
        b.comment("Повторные TRACE по одной цели усредняются: каждый следующий замер режет погрешность на треть, но не больше четырёх раз. Платить всё равно за каждый.");
        TRACE_AVERAGE = b.define("traceAveraging", true);
        b.comment("Доля катки, на которой всем утекают координаты всех игроков с погрешностью (0.5 — половина матча).");
        LEAK_PLAYERS_AT = b.defineInRange("leakPlayersAt", 0.5D, 0.05D, 1.0D);
        b.comment("Доля катки, на которой всем утекают координаты всех маяков отрядов с погрешностью (0.75 — три четверти матча).");
        LEAK_BEACONS_AT = b.defineInRange("leakBeaconsAt", 0.75D, 0.05D, 1.0D);
        b.comment("Погрешность утечек в процентах от стороны карты: 8 — это ±200 блоков на карте 2500.");
        LEAK_SCATTER_PERCENT = b.defineInRange("leakScatterPercent", 8, 0, 40);
        b.pop();

        b.push("towers");
        b.comment("Вышки связи: высокие мачты на карте, на верхней площадке лежит хороший лут. На них видно половину карты, поэтому за них драка. 0 — не ставить.");
        TOWER_COUNT = b.defineInRange("towerCount", 6, 0, 12);
        b.comment("Класть ли на верхнюю площадку вышки контейнер с лутом поздней волны.");
        TOWER_LOOT = b.define("towerLoot", true);
        b.comment("Что даёт вышка: пеленг на любой шум 400, карта сектора 600 раз в 60с, крипта 2/мин, чат отряда 800 без стойки, перехват чужих действий");
        TOWER_PELENG_RADIUS = b.defineInRange("towerPelengRadius", 400, 32, 2048);
        TOWER_SECTOR_RADIUS = b.defineInRange("towerSectorRadius", 600, 32, 2048);
        TOWER_SECTOR_SECONDS = b.defineInRange("towerSectorSeconds", 60, 10, 600);
        TOWER_CHAT_RADIUS = b.defineInRange("towerChatRadius", 800, 32, 2048);
        TOWER_INTERCEPT_RADIUS = b.defineInRange("towerInterceptRadius", 400, 32, 2048);
        TOWER_CRYPTO_PER_MIN = b.defineInRange("towerCryptoPerMin", 2, 0, 100);
        TOWER_HACK_SECONDS = b.defineInRange("towerHackSeconds", 20, 2, 120);
        TOWER_DISABLE_SECONDS = b.defineInRange("towerDisableSeconds", 300, 10, 3600);
        b.comment("Платные действия с вышки: камера и скан");
        TOWER_CAM_COST = b.defineInRange("towerCamCost", 6, 0, 100);
        TOWER_SCAN_COST = b.defineInRange("towerScanCost", 4, 0, 100);
        b.pop();

        b.push("events");
        b.comment("События вечера: раз в eventIntervalMinutes канал объявляет ЭМИ-шторм, тишину, аукцион или сводку. false — не проводить.");
        EVENT_ENABLED = b.define("eventsEnabled", true);
        EVENT_FIRST_MINUTE = b.defineInRange("eventFirstMinute", 20, 1, 180);
        EVENT_INTERVAL_MINUTES = b.defineInRange("eventIntervalMinutes", 18, 5, 180);
        b.comment("Аукцион: раз в катку на рынок выставляется лот, который никто не видит до покупки. Торгуются вслепую.");
        AUCTION_ENABLED = b.define("auctionEnabled", true);
        b.comment("Узел: терминал в центре карты включается за nodeMinutesBeforeEnd минут до конца финала и даёт очки за удержание. 0 — не включать.");
        NODE_MINUTES_BEFORE_END = b.defineInRange("nodeMinutesBeforeEnd", 10, 0, 60);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
