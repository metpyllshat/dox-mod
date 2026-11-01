#!/usr/bin/env python3
"""
Экономика лута Black Channel: что из чего крафтится и сколько базовых ресурсов
надо на матч. Скрипт читает jar'ы модов из корня репозитория и рецепты самого мода,
разворачивает их до сырья и строит таблицу спроса и график.

Запуск:  python3 docs/loot_stats.py
Выход:   docs/loot-stats.md, docs/loot-demand.png
"""
import glob
import json
import math
import os
import re
import zipfile
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_MD = os.path.join(ROOT, 'docs', 'loot-stats.md')
OUT_PNG = os.path.join(ROOT, 'docs', 'loot-demand.png')

# Ванильные блоки и предметы, которых нет в jar'ах модов, но которые нужны как сырьё
VANILLA = {
    'minecraft:iron_block': {'minecraft:iron_ingot': 9},
    'minecraft:copper_block': {'minecraft:copper_ingot': 9},
    'minecraft:gold_block': {'minecraft:gold_ingot': 9},
    'minecraft:redstone_block': {'minecraft:redstone': 9},
    'minecraft:coal_block': {'minecraft:coal': 9},
    'minecraft:lapis_block': {'minecraft:lapis_lazuli': 9},
    'minecraft:diamond_block': {'minecraft:diamond': 9},
    'minecraft:netherite_block': {'minecraft:netherite_ingot': 9},
    'minecraft:netherite_ingot': {'minecraft:netherite_scrap': 4, 'minecraft:gold_ingot': 4},
    'minecraft:copper_ingot': {'minecraft:raw_copper': 1},
    'minecraft:iron_ingot': {'minecraft:raw_iron': 1},
    'minecraft:gold_ingot': {'minecraft:raw_gold': 1},
    'minecraft:glass': {'minecraft:sand': 1},
    'minecraft:glass_pane': {'minecraft:glass': 1},
    'minecraft:observer': {'minecraft:cobblestone': 6, 'minecraft:redstone': 2, 'minecraft:quartz': 1},
    'minecraft:piston': {'minecraft:cobblestone': 4, 'minecraft:iron_ingot': 1, 'minecraft:redstone': 1, 'minecraft:logs': 1},
    'minecraft:hopper': {'minecraft:iron_ingot': 5, 'minecraft:chest': 1},
    'minecraft:chest': {'minecraft:logs': 1},
    'minecraft:crafting_table': {'minecraft:logs': 1},
    'minecraft:furnace': {'minecraft:cobblestone': 8},
    'minecraft:repeater': {'minecraft:cobblestone': 3, 'minecraft:redstone': 2},
    'minecraft:redstone_torch': {'minecraft:redstone': 1, 'minecraft:stick': 1},
    'minecraft:stick': {'minecraft:logs': 0.25},
    'minecraft:torch': {'minecraft:coal': 0.25, 'minecraft:stick': 1},
    'minecraft:lightning_rod': {'minecraft:copper_ingot': 3},
    'minecraft:iron_bars': {'minecraft:iron_ingot': 0.375},
    'minecraft:bucket': {'minecraft:iron_ingot': 3},
    'minecraft:cauldron': {'minecraft:iron_ingot': 7},
    'minecraft:anvil': {'minecraft:iron_block': 3, 'minecraft:iron_ingot': 4},
    'minecraft:dispenser': {'minecraft:cobblestone': 7, 'minecraft:redstone': 1, 'minecraft:stick': 1},
    'minecraft:dropper': {'minecraft:cobblestone': 7, 'minecraft:redstone': 1},
    'minecraft:red_dye': {'minecraft:poppy': 1},
    'minecraft:sugar_cane': {'minecraft:sugar_cane': 1},
    'minecraft:charcoal': {'minecraft:charcoal': 1},
    'minecraft:leather': {'minecraft:leather': 1},
    # Сталь Electrodynamics: одна прокалка железа, без машин
    'electrodynamics:ingotsteel': {'minecraft:iron_ingot': 1},
    'minecraft:sugar': {'minecraft:sugar_cane': 1},
    'minecraft:lapis_block': {'minecraft:lapis_lazuli': 9},
    'minecraft:tin_ingot': {'minecraft:raw_tin': 1},
    'minecraft:tnt': {'minecraft:gunpowder': 5, 'minecraft:sand': 4},
    'minecraft:lava_bucket': {'minecraft:lava_bucket': 1},
    'electrodynamics:ingottin': {'electrodynamics:raworetin': 1},
}

# Теги, которых нет в jar'ах: ваниль и общие forge-теги. Значение — список предметов.
MANUAL_TAGS = {
    'minecraft:logs': ['minecraft:logs'],
    'minecraft:planks': ['minecraft:planks'],
    'minecraft:stone_crafting_materials': ['minecraft:cobblestone'],
    'minecraft:coals': ['minecraft:coal'],
    'forge:ingots/iron': ['minecraft:iron_ingot'],
    'forge:ingots/copper': ['minecraft:copper_ingot'],
    'forge:ingots/gold': ['minecraft:gold_ingot'],
    # В сборке два вида стали: дешёвая electrodynamics (прокалка железа) и дорогая SBW из пыли.
    'forge:ingots/steel': ['electrodynamics:ingotsteel'],
    'forge:ingots/lead': ['superbwarfare:lead_ingot'],
    'forge:gunpowder': ['minecraft:gunpowder'],
    'forge:dusts/gunpowder': ['minecraft:gunpowder'],
    'forge:dusts/redstone': ['minecraft:redstone'],
    'forge:dusts/glowstone': ['minecraft:glowstone_dust'],
    'forge:nuggets/iron': ['minecraft:iron_nugget'],
    'forge:nuggets/gold': ['minecraft:gold_nugget'],
    'forge:flint': ['minecraft:flint'],
    'forge:sand': ['minecraft:sand'],
    'forge:glass': ['minecraft:glass'],
    'forge:glass_panes': ['minecraft:glass_pane'],
    'forge:paper': ['minecraft:paper'],
    'forge:ender_pearls': ['minecraft:ender_pearl'],
    'forge:diamonds': ['minecraft:diamond'],
    'forge:storage_blocks/iron': ['minecraft:iron_block'],
    'forge:storage_blocks/copper': ['minecraft:copper_block'],
    'forge:storage_blocks/redstone': ['minecraft:redstone_block'],
    'forge:storage_blocks/coal': ['minecraft:coal_block'],
    'forge:ores/iron': ['minecraft:iron_ore'],
    'forge:ores/copper': ['minecraft:copper_ore'],
    'forge:raw_materials/iron': ['minecraft:raw_iron'],
    'forge:raw_materials/copper': ['minecraft:raw_copper'],
    'forge:string': ['minecraft:string'],
    'forge:slimeballs': ['minecraft:slime_ball'],
    'forge:rods/wooden': ['minecraft:stick'],
    'forge:chests/wooden': ['minecraft:chest'],
    'forge:obsidian': ['minecraft:obsidian'],
    'forge:gems/quartz': ['minecraft:quartz'],
    'forge:gems/diamond': ['minecraft:diamond'],
    'forge:dusts/sulfur': ['electrodynamics:dustsulfur'],
    'forge:dusts/niter': ['electrodynamics:dustniter'],
    'forge:circuits/basic': ['electrodynamics:circuitbasic'],
    'forge:circuits/advanced': ['electrodynamics:circuitadvanced'],
    'forge:circuits/elite': ['electrodynamics:circuitelite'],
    'forge:circuits/ultimate': ['electrodynamics:circuitultimate'],
    'forge:plates/steel': ['electrodynamics:platesteel'],
    'forge:plates/copper': ['electrodynamics:platecopper'],
    'forge:plates/iron': ['electrodynamics:plateiron'],
    'forge:plates/lead': ['electrodynamics:platelead'],
    'forge:plates/tin': ['electrodynamics:platetin'],
    'forge:plates/bronze': ['electrodynamics:platebronze'],
    'forge:leather': ['minecraft:leather'],
    'forge:storage_blocks/lapis': ['minecraft:lapis_block'],
    'forge:dyes/red': ['minecraft:red_dye'],
    'forge:dyes': ['minecraft:red_dye'],
    'forge:glow_ink_sacs': ['minecraft:glow_ink_sac'],
    'forge:ender_eyes': ['minecraft:ender_eye'],
    'forge:feathers': ['minecraft:feather'],
}

# Сырьё, которое в игре берут из мира, а не крафтят: рекурсию на нём останавливаем,
# даже если в каком-то моде есть альтернативный рецепт (порох из серы и натра и т.п.)
TERMINAL = {
    'minecraft:copper_ingot', 'minecraft:iron_ingot', 'minecraft:gold_ingot', 'minecraft:coal',
    'minecraft:redstone', 'minecraft:gunpowder', 'minecraft:quartz', 'minecraft:diamond',
    'minecraft:lapis_lazuli', 'minecraft:netherite_scrap', 'minecraft:ender_pearl',
    'minecraft:logs', 'minecraft:cobblestone', 'minecraft:sand', 'minecraft:flint',
    'minecraft:leather', 'minecraft:paper', 'minecraft:sugar', 'minecraft:slime_ball',
    'minecraft:string', 'minecraft:amethyst_shard', 'minecraft:clay_ball', 'minecraft:poppy',
    'minecraft:feather', 'minecraft:glow_ink_sac', 'minecraft:obsidian', 'minecraft:stick',
    'superbwarfare:lead_ingot', 'superbwarfare:steel_ingot', 'superbwarfare:cemented_carbide_ingot',
    'superbwarfare:tungsten_ingot', 'superbwarfare:silver_ingot', 'superbwarfare:raw_galena',
    'electrodynamics:dustsulfur', 'electrodynamics:dustniter', 'electrodynamics:tin_ingot',
    'electrodynamics:lead_ingot', 'electrodynamics:silver_ingot', 'electrodynamics:aluminum_ingot',
    'electrodynamics:uranium_ingot', 'electrodynamics:lithium_ingot', 'electrodynamics:bronze_ingot',
    'nuclearscience:lead_ingot',
}

# Русские названия сырья для таблиц
RU = {
    'minecraft:copper_ingot': 'Медь',
    'minecraft:iron_ingot': 'Железо',
    'minecraft:gunpowder': 'Порох',
    'minecraft:redstone': 'Редстоун',
    'minecraft:gold_ingot': 'Золото',
    'minecraft:coal': 'Уголь',
    'minecraft:raw_copper': 'Медное сырьё',
    'minecraft:raw_iron': 'Железное сырьё',
    'minecraft:logs': 'Дерево',
    'minecraft:iron_nugget': 'Железный самородок',
    'minecraft:gold_nugget': 'Золотой самородок',
    'minecraft:flint': 'Кремень',
    'minecraft:sand': 'Песок',
    'minecraft:diamond': 'Алмаз',
    'minecraft:lapis_lazuli': 'Лазурит',
    'minecraft:netherite_ingot': 'Незерит',
    'minecraft:netherite_scrap': 'Незеритовый скрап',
    'minecraft:ender_pearl': 'Эндер-жемчуг',
    'minecraft:ender_eye': 'Око Эндера',
    'minecraft:paper': 'Бумага',
    'minecraft:sugar': 'Сахар',
    'minecraft:clay_ball': 'Глина',
    'minecraft:amethyst_shard': 'Аметист',
    'minecraft:quartz': 'Кварц',
    'minecraft:string': 'Нить',
    'minecraft:slime_ball': 'Слизь',
    'minecraft:lead': 'Свинец',
    'superbwarfare:lead_ingot': 'Свинец (SBW)',
    'superbwarfare:steel_ingot': 'Сталь (SBW)',
    'superbwarfare:cemented_carbide_ingot': 'Карбид (SBW)',
    'superbwarfare:tungsten_ingot': 'Вольфрам (SBW)',
    'superbwarfare:silver_ingot': 'Серебро (SBW)',
    'superbwarfare:raw_galena': 'Галенит (SBW)',
    'electrodynamics:copper_ingot': 'Медь (Electrodynamics)',
    'electrodynamics:steel_ingot': 'Сталь (Electrodynamics)',
    'electrodynamics:tin_ingot': 'Олово',
    'electrodynamics:bronze_ingot': 'Бронза',
    'nuclearscience:lead_ingot': 'Свинец (Nuclear)',
    'ballistix:poisonpowder': 'Яд (Ballistix)',
    'minecraft:cobblestone': 'Булыжник',
    'minecraft:iron_nugget': 'Железный самородок',
    'minecraft:paper': 'Бумага',
    'minecraft:string': 'Нить',
    'minecraft:clock': 'Часы',
    'minecraft:lever': 'Рычаг',
    'minecraft:glow_ink_sac': 'Светящиеся чернила',
    'minecraft:lime_stained_glass_pane': 'Стекло (зелёное)',
    'minecraft:wool_carpets': 'Ковёр',
    'minecraft:red_dye': 'Красный краситель',
    'minecraft:poppy': 'Мак',
    '#minecraft:wool_carpets': 'Ковёр',
    '#forge:dyes/black': 'Чёрный краситель',
    'electrodynamics:wireinsulatedcopperblack': 'Провод медный',
    'electrodynamics:compositeplating': 'Композитная плита',
    'electrodynamics:sheetplastic': 'Лист пластика',
    'electrodynamics:wireinsulatedcoppered': 'Провод медный (красный)',
    'electrodynamics:dustiron': 'Пыль железа',
    'minecraft:tnt': 'ТНТ',
    'minecraft:rotten_flesh': 'Гнилая плоть',
    'minecraft:lava_bucket': 'Ведро лавы',
    'minecraft:water_bucket': 'Ведро воды',
    'ballistix:poisonpowder': 'Ядовитый порошок',
    'electrodynamics:dustsulfur': 'Серная пыль',
    'minecraft:sand': 'Песок',
    'minecraft:quartz': 'Кварц',
}

# Добавляем недостающие русские подписи по ключевым словам
RU_HINT = [
    ('copper', 'Медь'), ('iron', 'Железо'), ('gold', 'Золото'), ('steel', 'Сталь'),
    ('lead', 'Свинец'), ('tin', 'Олово'), ('bronze', 'Бронза'), ('tungsten', 'Вольфрам'),
    ('silver', 'Серебро'), ('nickel', 'Никель'), ('zinc', 'Цинк'), ('aluminum', 'Алюминий'),
    ('circuit', 'Схема'), ('plate', 'Пластина'), ('wire', 'Провод'),
]


def fmt(v):
    if v >= 10:
        return f'{v:.0f}'
    if v >= 1:
        return f'{v:.1f}'.rstrip('0').rstrip('.')
    return f'{v:.2f}'.rstrip('0').rstrip('.')


def pretty(item):
    if item in RU:
        return RU[item]
    path = item.split(':')[-1]
    words = path.replace('_', ' ')
    for key, word in RU_HINT:
        if key in path:
            return f'{word} ({item.split(":")[0]})'
    return words


def load(jars):
    recipes, tags, names = [], {}, set()
    for jar in jars:
        z = zipfile.ZipFile(jar)
        for n in z.namelist():
            if not n.endswith('.json') or '/advancements/' in n:
                continue
            if '/recipes/' not in n and '/tags/' not in n:
                continue
            try:
                txt = re.sub(r'^\s*//[^\n]*$', '', z.read(n).decode('utf-8', 'ignore'), flags=re.M)
                data = json.loads(txt)
            except Exception:
                continue
            if '/recipes/' in n:
                recipes.append(data)
            elif n.startswith('data/') and '/tags/' in n:
                # data/<ns>/tags/items/<path>.json → <ns>:<path>
                ns = n.split('/', 2)[1]
                rest = n.split('/tags/', 1)[1]
                kind, rel = rest.split('/', 1)
                if kind == 'items':
                    values = [v if isinstance(v, str) else v.get('id') for v in data.get('values', [])]
                    tags.setdefault(ns + ':' + rel[:-5], []).extend(v for v in values if v)
    local = os.path.join(ROOT, 'blackchannel/src/main/resources/data')
    for path in glob.glob(os.path.join(local, '**', '*.json'), recursive=True):
        rel = path.replace('\\', '/')
        try:
            data = json.load(open(path, encoding='utf-8'))
        except Exception:
            continue
        if '/recipes' in rel:
            recipes.append(data)
        elif '/tags/items' in rel:
            ns = rel.split('/data/', 1)[1].split('/', 1)[0]
            tag_id = ns + ':' + rel.split('/tags/items/', 1)[1][:-5]
            values = [v if isinstance(v, str) else v.get('id') for v in data.get('values', [])]
            tags.setdefault(tag_id, []).extend(v for v in values if v)
    for tag_id, values in MANUAL_TAGS.items():
        tags[tag_id] = list(values)
    return recipes, tags


def split_id(raw):
    """az: 'minecraft:iron_ingot' → (ns, path)."""
    if ':' in raw:
        return tuple(raw.split(':', 1))
    return 'minecraft', raw


def ingredient_pairs(value):
    """Разбор одного ингредиента рецепта в список альтернатив."""
    if isinstance(value, str):
        return [value]
    if isinstance(value, list):
        return sum((ingredient_pairs(v) for v in value), [])
    if isinstance(value, dict):
        if 'item' in value:
            return [value['item']]
        if 'tag' in value:
            return ['#' + value['tag']]
        if 'items' in value:
            return sum((ingredient_pairs(v) for v in value['items']), [])
    return []


def recipe_entries(recipe):
    """[(результат, количество, [(альтернативы, количество)...])]"""
    rtype = recipe.get('type', '')
    out = []
    if rtype in ('minecraft:crafting_shaped',):
        key = recipe.get('key', {})
        counts = Counter()
        for row in recipe.get('pattern', []):
            for ch in row:
                if ch == ' ':
                    continue
                counts[ch] += 1
        ing = []
        for ch, n in counts.items():
            ing.append((ingredient_pairs(key.get(ch, [])), n))
        out.append((recipe['result'].get('item'), recipe['result'].get('count', 1), ing))
    elif rtype == 'minecraft:crafting_shapeless':
        ing = [(ingredient_pairs(v), 1) for v in recipe.get('ingredients', [])]
        out.append((recipe['result'].get('item'), recipe['result'].get('count', 1), ing))
    elif rtype == 'tacz:gun_smith_table_crafting':
        ing = [(ingredient_pairs(m.get('item')), m.get('count', 1)) for m in recipe.get('materials', [])]
        res = recipe.get('result', {})
        out.append((res.get('id'), res.get('count', 1), ing))
    elif rtype in ('minecraft:smelting', 'minecraft:blasting', 'minecraft:smoking', 'minecraft:campfire_cooking'):
        ing = [(ingredient_pairs(recipe.get('ingredient')), 1)]
        res = recipe.get('result')
        if isinstance(res, dict):
            out.append((res.get('item'), res.get('count', 1), ing))
        else:
            out.append((res, 1, ing))
    return out


class Economy:
    def __init__(self, recipes, tags):
        self.tags = tags
        self.variants = defaultdict(list)  # item -> [(count_out, [(alts, n)])]
        for r in recipes:
            for item, count, ing in recipe_entries(r):
                if not item:
                    continue
                self.variants[item].append((max(1, count), ing))
        # Ванильные цепочки (блок → слитки, руда → слиток): только если своего рецепта нет
        for item, parts in VANILLA.items():
            if self.variants.get(item):
                continue
            self.variants[item].append((1, [([alt], n) for alt, n in parts.items()]))
        self.cache = {}

    def resolve(self, alt):
        """
        Одна альтернатива → конкретный id. Тег раскрывается в один канонический предмет:
        сначала свой для неймспейса тега, потом minecraft, потом первый нормальный.
        Иначе в «самый дешёвый» вариант попадает мусор из чужих датапаков.
        """
        if not alt.startswith('#'):
            return alt
        tag = alt[1:]
        members = [m for m in self.tags.get(tag, []) if isinstance(m, str)]
        if not members:
            return alt
        members = [m for m in members if not m.startswith('#')] or [self.resolve(m) for m in members]
        ns = tag.split(':', 1)[0]
        def score(member):
            mns = member.split(':', 1)[0]
            if mns == ns:
                return 0
            if mns == 'minecraft':
                return 1
            if mns in ('forge', 'electrodynamics', 'superbwarfare', 'nuclearscience', 'ballistix'):
                return 2
            return 3
        members.sort(key=score)
        return members[0]

    def cost(self, item, depth=0):
        """Цена в единицах сырья: {сырьё: количество}."""
        if item in self.cache:
            return self.cache[item]
        if item in TERMINAL:
            self.cache[item] = {item: 1}
            return self.cache[item]
        if depth > 8 or item.startswith('#'):
            return {item: 1}
        options = self.variants.get(item)
        if not options:
            result = {item: 1}
            self.cache[item] = result
            return result
        best = None
        for count_out, ing in options:
            need = defaultdict(float)
            for alts, n in ing:
                if not alts:
                    continue
                concrete = self.resolve(alts[0])
                for k, v in self.cost(concrete, depth + 1).items():
                    need[k] += v * n / count_out
            if best is None or sum(need.values()) < sum(best.values()):
                best = need
        result = {k: round(v, 3) for k, v in (best or {}).items() if v > 0}
        self.cache[item] = result
        return result

    def batch_cost(self, item):
        """Цена одного крафта целиком: сколько сырья уходит на весь выход рецепта."""
        unit = self.cost(item)
        options = self.variants.get(item) or [(1, [])]
        count_out = max(1, options[0][0])
        return {k: v * count_out for k, v in unit.items()}


# Сценарий на одного игрока. Обязательное — без этого катка не играется,
# полезное — то, что реально крафтят в первые полчаса, эндгейм — если сильно захотеть.
BASKETS = {
    'Обязательное': {
        'tacz:gun_smith_table': 1,
        'blackchannel:uplink_rack': 1,
        'blackchannel:battery_cell': 3,
    },
    'Полезное': {
        'blackchannel:ids_module': 1,
        'blackchannel:trace_logger': 1,
        'blackchannel:burner_sim': 2,
        'superbwarfare:medical_kit': 2,
        'superbwarfare:c4_bomb': 2,
    },
    'Эндгейм': {
        'electrodynamics:nightvisiongoggles': 1,
        'electrodynamics:jetpack': 1,
        'electrodynamics:compositearmorchestplate': 1,
        'electrodynamics:compositearmorhelmet': 1,
    },
}

# Патроны: два-три боя по 100 выстрелов на игрока. Считаются в штуках, не в крафтах.
AMMO = {'tacz:9mm': 150, 'tacz:556x45': 120, 'tacz:12g': 24, 'tacz:762x39': 50}

# Сколько базовых единиц каждого ресурса даёт один схрон при текущих весах
SCENARIO_PLAYERS = 8
SCENARIO_CACHES = 21
CACHE_ITEMS = 4.5  # среднее между cacheItemsMin 3 и cacheItemsMax 6

# Ресурсы, которые реально лимитируют игру: их и сравниваем со схронами
BOTTLENECK = [
    'minecraft:copper_ingot', 'minecraft:iron_ingot', 'minecraft:gunpowder',
    'minecraft:redstone', 'minecraft:coal', 'minecraft:quartz',
    'superbwarfare:steel_ingot', 'superbwarfare:lead_ingot', 'electrodynamics:tin_ingot',
]


def parse_pools():
    """Пулы из Config.java: имя → [(id, минимум, максимум, вес, nbt)]."""
    path = os.path.join(ROOT, 'blackchannel/src/main/java/dev/blackchannel/Config.java')
    src = open(path, encoding='utf-8').read()
    pools = {}
    for m in re.finditer(r'CACHE_(EARLY|MID|LATE|AIRDROP|DUNGEON) = b\.defineList\("(\w+)", List\.of\((.*?)\)\, o -> o instanceof String\);', src, re.S):
        entries = []
        for raw in re.findall(r'"([^"]+)"', m.group(3)):
            weight = 10
            spec = raw
            if '@' in spec:
                spec, w = spec.rsplit('@', 1)
                weight = int(w)
            if '{' in spec:
                spec = spec[:spec.index('{')] + spec[spec.index('}') + 1:]
            lo = hi = 1
            if '*' in spec:
                spec, cnt = spec.split('*', 1)
                if '-' in cnt:
                    lo, hi = (int(v) for v in cnt.split('-', 1))
                else:
                    lo = hi = int(cnt)
            entries.append((spec.strip(), lo, hi, weight))
        pools[m.group(1)] = entries
    return pools


# Сколько схронов какой волны и сколько лута: модель на катку 4 отряда × 2 игрока, карта 1000
# Рабочая катка: 90 минут, 8 игроков, 4 отряда, карта 1000. Схроны при такой длине — 24.
MATCH_MODEL = {
    'caches': 24,
    'waveShare': {'EARLY': 0.55, 'MID': 0.30, 'LATE': 0.15},
    'itemsPerCache': 4.5,
    'airdrops': 4,
    'airdropItems': 5,
    'players': 8,
}

# Длины каток, для которых считаем «что успеешь собрать»
MATCH_MINUTES = (90, 120)
# Доля катки, когда игрок реально может копать, а не воевать
ECONOMY_SHARE = 0.4


def caches_for(minutes, teams=4, side=1000):
    """Повторяет Minigame.cacheCount: база по отрядам и карте, длина под корнем."""
    base = 3.5 * teams * side / 1000.0
    return max(8, min(56, round(base * math.sqrt(minutes / 30.0))))


def match_budget(eco, pools, minutes, players=8):
    """Часовой бюджет шахты × доля времени + доля лута со схронов на игрока."""
    saved = MATCH_MODEL['caches']
    MATCH_MODEL['caches'] = caches_for(minutes)
    got = supply(eco, pools)
    MATCH_MODEL['caches'] = saved
    hours = minutes / 60.0
    budget = {k: v * hours * ECONOMY_SHARE + got.get(k, 0.0) / players for k, v in HOUR_BUDGET.items()}
    return budget, got


def supply(eco, pools):
    """Сколько базового сырья приносят схроны за одну катку."""
    got = defaultdict(float)
    for wave, share in MATCH_MODEL['waveShare'].items():
        picks = MATCH_MODEL['caches'] * share * MATCH_MODEL['itemsPerCache']
        entries = pools.get(wave, [])
        total = sum(e[3] for e in entries) or 1
        for item, lo, hi, weight in entries:
            if item.startswith('tacz:'):
                continue  # стволы, патроны и навесное считаем в штуках отдельно
            avg = (lo + hi) / 2
            for mat, v in eco.cost(item).items():
                got[mat] += v * avg * (weight / total) * picks
    entries = pools.get('AIRDROP', [])
    total = sum(e[3] for e in entries) or 1
    for item, lo, hi, weight in entries:
        if item.startswith('tacz:'):
            continue
        avg = (lo + hi) / 2
        for mat, v in eco.cost(item).items():
            got[mat] += v * avg * (weight / total) * MATCH_MODEL['airdrops'] * MATCH_MODEL['airdropItems']
    return got


def main():
    jars = sorted(glob.glob(os.path.join(ROOT, '*.jar')))
    recipes, tags = load(jars)
    eco = Economy(recipes, tags)

    print('рецептов разобрано:', sum(len(v) for v in eco.variants.values()))
    for target, want in [('tacz:9mm', 50), ('tacz:556x45', 45), ('tacz:gun_smith_table', 1)]:
        cost = eco.cost(target)
        top = sorted(cost.items(), key=lambda kv: -kv[1])[:5]
        print(f'  {target} →', ', '.join(f'{pretty(k)} {v:.1f}' for k, v in top))

    per_basket = {}
    for name, basket in BASKETS.items():
        acc = defaultdict(float)
        for item, n in basket.items():
            for mat, v in eco.cost(item).items():
                acc[mat] += v * n
        per_basket[name] = acc

    # Обязательное и полезное — то, что реально крафтят. Эндгейм-технику чаще находят в луте.
    totals = defaultdict(float)
    for name in ('Обязательное', 'Полезное'):
        for mat, v in per_basket[name].items():
            totals[mat] += v

    pools = parse_pools()
    got = supply(eco, pools)
    cons = consumables(pools)

    # Патроны: схроны дают часть готовыми, остальное крафтят, и только это тянет медь и порох
    rounds_need_player = sum(AMMO.values())
    rounds_need = rounds_need_player * SCENARIO_PLAYERS
    rounds_have = cons['rounds'] + cons.get('rounds_auto', 0)
    craft_share = max(0.0, min(1.0, 1.0 - rounds_have / rounds_need))
    ammo_cost = defaultdict(float)
    for item, n in AMMO.items():
        for mat, v in eco.cost(item).items():
            ammo_cost[mat] += v * n * craft_share
    for mat, v in ammo_cost.items():
        totals[mat] += v

    print('\nСпрос на одного игрока (топ-12):')
    for mat, v in sorted(totals.items(), key=lambda kv: -kv[1])[:12]:
        print(f'  {pretty(mat):32s} {v:8.1f}')

    print('\nПатронов на катку надо', rounds_need, '| схроны дают', round(rounds_have),
          '| крафтить', f'{100 * craft_share:.0f}%')
    print('\nПредложение от схронов (топ-12):')
    for mat, v in sorted(got.items(), key=lambda kv: -kv[1])[:12]:
        print(f'  {pretty(mat):32s} {v:8.1f}')

    per_match = {k: v * SCENARIO_PLAYERS for k, v in totals.items()}
    print('\nПокрытие спроса схронами (только лимитирующее сырьё):')
    rows = []
    for mat in BOTTLENECK:
        need = per_match.get(mat, 0.0)
        have = got.get(mat, 0.0)
        if need <= 0:
            continue
        rows.append((mat, need, have, have / need if need else 0))
        print(f'  {pretty(mat):28s} надо {need:7.1f}  есть {have:7.1f}  {100 * have / need:5.0f}%')

    print('\nРасходники за катку:', {k: round(v) for k, v in cons.items()})

    draw(totals, per_basket, rows)
    write_md(totals, per_basket, rows, eco, cons, rounds_need, rounds_have, craft_share)


def draw(totals, per_basket, rows):
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt

    top = sorted(totals.items(), key=lambda kv: -kv[1])[:12]
    labels = [pretty(k) for k, _ in top]
    values = [v for _, v in top]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6), gridspec_kw={'width_ratios': [1.15, 1]})
    colors = ['#e8743b', '#8c8c8c', '#5b5b5b', '#c0392b', '#d4ac0d', '#2e4053',
              '#7f8c8d', '#a04000', '#1b4f72', '#6e2c00', '#b7950b', '#566573']
    ax1.barh(labels[::-1], values[::-1], color=colors[:len(values)][::-1])
    ax1.set_xlabel('единиц сырья на одного игрока за катку')
    ax1.set_title('Спрос на сырьё: что реально надо копать')
    for i, v in enumerate(values[::-1]):
        ax1.text(v + max(values) * 0.01, i, f'{v:.0f}', va='center', fontsize=9)
    ax1.grid(axis='x', alpha=0.25)

    names = list(per_basket.keys())
    mats = [m for m, _ in sorted(totals.items(), key=lambda kv: -kv[1])[:6]]
    bottom = [0] * len(names)
    palette = ['#e8743b', '#8c8c8c', '#5b5b5b', '#c0392b', '#d4ac0d', '#2e4053']
    for mat, color in zip(mats, palette):
        vals = [per_basket[n].get(mat, 0) for n in names]
        ax2.bar(names, vals, bottom=bottom, label=pretty(mat), color=color)
        bottom = [b + v for b, v in zip(bottom, vals)]
    ax2.set_ylabel('единиц сырья')
    ax2.set_title('Куда уходит сырьё')
    ax2.legend(fontsize=8)
    ax2.tick_params(axis='x', rotation=12)
    ax2.grid(axis='y', alpha=0.25)
    fig.tight_layout()
    fig.savefig(OUT_PNG, dpi=150)

    # Второй график: спрос против предложения из схронов
    fig2, ax = plt.subplots(figsize=(11, 6))
    names = [pretty(m) for m, _, _, _ in rows][::-1]
    need = [n for _, n, _, _ in rows][::-1]
    have = [h for _, _, h, _ in rows][::-1]
    y = range(len(names))
    ax.barh([i + 0.2 for i in y], need, height=0.38, color='#c0392b', label='надо на катку (8 игроков)')
    ax.barh([i - 0.2 for i in y], have, height=0.38, color='#2e86c1', label='дают схроны')
    ax.set_yticks(list(y))
    ax.set_yticklabels(names)
    ax.set_xlabel('единиц сырья')
    ax.set_title('Спрос и предложение: где схроны не закрывают экономику')
    ax.legend()
    ax.grid(axis='x', alpha=0.25)
    fig2.tight_layout()
    fig2.savefig(OUT_PNG.replace('.png', '-supply.png'), dpi=150)
    print('график:', OUT_PNG.replace('.png', '-supply.png'))
    print('\nграфик:', OUT_PNG)


def consumables(pools):
    """Сколько патронов, стволов и аптечек даёт лут за катку — в штуках, а не в сырье."""
    out = defaultdict(float)
    for wave, share in MATCH_MODEL['waveShare'].items():
        picks = MATCH_MODEL['caches'] * share * MATCH_MODEL['itemsPerCache']
        entries = pools.get(wave, [])
        total = sum(e[3] for e in entries) or 1
        for item, lo, hi, weight in entries:
            avg = (lo + hi) / 2
            share_of_pick = (weight / total) * picks
            if item == 'tacz:ammo':
                out['rounds'] += avg * share_of_pick
            elif item == 'tacz:modern_kinetic_gun':
                out['guns'] += share_of_pick
                out['rounds_auto'] += 24 * share_of_pick
            elif item == 'tacz:attachment':
                out['attachments'] += share_of_pick
            elif 'medical_kit' in item:
                out['medkits'] += avg * share_of_pick
    entries = pools.get('AIRDROP', [])
    total = sum(e[3] for e in entries) or 1
    picks = MATCH_MODEL['airdrops'] * MATCH_MODEL['airdropItems']
    for item, lo, hi, weight in entries:
        avg = (lo + hi) / 2
        share_of_pick = (weight / total) * picks
        if item == 'tacz:ammo':
            out['rounds'] += avg * share_of_pick
        elif item == 'tacz:modern_kinetic_gun':
            out['guns'] += share_of_pick
            out['rounds_auto'] += 24 * share_of_pick
        elif item == 'tacz:attachment':
            out['attachments'] += share_of_pick
        elif 'medical_kit' in item:
            out['medkits'] += avg * share_of_pick
    return out


# Оружие TaCZ: цена крафта на столе оружейника. Читается из рецептов мода напрямую.
def gun_prices():
    import zipfile
    prices = []
    for jar in glob.glob(os.path.join(ROOT, 'tacz*.jar')):
        z = zipfile.ZipFile(jar)
        for n in z.namelist():
            if '/recipes/gun/' not in n or not n.endswith('.json'):
                continue
            try:
                d = json.loads(re.sub(r'^\s*//[^\n]*$', '', z.read(n).decode('utf-8', 'ignore'), flags=re.M))
            except Exception:
                continue
            mats = {}
            for m in d.get('materials', []):
                key = m['item'].get('tag') or m['item'].get('item') or '?'
                mats[key] = m.get('count', 1)
            prices.append((d['result']['id'].split(':')[-1], mats))
    prices.sort(key=lambda r: r[1].get('forge:ingots/iron', 0))
    return prices


# Часовая «зарплата» одного игрока. Оценка, не замер: шахта на y=-16..0, туннели через 2 блока,
# железная кирка, без PvP-помех, плюс 15 минут Незера на кварц.
HOUR_BUDGET = {
    'minecraft:iron_ingot': 45,
    'minecraft:copper_ingot': 110,
    'minecraft:redstone': 55,
    'minecraft:gold_ingot': 18,
    'minecraft:quartz': 60,
    'minecraft:diamond': 4,
    'minecraft:lapis_lazuli': 40,
    'minecraft:logs': 60,
    'minecraft:gunpowder': 24,
}

KEY_ITEMS = [
    ('tacz:gun_smith_table', 'Стол оружейника (патроны)'),
    ('tacz:9mm', 'Крафт 9 мм (50 шт)'),
    ('tacz:556x45', 'Крафт 5.56 (45 шт)'),
    ('tacz:12g', 'Крафт 12 калибра (12 шт)'),
    ('blackchannel:uplink_rack', 'Стойка-аплинк'),
    ('blackchannel:battery_cell', 'Элемент питания'),
    ('blackchannel:ids_module', 'IDS'),
    ('superbwarfare:medical_kit', 'Аптечка SBW'),
    ('superbwarfare:c4_bomb', 'C4'),
    ('electrodynamics:nightvisiongoggles', 'ПНВ'),
    ('electrodynamics:compositearmorchestplate', 'Композитная броня'),
    ('electrodynamics:jetpack', 'Джетпак'),
]


def write_md(totals, per_basket, rows, eco, cons, rounds_need, rounds_have, craft_share, pools=None):
    lines = ['# Экономика лута Black Channel', '',
             'Сгенерировано `docs/loot_stats.py`: скрипт читает рецепты всех модов из корня',
             'репозитория (2 100+ рецептов), разворачивает их до сырья и сравнивает с тем,',
             'что дают схроны. Пересобрать: `python3 docs/loot_stats.py`.', '',
             '## Коротко', '',
             '- Лимитируют игру **железо, порох и редстоун**, а не «редкие» вещи из пулов.',
             '- Медь нужна в основном на патроны: один крафт 5.56 — это 15 меди и 3 пороха.',
             '- Стволы TaCZ крафтятся на столе оружейника, но дорого: пистолет 16 железа, АК 38, AWP 200.',
             '- Ствол без патронов бесполезен, поэтому к каждому выпавшему оружию добивается стак патронов.',
             '- Ядерный заряд в игре собирается по-настоящему, но это проект на 4–8 часов, не на катку.',
             '', '![Спрос на сырьё](loot-demand.png)', '',
             '![Спрос и предложение](loot-demand-supply.png)', '',
             '## Что из чего крафтится', '',
             '| Предмет | Что нужно (в сумме) |', '|---|---|']
    for item, label in KEY_ITEMS:
        cost = eco.batch_cost(item)
        parts = ', '.join(f'{pretty(k)} {fmt(v)}' for k, v in sorted(cost.items(), key=lambda kv: -kv[1])[:5])
        lines.append(f'| {label} | {parts or "—"} |')

    lines += ['', '## Спрос на одного игрока за катку', '',
              'Обязательное (стол оружейника, аплинк, три элемента питания), полезное (IDS,',
              'трекер-логгер, две аптечки, две C4) и патроны — за вычетом того, что дали схроны.', '',
              '| Сырьё | Сколько |', '|---|---:|']
    for mat, v in sorted(totals.items(), key=lambda kv: -kv[1])[:12]:
        lines.append(f'| {pretty(mat)} | {v:.0f} |')

    lines += ['', '## Спрос и предложение на катку', '',
              'Модель: катка 90 минут, 8 игроков, 24 схрона (карта 1000, 4 отряда),',
              '4–6 вещей в схроне, волны 55% / 30% / 15%, четыре аирдропа. Покрытие ниже 100% —',
              'остальное игрок добывает сам, это и есть смысл копать.', '',
              '| Сырьё | Надо | Дают схроны | Покрытие |', '|---|---:|---:|---:|']
    for mat, need, have, cover in rows:
        lines.append(f'| {pretty(mat)} | {need:.0f} | {have:.0f} | {100 * cover:.0f}% |')

    lines += ['', '## Расходники за катку (в штуках)', '',
              '| Что | Даёт лут | Надо на 8 игроков |', '|---|---:|---:|',
              f'| Патроны из пулов | {cons["rounds"]:.0f} | {rounds_need} |',
              f'| Патроны, добитые к стволам | {cons.get("rounds_auto", 0):.0f} | — |',
              f'| Патроны всего | {cons["rounds"] + cons.get("rounds_auto", 0):.0f} '
              f'({100 * (1 - craft_share):.0f}%) | {rounds_need} |',
              f'| Стволы TaCZ | {cons["guns"]:.0f} | 16 |',
              f'| Навесное | {cons["attachments"]:.0f} | 16 |',
              f'| Аптечки | {cons["medkits"]:.0f} | 16 |', '',
              'Остальные патроны (%.0f%%) игроки крафтят на столе оружейника из меди и пороха —'
              % (100 * craft_share),
              'это и есть планомерная часть: схроны дают ствол и первый боезапас,', 'а не бесконечные патроны.', '',
              '## Что и почему изменено', '',
              '| Правка | Было | Стало | Причина |', '|---|---|---|---|',
              '| Патроны 9 мм | 24–48 | 48–96 | патроны — самый нужный расходник, их не хватало втрое |',
              '| Патроны 5.56 | 20–40 | 40–80 | то же, это основной калибр средней волны |',
              '| Патроны 7.62 | 20–40 | 40–80 | АК и СКС — самые частые стволы |',
              '| Патроны .308 | 10–20 | 20–40 | поздняя волна и аирдроп |',
              '| Медь | 8–16 @12 | 24–44 @20 | медь уходит на каждый крафт патронов |',
              '| Железо | 4–8 @12 | 6–14 @16 + 12–24 @14 в средней | стол оружейника и аплинк — 18 железа на игрока |',
              '| Порох | 4–8 @8 | 8–14 @20 + 12–24 @14 | порох нужен на каждый крафт патронов |',
              '| Редстоун | 6–10 @8 | 6–12 @10 | аплинк, IDS, ПНВ, техника Electrodynamics |',
              '| Волны схронов | 100% сразу | 55% / 30% / 15% | раньше все схроны ставились на старте |',
              '| Длина катки | 30 минут | 90 минут (5–180) | рабочая катка — полтора-два часа |',
              '| Число схронов | только от отрядов и карты | ещё и от длины катки (√) | иначе за 90 минут карта опустеет |', '',
              'Почему не «положить всего побольше»: если схроны закрывают 100% спроса, копать',
              'становится незачем, и карта превращается в гонку по сундукам. Цель — 75–85%:',
              'схроны снимают нужду, но медь, железо и порох всё ещё надо добывать.', '']

    lines += hourly_section(eco)
    lines += gun_section()
    lines += endgame_section(eco, pools or parse_pools())
    lines += nuclear_section(eco)
    lines += scale_section()
    open(OUT_MD, 'w', encoding='utf-8').write('\n'.join(lines))
    print('таблица:', OUT_MD)


def fits(cost, budget, slack=1.3):
    """Влезает ли набор в часовой бюджет: по каждому ресурсу не больше чем budget × slack."""
    for mat, v in cost.items():
        if mat in budget and v > budget[mat] * slack:
            return False
    return True


def hourly_section(eco):
    goals = [
        ('tacz:gun_smith_table', 'Стол оружейника'),
        ('tacz:9mm', 'Крафт 9 мм (50 шт)'),
        ('tacz:556x45', 'Крафт 5.56 (45 шт)'),
        ('blackchannel:uplink_rack', 'Стойка-аплинк'),
        ('electrodynamics:mineralgrinder', 'Дробилка'),
        ('electrodynamics:coalgenerator', 'Генератор на угле'),
        ('electrodynamics:circuitbasic', 'Базовая схема'),
        ('superbwarfare:c4_bomb', 'C4'),
        ('electrodynamics:nightvisiongoggles', 'ПНВ'),
        ('electrodynamics:jetpack', 'Джетпак'),
        ('nuclearscience:chemicalextractor', 'Хим. экстрактор'),
        ('nuclearscience:gascentrifuge', 'Газ. центрифуга'),
        ('nuclearscience:nuclearboiler', 'Ядерный бойлер'),
        ('ballistix:nuclear', 'Ядерный заряд Ballistix'),
    ]
    lines = ['## Что можно скрафтить за час', '',
             'Оценка бюджета одного игрока за час: ~45 железа, ~110 меди, ~55 редстоуна,',
             '~18 золота, ~4 алмаза, ~60 кварца (с заходом в Незер на 15 минут).',
             'Это прикидка темпа шахты, а не замер: реальные числа зависят от карты и PvP.',
             'Колонка «хватает сырья» — только про ресурсы: машинам ещё нужна энергосеть и время', '',
             '| Цель | Что нужно | Хватает сырья |', '|---|---|---|']
    for item, label in goals:
        cost = eco.batch_cost(item)
        parts = ', '.join(f'{pretty(k)} {fmt(v)}' for k, v in sorted(cost.items(), key=lambda kv: -kv[1])[:4])
        ok = 'да' if fits(cost, HOUR_BUDGET) else 'нет'
        lines.append(f'| {label} | {parts} | {ok} |')
    lines.append('')
    return lines


def gun_section():
    prices = gun_prices()
    if not prices:
        return []
    alias = {'forge:ingots/iron': 'железо', 'forge:ingots/gold': 'золото', 'forge:gems/diamond': 'алмазы',
             'forge:gems/lapis': 'лазурит', 'minecraft:logs': 'дерево', 'forge:rods/blaze': 'стержни ифрита',
             'forge:gems/quartz': 'кварц', 'forge:ingots/netherite': 'незерит', 'forge:gems/amethyst': 'аметист'}
    iron_budget = HOUR_BUDGET['minecraft:iron_ingot']
    lines = ['## Оружие TaCZ: сколько стоит скрафтить ствол', '',
             'Стволы делаются на столе оружейника (tacz:gun_smith_table). Цена в рецепте —',
             'это железо, золото, алмазы и лазурит, без навесного. Час шахты — примерно',
             f'{iron_budget} железа, поэтому граница «своими руками за катку» проходит около 40–50 железа.', '',
             '| Ствол | Железо | Золото | Алмазы | Лазурит | Прочее | За час |',
             '|---|---:|---:|---:|---:|---|---|']
    for gid, mats in prices:
        f = lambda k: mats.get(k, 0)
        other = ', '.join(f'{alias.get(k, k)} {v}' for k, v in mats.items()
                          if k not in ('forge:ingots/iron', 'forge:ingots/gold', 'forge:gems/diamond',
                                       'forge:gems/lapis', 'minecraft:logs'))
        ok = 'да' if f('forge:ingots/iron') <= iron_budget * 1.1 else 'нет'
        lines.append(f'| {gid} | {f("forge:ingots/iron")} | {f("forge:ingots/gold")} | '
                     f'{f("forge:gems/diamond")} | {f("forge:gems/lapis")} | {other or "—"} | {ok} |')
    lines.append('')
    return lines


def nuclear_section(eco):
    charge = eco.batch_cost('ballistix:nuclear')
    parts = sorted(charge.items(), key=lambda kv: -kv[1])[:8]
    machines = [
        ('nuclearscience:chemicalextractor', 'Хим. экстрактор: сырьё → жёлтый кек'),
        ('nuclearscience:nuclearboiler', 'Ядерный бойлер: кек → гексафторид урана'),
        ('nuclearscience:gascentrifuge', 'Газ. центрифуга: разделение U-235 и U-238'),
    ]
    lines = ['## Можно ли сделать ядерный заряд', '',
             'Цепочка целиком, по рецептам из jar\'ов:', '',
             '1. Руда урана (Electrodynamics) → хим. экстрактор → жёлтый кек.',
             '2. Ядерный бойлер → гексафторид урана.',
             '3. Газ. центрифуга → U-235 и U-238 (передел без JSON-рецепта, внутри машины).',
             '4. Стекло + U-235 + U-238 → топливный стержень HEUO2.',
             '5. 4 термобарических заряда + 4 базовые схемы + стержень → ballistix:nuclear.',
             '', 'Машины, без которых шаг не пройти:', '',
             '| Машина | Зачем | Что нужно |', '|---|---|---|']
    for item, why in machines:
        cost = eco.batch_cost(item)
        lines.append(f'| {pretty(item)} | {why} | '
                     + ', '.join(f'{pretty(k)} {fmt(v)}' for k, v in sorted(cost.items(), key=lambda kv: -kv[1])[:4])
                     + ' |')
    lines += ['', 'Сам заряд в сырье:', '',
              '| Ресурс | Сколько |', '|---|---:|']
    for k, v in parts:
        lines.append(f'| {pretty(k)} | {fmt(v)} |')
    lines += ['', 'Вывод: **в принципе да**, всё крафтится, ничего не заблокировано. Практически —',
              'нет: это 4–8 часов промышленной игры на команду (энергосеть, дробилка, три ядерные',
              'машины, добыча урана, центрифугирование, плюс ~340 пороха в эквиваленте на сам заряд).',
              'В часовую катку это не влезает даже у скиллового игрока, и это нормально:',
              'ядерка — витрина мода, а не оружие катки.', '']
    return lines


def endgame_section(eco, pools):
    """Что реально собрать к концу катки 1.5–2 часа."""
    combos = [
        ('РПГ в руках + 3 ракеты T1 с осколочной БЧ',
         [('ballistix:rocketlauncher', 1), ('ballistix:missiletier1', 3), ('ballistix:shrapnel', 3)]),
        ('VLS (пусковая) + 2 ракеты T1',
         [('ballistix:vls', 1), ('ballistix:missiletier1', 2), ('ballistix:shrapnel', 2)]),
        ('Пусковая шахта T2: платформа + рама + пульт',
         [('ballistix:launcherplatformtier2', 1), ('ballistix:launchersupportframetier2', 1),
          ('ballistix:launchercontrolpaneltier2', 1)]),
        ('Ракета T2 с термобарической БЧ',
         [('ballistix:missiletier2', 1), ('ballistix:thermobaric', 1)]),
        ('3× C4',
         [('superbwarfare:c4_bomb', 3)]),
        ('Автомат TaCZ (АК-47 или M4A1)',
         [('tacz:ak47', 1)]),
        ('Снайперка AWP',
         [('tacz:ai_awp', 1)]),
        ('Джетпак',
         [('electrodynamics:jetpack', 1)]),
        ('ПНВ + композитная броня',
         [('electrodynamics:nightvisiongoggles', 1), ('electrodynamics:compositearmorchestplate', 1)]),
        ('Дробилка + генератор на угле',
         [('electrodynamics:mineralgrinder', 1), ('electrodynamics:coalgenerator', 1)]),
        ('Ядерный заряд Ballistix',
         [('ballistix:nuclear', 1)]),
    ]

    def combo_cost(parts):
        total = {}
        for item, n in parts:
            for k, v in eco.batch_cost(item).items():
                total[k] = total.get(k, 0) + v * n
        return {k: v for k, v in total.items() if v >= 0.05}

    budgets = {m: match_budget(eco, pools, m)[0] for m in MATCH_MINUTES}

    lines = ['## Что реально собрать к концу катки 1.5–2 часа', '',
             'Считается так: бюджет шахты × 40% времени катки (остальное — война и логистика)',
             'плюс доля лута со схронов на игрока. Схронов на 90 минут — 24, на 120 — 28.', '',
             '| Цель | 90 мин | 120 мин | Упирается в |', '|---|---|---|---|']
    for label, parts in combos:
        cost = combo_cost(parts)
        cells = []
        worst_name = ''
        for m in MATCH_MINUTES:
            budget = budgets[m]
            worst, who = 0.0, ''
            for k, v in cost.items():
                if k in budget and budget[k] > 0 and v / budget[k] > worst:
                    worst, who = v / budget[k], k
            cells.append(f'{"да" if worst <= 1 else "нет"} ({worst * 100:.0f}%)')
            worst_name = worst_name or pretty(who)
        lines.append(f'| {label} | {cells[0]} | {cells[1]} | {worst_name} |')

    lines += ['', '### Где проходит граница', '',
              'Всё упирается в **порох**. Без промышленности порох — это криперы: за катку',
              'реально набрать ~30–38 штук, а термобарическая БЧ стоит 86, топливный стержень',
              'для ядерки — 343. Поэтому по «криперской» ветке потолок такой:', '',
              '- **да:** РПГ в руках с ракетами T1 (любая БЧ первого тира), VLS, C4, гранаты T1;',
              '- **да:** сама пусковая шахта T2 (это 19 железа, дешёво), но не ракета к ней;',
              '- **нет:** ракета T2 с термобаром, ракета T3, ядерка, снайперка AWP (200 железа).', '',
              'Вторая ветка — промышленная. Electrodynamics даёт рецепт пороха',
              '(серная пыль + селитра + уголь = 6 пороха), а серу и селитру добывают из руд',
              '(`oresulfur`, `oreniter`) и дробят в дробилке. То есть:', '',
              '| Что нужно | Порох | Серы и селитры | Уголь |', '|---|---:|---:|---:|',
              '| Термобарическая БЧ | 86 | ~15 + 15 | 15 |',
              '| Ракета T2 + термобар | 88 | ~15 + 15 | 15 |',
              '| Ядерный заряд | 343 | ~58 + 58 | 58 |', '',
              'С дробилкой, генератором и 15 блоками серной и селитряной руды термобар становится',
              'вопросом логистики, а не удачи: **ракета T2 к концу двухчасовой катки реальна**.',
              'Ядерка — нет: помимо 58 крафтов пороха это ещё уран, центрифуга, бойлер и',
              'экстрактор, то есть 4–8 часов промышленной игры на команду.', '']
    return lines


def scale_section():
    lines = ['## Коэффициент лута под размер катки', '',
             'База в конфиге — эталон: 8 игроков и 14 схронов на карте 1000×1000.',
             'Дальше применяется коэффициент K = (игроков / схронов) / (8/14) × (сторона/1000)^0.2,',
             'вещи в сундуке и стаки домножаются на √K (чтобы сундук не распухал), K ограничен',
             'сверху lootScaleMax (по умолчанию 3). Выключается ключом lootScaling = false.', '',
             '| Катка | Схронов | K | Вещей в сундуке | Стак × |', '|---|---:|---:|---:|---:|']
    import math
    def caches(teams, side):
        return max(8, min(56, round(3.5 * teams * side / 1000)))
    rows = [(8, 4, 1000), (12, 3, 1000), (16, 4, 1000), (16, 8, 1000), (24, 6, 1500), (32, 8, 2500), (8, 2, 1000)]
    for players, teams, side in rows:
        c = caches(teams, side)
        k = (players / c) / (8 / 14) * (side / 1000) ** 0.2
        k = max(0.5, min(3.0, k))
        lines.append(f'| {players} игроков / {teams} отряда / {side} | {c} | {k:.2f} | '
                     f'{4.5 * math.sqrt(k):.1f} | {math.sqrt(k):.2f} |')
    lines.append('')
    return lines


if __name__ == '__main__':
    main()
