#!/usr/bin/env python3
"""Generate models, loot, recipes, advancements and lang. Does not touch textures."""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "src", "main", "resources")

BLOCKS = [
    "uplink_rack",
    "sigint_dish",
    "faraday_anchor",
    "signal_jammer",
    "team_beacon",
    "decoy_beacon",
]
ITEMS = [
    "burner_phone",
    "field_laptop",
    "vpn_chip",
    "deep_vpn",
    "ids_module",
    "emp_hardening",
    "exploit_firmware",
    "zero_day",
    "false_contract",
    "sticky_tracker",
    "signal_scanner",
    "battery_cell",
    "burner_sim",
    "satellite_firmware",
    "hashcoin",
    "signal_sample",
]


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def models():
    for block in BLOCKS:
        dump(os.path.join(RES, "assets/blackchannel/models/block", block + ".json"), {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": "blackchannel:block/" + block},
        })
        dump(os.path.join(RES, "assets/blackchannel/models/item", block + ".json"), {
            "parent": "blackchannel:block/" + block,
        })
        dump(os.path.join(RES, "assets/blackchannel/blockstates", block + ".json"), {
            "variants": {"": {"model": "blackchannel:block/" + block}},
        })
        dump(os.path.join(RES, "data/blackchannel/loot_tables/blocks", block + ".json"), {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": "blackchannel:" + block}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }],
        })
    for item in ITEMS:
        dump(os.path.join(RES, "assets/blackchannel/models/item", item + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "blackchannel:item/" + item},
        })
    dump(os.path.join(RES, "data/minecraft/tags/blocks/mineable/pickaxe.json"), {
        "replace": False,
        "values": ["blackchannel:" + b for b in BLOCKS],
    })


def shaped(name, pattern, keys, result, count=1, unlock=None, conditions=None):
    obj = {
        "type": "minecraft:crafting_shaped",
        "pattern": pattern,
        "key": {k: {"item": v} for k, v in keys.items()},
        "result": {"item": result, "count": count},
    }
    if conditions:
        obj["conditions"] = conditions
    dump(os.path.join(RES, "data/blackchannel/recipes", name + ".json"), obj)
    if conditions:
        return
    item = unlock or next(iter(keys.values()))
    dump(os.path.join(RES, "data/blackchannel/advancements/recipes/misc", name + ".json"), {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {
                "trigger": "minecraft:inventory_changed",
                "conditions": {"items": [{"items": [item]}]},
            },
            "has_the_recipe": {
                "trigger": "minecraft:recipe_unlocked",
                "conditions": {"recipe": "blackchannel:" + name},
            },
        },
        "requirements": [["has_item", "has_the_recipe"]],
        "rewards": {"recipes": ["blackchannel:" + name]},
    })


def recipes():
    shaped("burner_phone", [" C ", "RGR", " I "], {
        "C": "minecraft:copper_ingot", "R": "minecraft:redstone",
        "G": "minecraft:glass_pane", "I": "minecraft:iron_ingot",
    }, "blackchannel:burner_phone", unlock="minecraft:copper_ingot")
    shaped("field_laptop", ["IGI", "RPR", "III"], {
        "I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot",
        "R": "minecraft:redstone", "P": "blackchannel:burner_phone",
    }, "blackchannel:field_laptop", unlock="blackchannel:burner_phone")
    shaped("vpn_chip", [" R ", "IPI", " R "], {
        "R": "minecraft:redstone", "I": "minecraft:iron_nugget", "P": "minecraft:paper",
    }, "blackchannel:vpn_chip")
    shaped("deep_vpn", [" E ", "VDV", " E "], {
        "E": "minecraft:ender_pearl", "V": "blackchannel:vpn_chip", "D": "minecraft:diamond",
    }, "blackchannel:deep_vpn", unlock="blackchannel:vpn_chip")
    shaped("ids_module", [" R ", "OIO", " R "], {
        "R": "minecraft:redstone", "O": "minecraft:observer", "I": "minecraft:iron_ingot",
    }, "blackchannel:ids_module", unlock="minecraft:observer")
    shaped("emp_hardening", ["CIC", " R ", "CIC"], {
        "C": "minecraft:copper_ingot", "I": "minecraft:iron_ingot", "R": "minecraft:redstone_block",
    }, "blackchannel:emp_hardening", unlock="minecraft:copper_ingot")
    shaped("exploit_firmware", ["GPG", " R ", "GPG"], {
        "G": "minecraft:gold_nugget", "P": "minecraft:paper", "R": "minecraft:redstone",
    }, "blackchannel:exploit_firmware")
    shaped("zero_day", [" N ", "DED", " R "], {
        "N": "minecraft:netherite_scrap", "D": "minecraft:diamond",
        "E": "minecraft:ender_eye", "R": "minecraft:redstone_block",
    }, "blackchannel:zero_day", unlock="minecraft:netherite_scrap")
    shaped("false_contract", ["PPP", "GIG", "PPP"], {
        "P": "minecraft:paper", "G": "minecraft:gold_nugget", "I": "minecraft:ink_sac",
    }, "blackchannel:false_contract", unlock="minecraft:ink_sac")
    shaped("sticky_tracker", [" S ", "RIR", " S "], {
        "S": "minecraft:slime_ball", "R": "minecraft:redstone", "I": "minecraft:iron_nugget",
    }, "blackchannel:sticky_tracker", 2, unlock="minecraft:slime_ball")
    shaped("signal_scanner", [" R ", "ICI", " R "], {
        "R": "minecraft:redstone", "I": "minecraft:copper_ingot", "C": "minecraft:compass",
    }, "blackchannel:signal_scanner", unlock="minecraft:compass")
    shaped("battery_cell", [" R ", "CCC", " R "], {
        "R": "minecraft:redstone", "C": "minecraft:copper_ingot",
    }, "blackchannel:battery_cell", 2)
    shaped("burner_sim", [" R ", "IPI"], {
        "R": "minecraft:redstone", "I": "minecraft:iron_nugget", "P": "minecraft:paper",
    }, "blackchannel:burner_sim", 2)
    shaped("satellite_firmware", [" E ", "DRD", " E "], {
        "E": "minecraft:ender_eye", "D": "minecraft:diamond", "R": "minecraft:redstone_block",
    }, "blackchannel:satellite_firmware", unlock="minecraft:ender_eye")
    shaped("hashcoin", [" R ", " G ", " R "], {
        "R": "minecraft:redstone", "G": "minecraft:gold_ingot",
    }, "blackchannel:hashcoin", unlock="minecraft:gold_ingot")
    shaped("uplink_rack", ["IOI", "RBR", "III"], {
        "I": "minecraft:iron_ingot", "O": "minecraft:observer",
        "R": "minecraft:redstone", "B": "minecraft:redstone_block",
    }, "blackchannel:uplink_rack", unlock="minecraft:observer")
    shaped("sigint_dish", [" A ", "LRL", "III"], {
        "A": "minecraft:amethyst_shard", "L": "minecraft:lightning_rod",
        "R": "minecraft:redstone", "I": "minecraft:iron_ingot",
    }, "blackchannel:sigint_dish", unlock="minecraft:amethyst_shard")
    shaped("faraday_anchor", ["BIB", "IRI", "BIB"], {
        "B": "minecraft:iron_bars", "I": "minecraft:copper_ingot", "R": "minecraft:redstone",
    }, "blackchannel:faraday_anchor")
    shaped("signal_jammer", [" T ", "RIR", " C "], {
        "T": "minecraft:redstone_torch", "R": "minecraft:redstone",
        "I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot",
    }, "blackchannel:signal_jammer", unlock="minecraft:redstone_torch")
    shaped("decoy_beacon", [" G ", "RIR", " G "], {
        "G": "minecraft:gold_ingot", "R": "minecraft:redstone", "I": "minecraft:iron_ingot",
    }, "blackchannel:decoy_beacon", unlock="minecraft:gold_ingot")

    ed = [{"type": "forge:mod_loaded", "modid": "electrodynamics"}]
    shaped("burner_phone_ed", [" C ", "PBP", " P "], {
        "C": "electrodynamics:circuitbasic",
        "P": "electrodynamics:platecopper",
        "B": "electrodynamics:battery",
    }, "blackchannel:burner_phone", conditions=ed)
    shaped("uplink_rack_ed", ["CSC", "PBP", "CSC"], {
        "C": "electrodynamics:circuitadvanced",
        "S": "electrodynamics:platesteel",
        "P": "electrodynamics:platecopper",
        "B": "electrodynamics:battery",
    }, "blackchannel:uplink_rack", conditions=ed)


def lang():
    en = {}
    ru = {}

    def both(key, e, r):
        en[key] = e
        ru[key] = r

    names_en = {
        "uplink_rack": "Uplink Rack",
        "sigint_dish": "SIGINT Dish",
        "faraday_anchor": "Faraday Anchor",
        "signal_jammer": "Signal Jammer",
        "team_beacon": "Team Beacon",
        "decoy_beacon": "Decoy Beacon",
        "burner_phone": "Burner Phone",
        "field_laptop": "Field Laptop",
        "vpn_chip": "VPN Chip",
        "deep_vpn": "Deep VPN",
        "ids_module": "IDS Module",
        "emp_hardening": "EMP Hardening",
        "exploit_firmware": "Exploit Firmware",
        "zero_day": "Zero Day",
        "false_contract": "False Contract",
        "sticky_tracker": "Sticky Tracker",
        "signal_scanner": "Signal Scanner",
        "battery_cell": "Battery Cell",
        "burner_sim": "Burner SIM",
        "satellite_firmware": "Satellite Firmware",
        "hashcoin": "Hashcoin",
        "signal_sample": "Signal Sample",
    }
    names_ru = {
        "uplink_rack": "Стойка-аплинк",
        "sigint_dish": "Тарелка SIGINT",
        "faraday_anchor": "Клетка Фарадея",
        "signal_jammer": "Глушилка сигнала",
        "team_beacon": "Маяк отряда",
        "decoy_beacon": "Ложный маяк",
        "burner_phone": "Одноразовый телефон",
        "field_laptop": "Полевой ноутбук",
        "vpn_chip": "Чип VPN",
        "deep_vpn": "Глубокий VPN",
        "ids_module": "Модуль IDS",
        "emp_hardening": "Закалка ЭМИ",
        "exploit_firmware": "Прошивка эксплойта",
        "zero_day": "Нулевой день",
        "false_contract": "Фальшивый контракт",
        "sticky_tracker": "Липкий жучок",
        "signal_scanner": "Сканер сигнала",
        "battery_cell": "Элемент питания",
        "burner_sim": "Одноразовая SIM",
        "satellite_firmware": "Спутниковая прошивка",
        "hashcoin": "Хэш-коин",
        "signal_sample": "Образец сигнала",
    }
    for block in BLOCKS:
        both("block.blackchannel." + block, names_en[block], names_ru[block])
    for item in ITEMS:
        both("item.blackchannel." + item, names_en[item], names_ru[item])

    both("itemGroup.blackchannel", "Black Channel", "Чёрный канал")

    both("screen.blackchannel.phone", "Burner", "Телефон")
    both("screen.blackchannel.laptop", "Terminal", "Терминал")
    both("screen.blackchannel.rack", "Uplink", "Аплинк")
    both("screen.blackchannel.manual", "Guide", "Гайд")
    both("screen.blackchannel.rack_hint", "Coal, redstone, cell, or an FE item. Shift + empty hand: kill switch.",
         "Уголь, редстоун, элемент или предмет с FE. Шифт и пустая рука: гашение.")
    both("screen.blackchannel.send_hint", "Enter sends. @handle is a DM.", "Enter отправляет. @позывной — личка.")
    both("screen.blackchannel.tab.chat", "CHAT", "ЧАТ")
    both("screen.blackchannel.tab.radar", "RADAR", "РАДАР")
    both("screen.blackchannel.tab.market", "MARKET", "РЫНОК")
    both("screen.blackchannel.tab.gear", "GEAR", "СЕТЬ")
    both("screen.blackchannel.btn.airplane", "AIRPLANE", "АВИА")
    both("screen.blackchannel.btn.killswitch", "KILL", "ГАШЕНИЕ")
    both("screen.blackchannel.btn.manual", "GUIDE", "ГАЙД")
    both("screen.blackchannel.btn.radar_hud", "HUD", "МЕТКИ")
    both("screen.blackchannel.btn.hack", "HACK", "ВЗЛОМ")

    shops = {
        "cell": ("Cell", "Элемент"),
        "tracker": ("Tracker", "Жучок"),
        "scanner": ("Scanner", "Сканер"),
        "sim": ("SIM", "SIM"),
        "exploit": ("Exploit", "Эксплойт"),
        "contract": ("Contract", "Контракт"),
        "vpn": ("VPN", "VPN"),
        "ids": ("IDS", "IDS"),
        "harden": ("Harden", "Закалка"),
        "decoy": ("Decoy", "Ложный"),
        "faraday": ("Faraday", "Фарадей"),
        "jammer": ("Jammer", "Глушилка"),
        "sat": ("Sat link", "Спутник"),
        "zero": ("Zero day", "0-day"),
        "deep": ("Deep VPN", "Deep VPN"),
    }
    for k, (e, r) in shops.items():
        both("blackchannel.shop." + k, e, r)

    tips = {
        "phone": ("Right-click: terminal. Sneak: airplane mode.", "ПКМ — терминал. Шифт+ПКМ — авиарежим."),
        "laptop": ("Right-click: ops terminal. Sneak: radar HUD. Right-click a player to read a handle.",
                   "ПКМ — терминал. Шифт+ПКМ — радар в углу. ПКМ по игроку читает позывной."),
        "battery": ("Battery %s%%", "Батарея %s%%"),
        "tracker": ("Sneak-use on a player or block to plant a bug.", "Шифт+ПКМ по игроку или блоку — посадить жучок."),
        "scanner": ("Use on yourself or a block to hunt bugs.", "ПКМ по себе или по блоку — искать жучок."),
        "hash": ("Use to deposit. Sneak-use to withdraw up to 16.", "ПКМ — в кошелёк. Шифт+ПКМ — снять до 16."),
        "sim": ("Sneak-use to burn the handle and roll a new one.", "Шифт+ПКМ сжигает позывной и выдаёт новый."),
        "sat": ("Use to load 3 satellite charges. Works when no rack is nearby.",
                "ПКМ даёт 3 заряда спутника. Канал есть, даже если стойки рядом нет."),
        "vpn": ("Use to install VPN. Traces land off your body.", "ПКМ ставит VPN. След ложится мимо тела."),
        "vpn2": ("Use to install deep VPN. Traces often fail outright.", "ПКМ ставит глубокий VPN. След чаще срывается."),
        "ids": ("Use to install IDS. Attacks ping a bearing back.", "ПКМ ставит IDS. Атаки присылают пеленг."),
        "harden": ("Use to harden against EMP. Phone keeps a charge, racks drain less.",
                   "ПКМ ставит закалку. Телефон не умирает до нуля, стойки теряют меньше."),
        "exploit": ("Keep it in inventory. TRACE misses less. Required to hack a foreign rack.",
                    "Держите в инвентаре. TRACE реже промахивается. Нужен, чтобы взломать чужую стойку."),
        "zero": ("Keep it in inventory. DEANON ignores VPN and opens an encrypted sample.",
                 "Держите в инвентаре. DEANON обходит VPN и вскрывает шифрованный образец."),
        "contract": ("Keep it in inventory. A false fire mission costs 8 instead of 40.",
                     "Держите в инвентаре. Ложный вызов стоит 8, а не 40."),
        "sample": ("A captured handle. Use it to claim a bounty on that handle.",
                   "Снятый позывной. ПКМ сдаёт его, если на него висит награда."),
        "sample_enc": ("Encrypted. A zero-day in inventory decrypts it.", "Шифрован. Нулевой день в инвентаре вскрывает его."),
        "cell": ("Use to restore 60 battery, or feed a rack.", "ПКМ восстанавливает 60 батареи. Можно скормить стойке."),
    }
    for k, (e, r) in tips.items():
        both("tooltip.blackchannel." + k, e, r)

    both("blackchannel.hint.passive", "This item works from the inventory, not by right-click.",
         "Этот предмет работает из инвентаря, а не по ПКМ.")
    both("blackchannel.hint.sim_sneak", "Sneak-use the SIM to burn this handle.",
         "Шифт+ПКМ по SIM, чтобы сжечь позывной.")
    both("blackchannel.hint.tracker_sneak", "Sneak-use to plant the bug.",
         "Шифт+ПКМ, чтобы посадить жучок.")

    both("blackchannel.hud.emp", "NO CARRIER", "НЕТ НЕСУЩЕЙ")
    both("blackchannel.hud.strike", "INBOUND %ss  %s %s", "ПРИЛЁТ %s с  %s %s")
    both("blackchannel.spam.1", "UNKNOWN NUMBER", "НЕИЗВЕСТНЫЙ НОМЕР")
    both("blackchannel.spam.2", "CARRIER BUSY", "ЛИНИЯ ЗАНЯТА")
    both("blackchannel.spam.3", "PACKET FLOOD", "ПОТОК ПАКЕТОВ")
    both("blackchannel.spam.4", "CALLBACK FAILED", "ОБРАТНЫЙ ВЫЗОВ СОРВАН")
    both("blackchannel.spam.5", "SIM LOCK", "SIM ЗАБЛОКИРОВАНА")
    both("blackchannel.spam.6", "ROUTE LOOP", "ПЕТЛЯ МАРШРУТА")

    chats = {
        "welcome": ("Handle assigned: %s", "Позывной назначен: %s"),
        "emp": ("EMP. Terminals are dead.", "ЭМИ. Терминалы мертвы."),
        "bug_found": ("A bug on you just chirped.", "Жучок на вас только что пискнул."),
        "ids_trace": ("IDS: trace from %s.", "IDS: след с пеленга %s."),
        "ids_deanon": ("IDS: deanon attempt from %s.", "IDS: попытка деанона с пеленга %s."),
        "ids_bomb": ("IDS: flood from %s.", "IDS: флуд с пеленга %s."),
        "ids_bomb_soft": ("The line is noisy.", "Линия шумит."),
        "ids_bug": ("IDS: a bug was planted. Bearing %s.", "IDS: посадили жучок. Пеленг %s."),
        "ids_hack": ("Your rack was drained.", "Вашу стойку осушили."),
        "strike_target": ("False fire mission in %ss at %s %s %s (%s). Move.",
                          "Ложный вызов через %s с: %s %s %s (%s). Уходите."),
        "strike_near": ("Something is inbound in %ss near %s %s.", "Что-то летит через %s с рядом с %s %s."),
        "strike_impact": ("Impact %s %s %s.", "Прилёт %s %s %s."),
        "strike_abort": ("The fire mission aborted.", "Вызов сорван."),
    }
    for k, (e, r) in chats.items():
        both("blackchannel.chat." + k, e, r)

    results = {
        "air_off": ("Airplane off. You are on the air.", "Авиарежим выключен. Вы в эфире."),
        "air_on": ("Airplane on. You are dark.", "Авиарежим включён. Вас нет в эфире."),
        "airplane": ("Airplane mode.", "Включён авиарежим."),
        "already": ("Already installed.", "Уже стоит."),
        "bad_handle": ("Bad handle.", "Плохой позывной."),
        "beacon_set": ("Team beacon set to %s.", "Маяк отряда: %s."),
        "bomb_disabled": ("Flood is disabled on this server.", "Флуд выключен на этом сервере."),
        "bomb_locked": ("The handset is locked by the flood.", "Трубку забил флуд."),
        "bomb_ok": ("Flooding %s for %ss.", "Флуд %s на %s с."),
        "bought": ("Bought %s.", "Куплено: %s."),
        "charged": ("Battery topped up.", "Батарея пополнена."),
        "cooldown": ("Cooldown %ss.", "Перезарядка %s с."),
        "crew_set": ("Crew code %s.", "Код отряда %s."),
        "deanon_need": ("Need a trace, a sample, or a zero-day.", "Нужен след, образец или нулевой день."),
        "deanon_ok": ("%s is %s.", "%s — это %s."),
        "decoy_set": ("Decoy advertising %s.", "Ложный маяк светит %s."),
        "deposit": ("Deposited %s. Wallet #%s.", "Внесено %s. Кошелёк #%s."),
        "dnd_off": ("DND off.", "Не беспокоить выключено."),
        "dnd_on": ("DND on. Darknet messages are muted.", "Не беспокоить. Тёмный канал заглушён."),
        "drone_none": ("No friendly drone in range.", "Рядом нет своего дрона."),
        "drone_ok": ("UAV fix on %s: %s %s ±%s.", "UAV по %s: %s %s ±%s."),
        "emp": ("No carrier. EMP.", "Нет несущей. ЭМИ."),
        "faraday": ("Faraday cage. No leak.", "Клетка Фарадея. Утечки нет."),
        "fuel": ("Rack charge %s.", "Заряд стойки %s."),
        "hack_ok": ("Rack drained. Sample of %s.", "Стойка осушена. Образец %s."),
        "identify_fail": ("No handle on the air.", "В эфире нет позывного."),
        "identify_ok": ("Handle %s, last seen as %s.", "Позывной %s, последний ник %s."),
        "installed": ("Installed.", "Установлено."),
        "jammed": ("Jammed.", "Глушение."),
        "killswitch": ("Outbound missions killed.", "Исходящие вызовы погашены."),
        "loaded": ("Satellite charges: %s.", "Заряды спутника: %s."),
        "muted": ("They are not taking messages.", "Абонент не принимает."),
        "need_sim": ("Need a burner SIM.", "Нужна одноразовая SIM."),
        "no_battery": ("Battery dead.", "Батарея села."),
        "no_carrier": ("No carrier. They are dark or the fix is cold.", "Нет несущей. Абонент тёмный или отметка остыла."),
        "no_crypto": ("Need #%s.", "Нужно #%s."),
        "no_device": ("Need a phone or a laptop.", "Нужен телефон или ноутбук."),
        "no_room": ("The board is full.", "Доска объявлений полна."),
        "no_strike": ("Nothing outbound to kill.", "Гасить нечего."),
        "no_target": ("No such target.", "Нет такой цели."),
        "no_uplink": ("No uplink. Place a rack, or load satellite firmware.", "Нет аплинка. Поставьте стойку или зарядите спутник."),
        "node_off": ("Node dark. Charge %s.", "Узел тёмный. Заряд %s."),
        "node_on": ("Node live. Charge %s.", "Узел жив. Заряд %s."),
        "not_owner": ("Not your rack.", "Это не ваша стойка."),
        "plant_fail": ("The bug did not stick.", "Жучок не прилип."),
        "plant_ok": ("Bug planted.", "Жучок посажен."),
        "rack_charged_device": ("Handset charged from the rack.", "Трубка заряжена от стойки."),
        "radar_off": ("Radar HUD off.", "Метки выключены."),
        "radar_on": ("Radar HUD on.", "Метки включены."),
        "sample_encrypted": ("Sample is encrypted.", "Образец зашифрован."),
        "scan_none": ("No bug here.", "Жучка нет."),
        "scan_ok": ("Bug owned by %s.", "Жучок принадлежит %s."),
        "sent": ("Sent.", "Отправлено."),
        "sniff": ("Someone is on your local channel.", "Кто-то сидит на вашем местном канале."),
        "sold": ("Listed for #%s.", "Выставлено за #%s."),
        "swat_disabled": ("Fire missions are disabled.", "Ложные вызовы выключены."),
        "swat_need_intel": ("Select a stored fix first.", "Сначала выберите сохранённую отметку."),
        "swat_ok": ("Mission in %ss at %s %s. It hits the fix, not the body.",
                    "Вызов через %s с на %s %s. Бьёт по отметке, не по телу."),
        "trace_fail": ("Trace missed.", "След сорвался."),
        "trace_ok": ("%s @ %s %s %s %s ±%s", "%s @ %s %s %s %s ±%s"),
        "trace_stale": ("STALE %s @ %s %s %s %s ±%s", "СТАРЫЙ %s @ %s %s %s %s ±%s"),
        "vpn_blocks": ("VPN ate the deanon.", "VPN съел деанон."),
        "wipe_need_confirm": ("Press WIPE again to burn this handle.", "Нажмите WIPE ещё раз, чтобы сжечь позывной."),
        "wiped": ("Handle burned. New handle: %s.", "Позывной сожжён. Новый: %s."),
        "withdraw": ("Withdrew %s.", "Снято %s."),
    }
    for k, (e, r) in results.items():
        both("blackchannel.result." + k, e, r)

    both("blackchannel.cmd.help",
         "Black Channel: phone right-click, laptop TRACE/DEANON/SWAT/BOMB/UAV. /bc status, hooks, sethandle <name>. Ops: pulse, givehash, reveal.",
         "Чёрный канал: телефон ПКМ, ноутбук TRACE/DEANON/SWAT/BOMB/UAV. /bc status, hooks, sethandle <имя>. Операторам: pulse, givehash, reveal.")
    both("blackchannel.cmd.no_player", "A player must run this.", "Эту команду должен ввести игрок.")
    both("blackchannel.cmd.status",
         "handle %s  nick %s  #%s  VPN %s  bat %s  bars %s  link %s  air %s  emp %s  jam %s  cage %s",
         "позывной %s  ник %s  #%s  VPN %s  батарея %s  сигнал %s  канал %s  авиа %s  ЭМИ %s  глушение %s  клетка %s")
    both("blackchannel.cmd.pulse", "EMP pulse, radius %s.", "Импульс ЭМИ, радиус %s.")
    both("blackchannel.cmd.give", "Gave %s #%s.", "Выдано %s: #%s.")
    both("blackchannel.cmd.reveal", "%s handle %s at %s %s %s %s", "%s позывной %s в %s %s %s %s")
    both("blackchannel.cmd.handle", "Handle is now %s.", "Позывной теперь %s.")

    pages_en = [
        ("What this is",
         "Black Channel is an in-world SIGINT layer for your tech-war server. The phone and laptop are terminals. The mod does not read real IPs and does not leave the Minecraft world. A dox here is the last radio fix, not the player's body. The fix goes stale. A decoy and a Faraday cage lie to the radar. A Ballistix EMP, and any blast whose exploder name contains emp, kills the terminals."),
        ("Phone",
         "Right-click the burner to open chat, radar, market and gear. Sneak-use toggles airplane mode: you go dark and your fix stops updating. The battery drains. Charge it with a cell, or right-click the phone on a rack. A handle is assigned automatically. Change it with /bc sethandle name. Type @name for a DM. DARK needs an uplink. LOCAL is heard only nearby, with no rack."),
        ("Network",
         "An uplink rack carries the dark channel nearby. Fuel it with coal, redstone, a battery cell, or any item that exposes Forge Energy. A SIGINT dish stretches the link and tightens traces. A jammer cuts foreign traffic. Sneak with an empty hand on your rack to kill your outbound missions. That costs #200."),
        ("Trace",
         "On the laptop, type a handle and press TRACE. If they were on the air recently you get coordinates and an error radius, not a live body lock. VPN shifts the point. Deep VPN often returns nothing. IDS sends them a bearing on you. Cost is #2. The scanner hunts bugs on you and on blocks. Right-click a player with the laptop to read a handle if they are not in airplane mode."),
        ("Deanon",
         "DEANON binds a handle to a Minecraft name if you already have a trace, a signal sample, or a zero-day. It is a nickname, not a real person. The list sits on the right of the laptop and in the phone gear tab. VPN blocks it unless a zero-day is in your inventory. Cost is #4. A target with IDS gets a bearing."),
        ("False fire mission",
         "SWAT hits the selected stored fix after a delay. It does not follow the body. Pick an INTEL row, then press SWAT. The target sees the timer and the coordinates and can leave. The blast is small, not a nuke. EMP on the aim point or on the operator aborts it. Block breaking is a config switch. Cost is #40, or #8 with a false contract in inventory."),
        ("Flood and radar",
         "BOMB spams the target's screen while their phone is on. Airplane mode cuts it. The phone radar is short. The laptop radar is longer. The HUD button draws blips in the corner. Bugs and team beacons have their own colors. A decoy paints someone else's handle. Superb Warfare drones show up if that mod is loaded. UAV takes a fix from the nearest drone you can see."),
        ("Defense and EMP",
         "Right-click a VPN chip, deep VPN, IDS or hardening to install it. Hardening keeps some phone battery and drains racks less. A Faraday anchor hides a fix inside it. Airplane mode takes you off the air. A Ballistix EMP and an emp-named blast shut phones, laptops and racks in the radius and abort hanging missions. The EW EMP gun, used on a drone, leaves a small collateral pulse."),
        ("Market and bugs",
         "Right-click hashcoin to deposit. Sneak-use to withdraw. A charged rack mints coins. The phone market sells chips. On the laptop, SELL INTEL lists the selected fix and SELL ITEM lists the held item. Put $price in the box to set the price. Sneak-use a tracker on a player or a block. Right-click a signal sample to claim a bounty on that handle."),
        ("Commands",
         "/bc help, status, hooks. Match: /minigame center, size, team, start, stop. size is wall to wall, 2500 for 2500x2500. Pick a team in /minigame team before the start. Config: config/blackchannel-common.toml."),
    ]
    pages_ru = [
        ("Что это",
         "Чёрный канал — слой радиоразведки внутри вашего тех-сервера. Телефон и ноутбук — терминалы. Мод не читает настоящие IP и не выходит из мира. Dox здесь — последняя радиоотметка, а не тело игрока. Отметка стареет. Ложный маяк и клетка Фарадея врут радару. ЭМИ Ballistix и взрыв, в имени которого есть emp, гасят терминалы."),
        ("Телефон",
         "ПКМ по телефону открывает чат, радар, рынок и сеть. Шифт+ПКМ включает авиарежим: вас нет в эфире, отметка не обновляется. Батарея садится. Заряд — элемент питания или ПКМ телефоном по стойке. Позывной выдаётся сам. Смена: /bc sethandle имя. В строке @имя — личка. DARK идёт через аплинк. LOCAL слышно только рядом, стойка не нужна."),
        ("Сеть",
         "Стойка-аплинк держит тёмный канал рядом. Топливо: уголь, редстоун, элемент питания или предмет с Forge Energy. Тарелка SIGINT тянет связь и сужает погрешность следа. Глушилка режет чужой канал. Шифт и пустая рука по своей стойке гасит ваши исходящие вызовы. Это стоит #200."),
        ("След",
         "На ноутбуке впишите позывной и нажмите TRACE. Если цель недавно была в сети, вы получите координаты и погрешность, а не захват тела. VPN сдвигает точку. Глубокий VPN чаще отдаёт пусто. IDS шлёт жертве пеленг на вас. Цена #2. Сканер ищет жучки на себе и на блоке. ПКМ ноутбуком по игроку читает позывной, если нет авиарежима."),
        ("Деанон",
         "DEANON связывает позывной с ником Minecraft, если уже есть след, образец сигнала или нулевой день. Это ник, не настоящий человек. Список справа на ноутбуке и во вкладке сети телефона. VPN блокирует деанон, пока в инвентаре нет нулевого дня. Цена #4. Жертва с IDS получает пеленг."),
        ("Ложный вызов",
         "SWAT бьёт по выбранной сохранённой отметке после задержки. Он не следует за телом. Выберите строку INTEL и нажмите SWAT. Цель видит таймер и координаты и может уйти. Взрыв небольшой, не ядерный. ЭМИ по точке или по оператору срывает вызов. Поломку блоков можно выключить в конфиге. Цена #40, или #8, если в инвентаре фальшивый контракт."),
        ("Флуд и радар",
         "BOMB забивает экран, пока телефон цели включён. Авиарежим обрывает флуд. Радар телефона короткий, ноутбука длиннее. Кнопка меток рисует точки в углу экрана. Жучок и маяк отряда светятся своим цветом. Ложный маяк рисует чужой позывной. Дроны Superb Warfare видны, если мод стоит. UAV снимает отметку с ближайшего видимого дрона."),
        ("Защита и ЭМИ",
         "ПКМ по чипу VPN, глубокому VPN, IDS или закалке ставит защиту. Закалка оставляет телефону заряд и бережёт стойки. Клетка Фарадея прячет след внутри. Авиарежим убирает вас из эфира. ЭМИ Ballistix и взрыв с emp в имени гасят телефоны, ноутбуки и стойки в радиусе и рвут висящие вызовы. ЭМИ-пушка EW по дрону даёт маленький побочный импульс."),
        ("Рынок и жучки",
         "ПКМ по хэш-коину кладёт его в кошелёк. Шифт+ПКМ снимает. Заряженная стойка чеканит монеты. Рынок телефона продаёт чипы. На ноутбуке SELL INTEL продаёт выбранный след, SELL ITEM — предмет в руке. Цена: $число в строке. Жучок клеится шифтом на игрока или блок. ПКМ по образцу сигнала сдаёт награду на этот позывной."),
        ("Команды",
         "/bc help, status, hooks. Катка: /minigame center, size, team, start, stop. size — от стены до стены, для 2500×2500 пиши 2500. Команда выбирается в меню /minigame team до старта. Конфиг: config/blackchannel-common.toml."),
    ]
    for i, ((et, eb), (rt, rb)) in enumerate(zip(pages_en, pages_ru)):
        both(f"manual.blackchannel.{i}.t", et, rt)
        both(f"manual.blackchannel.{i}.b", eb, rb)
        if len(eb) > 520 or len(rb) > 520:
            print("LONG PAGE", i, len(eb), len(rb))

    # Готовые файлы важнее генератора: строки правятся руками, а скрипт лишь дописывает новое.
    for path, table in ((os.path.join(RES, "assets/blackchannel/lang/en_us.json"), en),
                        (os.path.join(RES, "assets/blackchannel/lang/ru_ru.json"), ru)):
        merged = {}
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                merged.update(json.load(f))
        for key, value in table.items():
            merged.setdefault(key, value)
        dump(path, merged)
    print("lang", len(en), len(ru))
    return set(en)


def main():
    models()
    recipes()
    keys = lang()
    print("models+recipes+lang written", len(keys))


if __name__ == "__main__":
    main()
