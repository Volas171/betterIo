package betterio;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.util.*;
import betterio.datas.PersistentPlayerData;
import betterio.datas.PlayerData;
import com.google.gson.Gson;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.graphics.Pal;
import mindustry.mod.Plugin;
import mindustry.world.Build;
import mindustry.world.Tile;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.json.JSONObject;
import org.json.JSONTokener;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import static betterio.Utils.*;
import static mindustry.Vars.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;


public class main {
    public static JedisPool pool;
    static Gson gson = new Gson();
    public static String prefix = ".";
    public static String serverName = "<untitled>";
    public static DiscordApi api = null;
    public static HashMap<String, PersistentPlayerData> playerDataGroup = new HashMap<>();
    private JSONObject allData;
    public static JSONObject data;
    public void init() {
        Utils.init();
        
        /*for (Player p : Vars.playerGroup.all()) {

            PlayerData pd = getData(p.uuid);
            if (pd == null) return;

            // update buildings built
            PersistentPlayerData tdata = (main.playerDataGroup.getOrDefault(p.uuid, null));
            if (tdata != null){
                if (tdata.bbIncrementor > 0){
                    pd.buildingsBuilt = pd.buildingsBuilt + tdata.bbIncrementor;
                    tdata.bbIncrementor = 0;
                }
            }


            pd.playTime++;
            if(pd.rank <= 0 && pd.playTime >= activeRequirements.playtime && pd.buildingsBuilt >= activeRequirements.buildingsBuilt && pd.gamesPlayed >= activeRequirements.gamesPlayed){
                Call.onInfoMessage(p.con, Utils.formatMessage(p, promotionMessage));
                if (pd.rank < 1) pd.rank = 1;
            }
            setData(p.uuid, pd);
            main.playerDataGroup.put(p.uuid, tdata); // update tdata with the new stuff
        }*/
        try {
            String pureJson = Core.settings.getDataDirectory().child("mods/settings.json").readString();
            data = allData = new JSONObject(new JSONTokener(pureJson));
        } catch(Exception e) {
            Log.err("Can't read settings.json file.");
        }
        try {
            api = new DiscordApiBuilder().setToken(allData.getString("token")).login().join();
        } catch (Exception e) {
            Log.err("Cant login to discord");
        }
        try{
            pool = new JedisPool(new JedisPoolConfig(), "localhost");
            Log.info("connected to redis database");
        } catch (Exception e) {
            e.printStackTrace();
            Core.app.exit();
        }
        if (data.has("prefix")) {
            prefix = String.valueOf(data.getString("prefix").charAt(0));
        } else {
            Log.warn("Prefix not found, using default '.' prefix.");
        }
        // setup name
        if (data.has("server_name")) {
            serverName = String.valueOf(data.getString("server_name"));
        } else {
            Log.warn("No server name setting detected!");
        }
        BotThread bt = new BotThread(api, Thread.currentThread(), allData);
        bt.setDaemon(false);
        bt.start();
        Events.on(EventType.PlayerJoin.class, event -> {
            CompletableFuture.runAsync(() -> {
                Player player = event.player;
                PlayerData pd = getData(player.uuid);
                if(!playerDataGroup.containsKey(player.uuid)) {
                    PersistentPlayerData data = new PersistentPlayerData();
                    playerDataGroup.put(player.uuid, data);
                }
                if(pd != null) {
                    if(pd.banned || pd.bannedUntil > Instant.now().getEpochSecond()) {
                        player.con.kick("[scarlet]You are banned, nooby.[accent]\n    Reason:" + pd.banReason + "\n    Ban ID: " + pd.banID);
                    }
                    int rank = pd.rank;
                    switch (rank) { // apply new tag

                        case 1:
                            Call.sendMessage("[sky]Active player " + player. + "[] joined the server!");
                            break;
                        case 2:
                            Call.sendMessage("[#fcba03]Veteran player " + player.name + "[] joined the server!");
                            break;
                        case 3:
                            Call.sendMessage("[scarlet]Contributer " + player.name + "[] joined the server!");
                            break;
                        case 4:
                            Call.sendMessage("[orange]<[][white]better io moderator[][orange]>[] " + player.name + "[] joined the server!");
                            break;
                        case 5:
                            Call.sendMessage("[orange]<[][white]better io admin[][orange]>[] " + player.name + "[] joined the server!");
                            break;
                    }
                } else {
                    setData(player.uuid, new PlayerData(0));
                }
            });
        });
        Events.on(EventType.WorldLoadEvent.class, () -> {
            for (java.util.Map.Entry<String, PersistentPlayerData> entry : playerDataGroup.entrySet()) {
                PersistentPlayerData tdata = entry.getValue();
                if(tdata != null) {
                    tdata.spawnedPowerGen = false;
                    tdata.spawnedLichPet = false;
                    tdata.draugPets.clear();
                }
            }
        });
        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.player == null) return;
            if (event.breaking) return;
            PlayerData pd = getData(event.player.uuid);
            PersistentPlayerData td = (playerDataGroup.getOrDefault(event.player.uuid, null));
            if (pd == null || td == null) return;
            if (event.tile.entity != null) {
                if (!activeRequirements.bannedBlocks.contains(event.tile.block())) {
                    td.bbIncrementor++;
                }
            }
        });
    }
    @Override
    public void registerClientCommands(CommandHandler handler){
        handler.<Player>register("lichpet", "[contributer+]Spawns lich pet", (args, player) -> {
            if(!state.rules.pvp || player.isAdmin) {
                PlayerData pd = getData(player.uuid);
                if(pd != null && pd.rank >= 3) {
                    PersistentPlayerData tdata = playerDataGroup.get(player.uuid);
                    if(tdata == null) return;
                    if(!tdata.spawnedLichPet || player.isAdmin) {
                        tdata.spawnedLichPet = true;
                        BaseUnit unit = UnitTypes.lich.create(player.getTeam());
                        unit.set(player.getClosestCore().x,player.getClosestCore().y);
                        unit.health = 200f;
                        unit.add();
                        Call.sendMessage(player.name+"[#ff0000] spawned a lich pet for 2 minutes.");
                        Timer.schedule(unit::kill, 120);
                    } else {
                        player.sendMessage("[scarlet]You already spawned one");
                    }
                } else {
                    player.sendMessage("[scarlet]Not high enough rank");
                }
            } else {
                player.sendMessage("[scarlet]Disabled on PVP");
            }
        });
        handler.<Player>register("draugpet", "[active+]Spawns a draugpet", (args, player) -> {
           if(!state.rules.pvp || player.isAdmin) {
               PlayerData pd = getData(player.uuid);
               if(pd != null & pd.rank >= 1) {
                   PersistentPlayerData tdata = playerDataGroup.get(player.uuid);
                   if(tdata == null) return;
                   if(tdata.draugPets.size < pd.rank || player.isAdmin) {
                       BaseUnit unit = UnitTypes.draug.create(player.getTeam());
                       unit.set(player.getX(), player.getY());
                       unit.add();
                       tdata.draugPets.add(unit);
                       Call.sendMessage(player.name + "[#b177fc] spawned in a drug pet! " + tdata.draugPets.size + "/" + pd.rank + " spawned.");
                   } else {
                       player.sendMessage("[scarlet]You maxed out ur pets");
                   }
               } else {
                   player.sendMessage("[scarlet]Not high enough rank!");
               }
           } else {
               player.sendMessage("[scarlet]Diable on PVP");
           }
        });
        handler.<Player>register("powergen", "[donator+] Spawn yourself a power generator.", (args, player) -> {
            if(!state.rules.pvp || player.isAdmin) {
                PlayerData pd = getData(player.uuid);
                if (pd != null && pd.rank >= 3) {
                    PersistentPlayerData tdata = playerDataGroup.get(player.uuid);
                    if (tdata == null) return;
                    if (!tdata.spawnedPowerGen || player.isAdmin) {
                        float x = player.getX();
                        float y = player.getY();

                        Tile targetTile = world.tileWorld(x, y);

                        if (targetTile == null || !Build.validPlace(player.getTeam(), targetTile.x, targetTile.y, Blocks.rtgGenerator, 0)) {
                            Call.onInfoToast(player.con, "[scarlet]Cannot place a power generator here.",5f);
                            return;
                        }

                        tdata.spawnedPowerGen = true;
                        targetTile.setNet(Blocks.rtgGenerator, player.getTeam(), 0);
                        Call.onLabel("[accent]" + escapeCharacters(escapeColorCodes(player.name)) + "'s[] generator", 60f, targetTile.worldx(), targetTile.worldy());
                        Call.onEffectReliable(Fx.explosion, targetTile.worldx(), targetTile.worldy(), 0, Pal.accent);
                        Call.onEffectReliable(Fx.placeBlock, targetTile.worldx(), targetTile.worldy(), 0, Pal.accent);
                        Call.sendMessage(player.name + "[#ff82d1] spawned in a power generator!");

                        // ok seriously why is this necessary
                        new Object() {
                            private Timer.Task task;
                            {
                                task = Timer.schedule(() -> {
                                    if (targetTile.block() == Blocks.rtgGenerator) {
                                        Call.transferItemTo(Items.thorium, 1, targetTile.drawx(), targetTile.drawy(), targetTile);
                                    } else {
                                        player.sendMessage("[scarlet]Your power generator was destroyed!");
                                        task.cancel();
                                    }
                                }, 0, 6);
                            }
                        };
                    } else {
                        player.sendMessage("[scarlet]You already spawned a power generator in this game!");
                    }
                } else {
                    player.sendMessage("[scarlet]Not high enough rank");
                }
            } else {
                player.sendMessage("[scarlet]This command is disabled on pvp.");
            }
        });
        handler.<Player>register("maps","[page]", "Display all maps in the playlist.", (args, player) -> { // self info
            if(args.length > 0 && !Strings.canParseInt(args[0])){
                player.sendMessage("[scarlet]'page' must be a number.");
                return;
            }
            int commandsPerPage = 6;
            int page = args.length > 0 ? Strings.parseInt(args[0]) : 1;
            int pages = Mathf.ceil((float)Vars.maps.customMaps().size / commandsPerPage);

            page --;

            if(page >= pages || page < 0){
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] " + pages + "[scarlet].");
                return;
            }

            StringBuilder result = new StringBuilder();
            result.append(Strings.format("[orange]-- Maps Page[lightgray] {0}[gray]/[lightgray]{1}[orange] --\n\n", (page+1), pages));

            for(int i = commandsPerPage * page; i < Math.min(commandsPerPage * (page + 1), Vars.maps.customMaps().size); i++){
                mindustry.maps.Map map = Vars.maps.customMaps().get(i);
                result.append("[white] - [accent]").append(escapeColorCodes(map.name())).append("\n");
            }
            player.sendMessage(result.toString());
        });

        Timekeeper vtime = new Timekeeper(60);

        VoteSession[] currentlyKicking = {null};

        handler.<Player>register("nominate","[map...]", "[regular+] Vote to change to a specific map.", (args, player) -> {
            if(!state.rules.pvp || player.isAdmin) {
                PlayerData pd = getData(player.uuid);
                if (pd != null && pd.rank >= 1) {
                    mindustry.maps.Map found = getMapBySelector(args[0]);

                    if(found != null){
                        if(!vtime.get()){
                            player.sendMessage("[scarlet]You must wait " + 60/60 + " minutes between nominations.");
                            return;
                        }

                        VoteSession session = new VoteSession(currentlyKicking, found);

                        session.vote(player, 1);
                        vtime.reset();
                        currentlyKicking[0] = session;
                    }else{
                        player.sendMessage("[scarlet]No map[orange]'" + args[0] + "'[scarlet] found.");
                    }
                } else {
                    player.sendMessage("[scarlet]Not high enough rank");
                }
            } else {
                player.sendMessage("[scarlet]This command is disabled on pvp.");
            }
        });

        handler.<Player>register("rtv", "Vote to change the map.", (args, player) -> { // self info
            if(currentlyKicking[0] == null){
                player.sendMessage("[scarlet]No map is being voted on.");
            }else{
                //hosts can vote all they want
                if(player.uuid != null && (currentlyKicking[0].voted.contains(player.uuid) || currentlyKicking[0].voted.contains(netServer.admins.getInfo(player.uuid).lastIP))){
                    player.sendMessage("[scarlet]You've already voted. Sit down.");
                    return;
                }

                currentlyKicking[0].vote(player, 1);
            }
        });
    }
}