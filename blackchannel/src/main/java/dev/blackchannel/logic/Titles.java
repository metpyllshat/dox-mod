package dev.blackchannel.logic;

import dev.blackchannel.data.MatchData;
import dev.blackchannel.data.NetData;

/**
 * Титулы — косметика из 04-экономика: Слухач / Тишина / Глушитель
 */
public final class Titles {
    private Titles(){}
    public static String forProfile(NetData.Profile p, MatchData.Stats s){
        if (s == null) return "Новичок";
        if (s.traces >= 20) return "Слухач";
        if (p.noise < 8 && s.traces > 5) return "Тишина";
        if (s.towers >= 3) return "Глушитель";
        if (s.kills >= 5) return "Чистильщик";
        if (s.cryptoEarned >= 300) return "Барыга";
        return "Оператор";
    }
}
