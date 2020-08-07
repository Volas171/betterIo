package betterio;

import arc.Events;
import arc.struct.Array;
import arc.util.Strings;
import betterio.datas.PlayerData;
import mindustry.content.Blocks;
import mindustry.entities.type.Player;
import mindustry.entities.type.TileEntity;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.maps.Map;
import mindustry.maps.Maps;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import org.javacord.api.entity.user.User;
import redis.clients.jedis.Jedis;

import java.awt.*;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static betterio.main.*;
import static mindustry.Vars.*;

public class Utils {
    public static int chatMessageMaxSize = 256;
    static String mapSaveKey = "bXn94MAP";
    static String welcomeMessage = "";
    static String promotionMessage = "";
    static HashMap<Integer, Rank> rankNames = new HashMap<>();
    public static void init() {
        rankNames.put(0, new Rank("[#7d7d7d]<none>[]", "none"));
        rankNames.put(1, new Rank("[accent]<[white]\uE810[accent]>[]", "active"));
        rankNames.put(2, new Rank("[accent]<[white]\uE809[accent]>[]", "regular"));
        rankNames.put(3, new Rank("[accent]<[white]\uE84E[accent]>[]", "donator"));
        rankNames.put(4, new Rank("[accent]<[white]\uE84F[accent]>[]", "moderator"));
        rankNames.put(5, new Rank("[accent]<[white]\uE828[accent]>[]", "admin"));
        activeRequirements.bannedBlocks.add(Blocks.conveyor);
        activeRequirements.bannedBlocks.add(Blocks.titaniumConveyor);
        activeRequirements.bannedBlocks.add(Blocks.junction);
        activeRequirements.bannedBlocks.add(Blocks.router);
    }
    public static PlayerData getData(String uuid) {
        try(Jedis jedis = main.pool.getResource()) {
            String json = jedis.get(uuid);
            if(json == null) return null;
            try{
                return gson.fromJson(json,PlayerData.class);
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
    public static class Rank{
        public String tag = "";
        public String name = "";

        Rank(String t, String n){
            this.tag = t;
            this.name = n;
        }
    }
    public static String formatMessage(Player player, String message){
        try {
            message = message.replaceAll("%player%", escapeCharacters(player.name));
            message = message.replaceAll("%map%", world.getMap().name());
            message = message.replaceAll("%wave%", String.valueOf(state.wave));
            PlayerData pd = getData(player.uuid);
            if (pd != null) {
                message = message.replaceAll("%playtime%", String.valueOf(pd.playTime));
                message = message.replaceAll("%games%", String.valueOf(pd.gamesPlayed));
                message = message.replaceAll("%buildings%", String.valueOf(pd.buildingsBuilt));
                message = message.replaceAll("%rank%", rankNames.get(pd.rank).tag + " " + escapeColorCodes(rankNames.get(pd.rank).name));
                if(pd.discordLink.length() > 0){
                    User discordUser = api.getUserById(pd.discordLink).get(2, TimeUnit.SECONDS);
                    if(discordUser != null) {
                        message = message.replaceAll("%discord%", discordUser.getDiscriminatedName());
                    }
                } else{
                    message = message.replaceAll("%discord%", "unlinked");
                }
            }
        }catch(Exception ignore){};
        return message;
    }
    public static class activeRequirements {
        public static Array<Block> bannedBlocks = new Array<>();
        public static int playtime = 60 * 10;
        public static int buildingsBuilt = 1000 * 10;
        public static int gamesPlayed = 10;
    }
    public static void changeMap(Map found) {
        Class<Maps> mapsClass = Maps.class;
        Field mapsField;
        try {
            mapsField = mapsClass.getDeclaredField("maps");
        } catch(NoSuchFieldException e) {
            throw new RuntimeException("Can't find field maps of class 'mindustry.maps.Maps'");
        }
        mapsField.setAccessible(true);
        Field mapsListField = mapsField;
        Array<Map> mapsList;
        try {
            mapsList = (Array<Map>)mapsListField.get(maps);
        } catch(IllegalAccessException e) {
            throw new RuntimeException("can't reach");
        }
        Array<Map> tempMapsList = mapsList.removeAll(map -> !map.custom || map != found);
        try {
            mapsListField.set(maps, tempMapsList);
        } catch(IllegalAccessException e) {
            throw new RuntimeException("can't reach");
        }
        Events.fire(new EventType.GameOverEvent(Team.crux));
        try{
            mapsListField.set(maps, mapsList);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("can't reach");
        }
    }
    public static String epochToString(long epoch){
        Date date = new Date(epoch * 1000L);
        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("Etc/UTC"));
        return format.format(date) + " UTC";
    }
    public static void setData(String uuid, PlayerData pd) {
        CompletableFuture.runAsync(() -> {
            try(Jedis jedis = main.pool.getResource()) {
                try {
                    String json = gson.toJson(pd);
                    jedis.set(uuid, json);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
    public static String escapeCharacters(String string){
        return escapeColorCodes(string.replaceAll("`", "").replaceAll("@", ""));
    }

    public static String escapeColorCodes(String string){
        return Strings.stripColors(string);
    }
    public static CoreBlock.CoreEntity getCore(Team team){
        Tile[][] tiles = world.getTiles();
        for (int x = 0; x < tiles.length; ++x) {
            for(int y = 0; y < tiles[0].length; ++y) {
                if (tiles[x][y] != null && tiles[x][y].entity != null) {
                    TileEntity ent = tiles[x][y].ent();
                    if (ent instanceof CoreBlock.CoreEntity) {
                        if(ent.getTeam() == team){
                            return (CoreBlock.CoreEntity) ent;
                        }
                    }
                }
            }
        }
        return null;
    }
    public static Map getMapBySelector(String query) {
        Map found = null;
        try {
            // try by number
            found = maps.customMaps().get(Integer.parseInt(query));
        } catch (Exception e) {
            // try by name
            for (Map m : maps.customMaps()) {
                if (m.name().replaceAll(" ", "").toLowerCase().contains(query.toLowerCase().replaceAll(" ", ""))) {
                    found = m;
                    break;
                }
            }
        }
        return found;
    }

    public static class Pals {
        public static Color warning = (Color.getHSBColor(5, 85, 95));
        public static Color info = (Color.getHSBColor(45, 85, 95));
        public static Color error = (Color.getHSBColor(3, 78, 91));
        public static Color success = (Color.getHSBColor(108, 80, 100));
    }
    public static Player findPlayer(String identifier){
        Player found = null;
        for (Player player : playerGroup.all()) {
            if(player == null) return null;
            if(player.uuid == null) return null;
            if(player.con == null) return null;
            if(player.con.address == null) return null;

            if (player.con.address.equals(identifier.replaceAll(" ", "")) || String.valueOf(player.id).equals(identifier.replaceAll(" ", "")) || player.uuid.equals(identifier.replaceAll(" ", "")) || escapeColorCodes(player.name.toLowerCase().replaceAll(" ", "")).replaceAll("<.*?>", "").startsWith(identifier.toLowerCase().replaceAll(" ", ""))) {
                found = player;
            }
        }
        return found;
    }
}
