package betterio.discordcommands;

public abstract class Command {
    public String help = "No command provided. Creman, awman.";
    public String name;
    public Command(String name) {
        this.name = name.toLowerCase();
    }
    public abstract void run(Context ctx);
    public boolean hasPermission(Context ctx) {
        return true;
    }
}
