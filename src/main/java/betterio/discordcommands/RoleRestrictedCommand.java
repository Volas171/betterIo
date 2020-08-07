package betterio.discordcommands;

import arc.util.Log;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.permission.Role;

import java.util.Optional;

public abstract class RoleRestrictedCommand extends Command {
    public String role = null;
    public Role resolvedRole = null;
    public RoleRestrictedCommand(String name) {
        super(name);
    }
    @Override
    public boolean hasPermission(Context ctx) {
        if(role == null) return false;
        if(ctx.event.isPrivateMessage()) return false;
        if(resolvedRole == null){
            resolvedRole = getRole(ctx.event.getApi(), role);
            if(resolvedRole == null) return false;
        }
        return ctx.event.getMessageAuthor().asUser().get().getRoles(ctx.event.getServer().get()).contains(resolvedRole);
    }
    public Role getRole(DiscordApi api, String id) {
        Optional<Role> r1 = api.getRoleById(id);
        if(!r1.isPresent()) {
            Log.err("Ohno, can't find role id: " + id);
        }
        return r1.get();
    }
}
