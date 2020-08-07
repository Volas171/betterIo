package betterio.discordcommands;

import arc.util.Log;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.event.message.MessageCreateEvent;

public class Context {
    public MessageCreateEvent event;
    public String[] args;
    public String message;
    public TextChannel channel;
    public MessageAuthor author;
    public Context(MessageCreateEvent event, String[] args, String message) {
        Log.info("context");
        this.event = event;
        this.args = args;
        this.message = message;
        this.channel = event.getChannel();
        this.author = event.getMessageAuthor();


    }
    public void reply(MessageBuilder message) {
        message.send(channel);
    }
    public void reply(String message) {
        MessageBuilder mb = new MessageBuilder();
        mb.append(message);
        mb.send(channel);
    }
}
