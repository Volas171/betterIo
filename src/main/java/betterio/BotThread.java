package betterio;

import arc.math.Mathf;
import arc.net.Server;
import betterio.datas.PersistentPlayerData;
import betterio.datas.PlayerData;
import betterio.discordcommands.DiscordCommands;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import org.javacord.api.DiscordApi;
import org.json.JSONObject;

import static betterio.Utils.*;
import static mindustry.Vars.*;

public class BotThread extends Thread{
    public DiscordApi api;
    private Thread mt;
    private JSONObject data;
    public DiscordCommands commandHandler = new DiscordCommands();

    public BotThread(DiscordApi api, Thread mt, JSONObject data){
        this.api = api;
        this.mt = mt;
        this.data = data;
        this.api.addMessageCreateListener(commandHandler);
        new ServerCommands(data).registerCommands(commandHandler);
    }

    @Override
    public void run() {
        while (this.mt.isAlive()) {
            try {
                Thread.sleep(10000);
                if (Mathf.chance(0.01f)) {
                    api.updateActivity("( ͡° ͜ʖ ͡°) | " + main.prefix + "help | mindustry.me");
                } else {
                    api.updateActivity("with " + Groups.player.size() + (netServer.admins.getPlayerLimit() == 0 ? "" : "/" + netServer.admins.getPlayerLimit()) + " players | " + main.prefix + "help | mindustry.me");
                }
            } catch(Exception e) {}
        }


        api.disconnect();
    }
}
