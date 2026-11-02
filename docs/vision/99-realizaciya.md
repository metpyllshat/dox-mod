# Что сделано по запросу 2026-11-02

## Таблица обещаний
| Обещание | Статус | Где |
|---|---|---|
| Статистика + сводка после катки | done | `MatchData.Stats` — traces/towers/kills/deaths/cryptoEarned + титулы |
| Диктор (текст+звук, слоты под твой голос) | done | `Announcer.java` + `sounds.json` слоты `announcer/start|storm|silence|airdrop|summary|tower_captured|node_open|team_out` — кидай ogg в `assets/blackchannel/sounds/announcer/` |
| Образец сигнала — куда тратить | done | 15-40 крипты на рынке + альтернатива: сжечь чистый образец вместо 8 крипты за PEEK/расшифровку (Ops.payPeekWithSampleOrCrypto) |
| Инфляция | done | TRACE 2→3→4→5→6 (+30% каждые 10м), SWAT +50% за второй, налог рынка 10% сгорает |
| Титулы | done | `Titles.java` — Слухач/Тишина/Глушитель/Чистильщик/Барыга |
| Ноутбук-станция (таблица слева, схема справа, очередь, команды снизу, стойка сверху) | done | `LaptopScreen.java` переписан — ширина 340, 4 панели |
| Мини-игра взлома (резонанс 6с) | done | `HackGame.java` — ползунок 0..1, зона 0.07, удержание 1.2с, 6с лимит |
| Звуки интерфейса/пеленга | done | `sounds.json` — ui.click, bearing.ping, jam.hum, hack.tick/success/fail |
| Глушение связи в глубокой пещере Y<-40 | done | `SignalEnv.deepCave()` — Y<-40 + нет неба → transmitting=false и link=false |
