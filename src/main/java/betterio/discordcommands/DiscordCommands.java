package betterio.discordcommands;

import arc.math.Mathf;
import arc.util.Log;
import betterio.main;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class DiscordCommands implements MessageCreateListener {
    public HashMap<String,Command> registry = new HashMap<>();
    public Set<MessageCreatedListener> messageCreatedListenerRegistry = new HashSet<>();
    public DiscordCommands(){

    }
    public void registerCommand(Command c) {
        registry.put(c.name.toLowerCase(), c);
    }
    public void registerCommand(String forcedName, Command c) {
        registry.put(forcedName.toLowerCase(), c);
    }
    public void registerOnMessage(MessageCreatedListener listener) {
        messageCreatedListenerRegistry.add(listener);
    }
    public void onMessageCreate(MessageCreateEvent event) {
        for(MessageCreatedListener listener: messageCreatedListenerRegistry) listener.run(event);;
        String message = event.getMessageContent();
        if (!message.startsWith(main.prefix)) {return;}
        String[] args = message.split(" ");
        int commandLength = args[0].length();
        Log.info("msg");
        args[0] = args[0].substring(main.prefix.length());
        String name = args[0];
        Log.info(name);
        String newMessage = null;
        if (args.length > 1) newMessage = message.substring(commandLength + 1);
        runCommand(name, new Context(event, args, newMessage));
    }
    public void runCommand(String name, Context ctx){
        Command command = registry.get(name);
        if(command == null) {return;}
        if(!command.hasPermission(ctx)) {
            EmbedBuilder eb = new EmbedBuilder().setTitle("No perms").setDescription("You dont have da perms nooby");
            ctx.channel.sendMessage(eb);
            return;
        }
        command.run(ctx);
    }
    public Command getCommand(String name) {
        return registry.get(name.toLowerCase());
    }
    public Collection<Command> getAllCommands(){
        return registry.values();
    }
    public boolean isCommand(String name) {
        return registry.containsKey(name.toLowerCase());
    }
}
