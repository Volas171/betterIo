package betterio;

import arc.util.Log;
import betterio.datas.PlayerData;
import betterio.discordcommands.Command;
import betterio.discordcommands.Context;
import betterio.discordcommands.DiscordCommands;
import betterio.discordcommands.RoleRestrictedCommand;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.entities.type.BaseUnit;
import mindustry.entities.type.Player;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.type.UnitType;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static betterio.Utils.*;
import static mindustry.Vars.*;

public class ServerCommands {
    private JSONObject data;
    public ServerCommands(JSONObject data) {
        this.data = data;
    }
    public void registerCommands(DiscordCommands handler) {
        if(data.has("administrator_roleid")) {
            String adminRole = data.getString("administrator_roleid");
            handler.registerCommand(new RoleRestrictedCommand("setrank") {
                {
                    help = "<playerid|ip|name> <rank> Change player rank to provided one";
                    role = adminRole;
                }
                @Override
                public void run(Context ctx) {
                    CompletableFuture.runAsync(() -> {
                        EmbedBuilder eb = new EmbedBuilder();
                        String target = ctx.args[1];
                        int targetRank = Integer.parseInt(ctx.args[2]);
                        if (target.length() > 0 && targetRank > -1 && targetRank < 6) {
                            Player player = findPlayer(target);
                            if (player == null) {
                                eb.setTitle("Command terminated");
                                eb.setDescription("Player not found.");
                                eb.setColor(Pals.error);
                                ctx.channel.sendMessage(eb);
                                return;
                            }

                            PlayerData pd = getData(player.uuid);
                            if (pd != null) {
                                pd.rank = targetRank;
                                setData(player.uuid, pd);
                                eb.setTitle("Command executed successfully");
                                eb.setDescription("Promoted " + escapeCharacters(player.name) + " to " + targetRank);
                                eb.setColor(Pals.success);
                                ctx.channel.sendMessage(eb);
                                player.con.kick("Your rank was modified, please rejoin.", 0);
                            }

                            if (targetRank >= 4) netServer.admins.adminPlayer(player.uuid, player.usid);
                        }
                    });
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("ban") {
                {
                    help = "<player> [reason..] Ban a player with  a specific reason";
                    role = adminRole;
                }
                @Override
                public void run(Context ctx) {
                    CompletableFuture.runAsync(() -> {
                       EmbedBuilder eb = new EmbedBuilder();
                       String target = ctx.args[1];
                       String reason = ctx.message.substring(target.length() + 1);
                       Player player = findPlayer(target);
                       if(player != null) {
                           String uuid = player.uuid;
                           String banId = uuid.substring(0, 4);
                           PlayerData pd = getData(uuid);
                           if(pd != null) {
                               pd.banned = true;
                               pd.banReason = reason;
                               pd.banID = banId;
                               setData(player.uuid, pd);
                           }
                           eb.setTitle("Banned `" + escapeCharacters(player.name) + "` permanently.");
                           eb.addField("UUID", uuid);
                           eb.addField("Ban ID", banId);
                           eb.addInlineField("Reason", reason);
                           eb.setColor(Pals.success);
                           ctx.channel.sendMessage(eb);
                           player.con.kick("You are banned on this server.", 0);
                       } else {
                           eb.setTitle("Player `" + escapeCharacters(target) + "` not found");
                           eb.setColor(Pals.error);
                           ctx.channel.sendMessage(eb);
                       }
                    });
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("blacklist") {
                {
                    help = "<uuid> Ban a player by the provided uuid.";
                    role = adminRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTimestampToNow();
                    String target = ctx.args[1];
                    PlayerData pd = getData(target);
                    Administration.PlayerInfo info = netServer.admins.getInfoOptional(target);

                    if (pd != null && info != null) {
                        pd.banned = true;
                        setData(target, pd);
                        eb.setTitle("Blacklisted successfully.");
                        eb.setDescription("`" + escapeCharacters(info.lastName) + "` was banned.");
                    } else {
                        eb.setTitle("Command terminated");
                        eb.setColor(Pals.error);
                        eb.setDescription("UUID `" + escapeCharacters(target) + "` was not found in the database.");
                    }
                    ctx.channel.sendMessage(eb);
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("unban"){
                {
                    help = "<uuid> Unban the player by the provided uuid.";
                    role = adminRole;
                }

                public void run(Context ctx) {
                    CompletableFuture.runAsync(() -> {
                        EmbedBuilder eb = new EmbedBuilder();
                        String target = ctx.args[1];
                        PlayerData pd = getData(target);

                        if (pd != null) {
                            pd.banned = false;
                            pd.bannedUntil = 0;
                            pd.banReason = "";
                            Administration.PlayerInfo info = netServer.admins.getInfo(target);
                            eb.setTitle("Unbanned `" + escapeCharacters(info.lastName) + "`.");
                            ctx.channel.sendMessage(eb);
                            setData(target, pd);
                        } else {
                            eb.setTitle("UUID `" + escapeCharacters(target) + "` not found in the database.");
                            eb.setColor(Pals.error);
                            ctx.channel.sendMessage(eb);
                        }
                    });
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("changeteam") {
                {
                    help = "<playerid|ip|all|name|teamid> <team> Change the provided player's team into the provided one.";
                    role = adminRole;
                }
                public void run(Context ctx) {
                    String target = ctx.args[1];
                    String targetTeam = ctx.args[2];
                    Team desiredTeam = Team.crux;


                    if(target.length() > 0 && targetTeam.length() > 0) {
                        try {
                            Field field = Team.class.getDeclaredField(targetTeam);
                            desiredTeam = (Team)field.get(null);
                        } catch (NoSuchFieldException | IllegalAccessException ignored) {}

                        EmbedBuilder eb = new EmbedBuilder();
                        if(target.equals("all")) {
                            for (Player p : playerGroup.all()) {
                                p.setTeam(desiredTeam);
                                p.spawner = getCore(p.getTeam());
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's team to " + desiredTeam.name);
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        else if(target.matches("[0-9]+") && target.length()==1){
                            for(Player p : playerGroup.all()){
                                if(p.getTeam().id== Byte.parseByte(target)){
                                    p.setTeam(desiredTeam);
                                    p.spawner = getCore(p.getTeam());
                                }
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed everyone's team to " + desiredTeam.name);
                            ctx.channel.sendMessage(eb);
                            return;
                        }
                        Player player = findPlayer(target);
                        if(player!=null){
                            player.setTeam(desiredTeam);
                            player.spawner = getCore(player.getTeam());
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Changed " + escapeCharacters(player.name) + "s team to " + desiredTeam.name);
                            ctx.channel.sendMessage(eb);
                        }
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("killunits") {
                {
                    help = "<playerid|ip|name> <unit> Kills all units of the team of the specified player";
                    role = adminRole;
                }
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    String targetUnit = ctx.args[2];
                    UnitType desiredUnit = UnitTypes.dagger;

                    if(target.length() > 0 && targetUnit.length() > 0) {
                        try {
                            Field field = UnitTypes.class.getDeclaredField(targetUnit);
                            desiredUnit = (UnitType)field.get(null);
                        } catch (NoSuchFieldException | IllegalAccessException ignored) {}

                        Player player = findPlayer(target);
                        if(player!=null){
                            int amount = 0;
                            for(BaseUnit unit : Vars.unitGroup.all()) {
                                if(unit.getTeam() == player.getTeam()){
                                    if(unit.getType() == desiredUnit) {
                                        unit.kill();
                                        amount++;
                                    }
                                }
                            }
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Killed " + amount + " " + targetUnit + "s on team " + player.getTeam());
                            ctx.channel.sendMessage(eb);
                        }
                    } else{
                        eb.setTitle("Command terminated");
                        eb.setDescription("Invalid arguments provided.");
                        eb.setColor(Pals.error);
                        ctx.channel.sendMessage(eb);
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("spawn") {
                {
                    help = "<playerid|ip|name> <unit> <amount> Spawn x units at the location of the specified player";
                    role = adminRole;
                }
                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    String target = ctx.args[1];
                    String targetUnit = ctx.args[2];
                    int amount = Integer.parseInt(ctx.args[3]);
                    UnitType desiredUnit = UnitTypes.dagger;
                    if(target.length() > 0 && targetUnit.length() > 0 && amount > 0 && amount < 1000) {
                        try {
                            Field field = UnitTypes.class.getDeclaredField(targetUnit);
                            desiredUnit = (UnitType)field.get(null);
                        } catch (NoSuchFieldException | IllegalAccessException ignored) {}

                        Player player = findPlayer(target);
                        if(player!=null){
                            UnitType finalDesiredUnit = desiredUnit;
                            IntStream.range(0, amount).forEach(i -> {
                                BaseUnit unit = finalDesiredUnit.create(player.getTeam());
                                unit.set(player.getX(), player.getY());
                                unit.add();
                            });
                            eb.setTitle("Command executed successfully.");
                            eb.setDescription("Spawned " + amount + " " + targetUnit + " near " + Utils.escapeCharacters(player.name) + ".");
                            ctx.channel.sendMessage(eb);
                        }
                    } else{
                        eb.setTitle("Command terminated");
                        eb.setDescription("Invalid arguments provided.");
                        eb.setColor(Pals.error);
                        ctx.channel.sendMessage(eb);
                    }
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("expel"){
                {
                    help = "<player> <duration (minutes)> [reason..] Ban the provided player for a specific duration with a specific reason.";
                    role = adminRole;
                }

                public void run(Context ctx) {
                    CompletableFuture.runAsync(() -> {
                        EmbedBuilder eb = new EmbedBuilder();
                        String target = ctx.args[1];
                        String targetDuration = ctx.args[2];
                        String reason = ctx.message.substring(target.length() + targetDuration.length() + 2);
                        long now = Instant.now().getEpochSecond();

                        Player player = findPlayer(target);
                        if (player != null) {
                            String uuid = player.uuid;
                            String banId = uuid.substring(0, 4);
                            PlayerData pd = getData(uuid);
                            long until = now + Integer.parseInt(targetDuration) * 60;
                            if (pd != null) {
                                pd.bannedUntil = until;
                                pd.banReason = reason + "\n" + "[accent]Until: " + epochToString(until) + "\n[accent]Ban ID:[] " + banId;
                                setData(uuid, pd);
                            }

                            eb.setTitle("Banned `" + escapeCharacters(player.name) + "` permanently.");
                            eb.addField("UUID", uuid);
                            eb.addField("Ban ID", banId);
                            eb.addField("For", targetDuration + " minutes.");
                            eb.addField("Until", epochToString(until));
                            eb.addInlineField("Reason", reason);
                            ctx.channel.sendMessage(eb);

                            player.con.kick(Packets.KickReason.banned);
                        } else {
                            eb.setTitle("Player `" + escapeCharacters(target) + "` not found.");
                            eb.setColor(Pals.error);
                            ctx.channel.sendMessage(eb);
                        }
                    });
                }
            });
            handler.registerCommand(new RoleRestrictedCommand("changemap"){
                {
                    help = "<mapname/mapid> Change the current map to the one provided.";
                    role = adminRole;
                }

                public void run(Context ctx) {
                    EmbedBuilder eb = new EmbedBuilder();
                    if (ctx.args.length < 2) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("Not enough arguments, use `%changemap <mapname|mapid>`".replace("%", main.prefix));
                        ctx.channel.sendMessage(eb);
                        return;
                    }
                    Map found = getMapBySelector(ctx.message.trim());
                    if (found == null) {
                        eb.setTitle("Command terminated.");
                        eb.setColor(Pals.error);
                        eb.setDescription("Map \"" + escapeCharacters(ctx.message.trim()) + "\" not found!");
                        ctx.channel.sendMessage(eb);
                        return;
                    }

                    changeMap(found);

                    eb.setTitle("Command executed.");
                    eb.setDescription("Changed map to " + found.name());
                    ctx.channel.sendMessage(eb);

                    maps.reload();
                }
            });
        }

        handler.registerCommand(new Command("help") {
            {
                help = "Display all available commands and their usage.";
            }
            public void run(Context ctx) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Public commands:");
                EmbedBuilder embed2 = new EmbedBuilder()
                        .setTitle("Restricted commands:");
                for(Command command : handler.getAllCommands()) {
                    if(command instanceof RoleRestrictedCommand) {
                        embed2.addField("**" + command.name + "**", command.help);
                    } else {
                        embed.addField("**" + command.name + "**", command.help);
                    }
                }
                ctx.channel.sendMessage(embed2);
                ctx.channel.sendMessage(embed);
            }
        });

        Log.info("registered commands");
    }
}
