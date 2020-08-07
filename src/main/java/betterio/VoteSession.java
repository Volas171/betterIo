package betterio;
import arc.struct.ObjectSet;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import mindustry.maps.Map;

import static mindustry.Vars.netServer;
import static mindustry.Vars.playerGroup;
import static betterio.Utils.*;

public class VoteSession {
    Map target;
    ObjectSet<String> voted = new ObjectSet<>();
    VoteSession[] map;
    Timer.Task task;
    float voteDuration = 1f *60;
    int votes;
    public VoteSession(VoteSession[] map, Map target){
        this.target = target;
        this.map = map;
        this.task = Timer.schedule(() -> {
            if(!checkPass()) {
                Call.sendMessage("[lightgray]Vote failed, not enough votes to change map into " + target.name());
                map[0] = null;
                task.cancel();
            }
        }, voteDuration);
    }
    public int votesRequired(){
        return(int)(playerGroup.size() / 1.5f);
    }
    void vote(Player player, int d){
        votes += d;
        voted.addAll(player.uuid, netServer.admins.getInfo(player.uuid).lastIP);

        Call.sendMessage(Strings.format("[orange]{0}[lightgray] has voted to change the map to[orange] {1}[].[accent] ({2}/{3})\n[lightgray]Type[orange] /rtv to agree.",
                player.name, target.name(), votes, votesRequired()));
    }
    boolean checkPass(){
        if(votes >= votesRequired()){
            Call.sendMessage(Strings.format("[orange]Vote passed.[scarlet] changing map to {0}", target.name()));
            changeMap(target);
            map[0] = null;
            task.cancel();
            return true;
        }
        return false;
    }
}
