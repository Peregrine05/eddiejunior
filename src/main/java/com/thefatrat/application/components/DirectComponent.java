package com.thefatrat.application.components;

import com.thefatrat.application.Bot;
import com.thefatrat.application.entities.Command;
import com.thefatrat.application.exceptions.BotErrorException;
import com.thefatrat.application.exceptions.BotWarningException;
import com.thefatrat.application.reply.Reply;
import com.thefatrat.application.sources.Server;
import com.thefatrat.application.util.Colors;
import com.thefatrat.application.util.Icon;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class DirectComponent extends Component {

    private final boolean autoRun;
    private final Set<String> blacklist = new HashSet<>();
    private boolean running = false;
    private String destination;

    private final BiConsumer<Message, Reply> receiver = (message, reply) -> {
        if (getDestination() == null) {
            throw new BotErrorException("Something went wrong");
        }
        if (getBlacklist().contains(message.getAuthor().getId())) {
            throw new BotWarningException("You are not allowed to send messages at the moment");
        }
        handleDirect(message, reply);
    };

    public DirectComponent(Server server, String name, boolean autoRun) {
        super(server, name, false);
        this.autoRun = autoRun;
        destination = getDatabaseManager().getSetting("destination");
        if (autoRun && Boolean.parseBoolean(getDatabaseManager().getSettingOr("running", false))) {
            start(Reply.empty());
        }
        blacklist.addAll(getDatabaseManager().getSettings("blacklist"));

        setComponentCommand();

        addSubcommands(new Command("start", "starts the component")
                .addOption(new OptionData(OptionType.CHANNEL, "channel", "channel destination")
                    .setChannelTypes(ChannelType.TEXT)
                )
                .setAction((command, reply) -> {
                    OptionMapping option = command.getArgs().get("channel");
                    TextChannel parsedDestination = option == null ? null : option.getAsChannel().asTextChannel();
                    TextChannel newDestination;
                    TextChannel currentDestination = getDestination();

                    if (currentDestination == null && parsedDestination == null) {
                        try {
                            newDestination = command.getChannel().asTextChannel();
                        } catch (IllegalStateException e) {
                            throw new BotErrorException("Can only start in a text channel");
                        }
                    } else {
                        if (parsedDestination != null) {
                            newDestination = parsedDestination;
                        } else {
                            newDestination = getDestination();
                        }
                    }

                    if (currentDestination == null || !newDestination.getId().equals(currentDestination.getId())) {

                        Bot.getInstance().requirePermission(newDestination, Permission.MESSAGE_EMBED_LINKS);

                        setDestination(newDestination.getId());

                        reply.send(Icon.SETTING, "Destination set to %s `(%s)`%n",
                            newDestination.getAsMention(), newDestination.getId()
                        );
                    }

                    start(reply);
                }),
            new Command("stop", "stops the component")
                .setAction((command, reply) -> this.stop(reply)),
            new Command("destination", "sets the destination channel")
                .addOption(new OptionData(OptionType.CHANNEL, "channel", "destination channel", false)
                    .setChannelTypes(ChannelType.TEXT)
                )
                .setAction((command, reply) -> {
                    TextChannel newDestination;
                    if (command.getArgs().containsKey("channel")) {
                        newDestination = command.getArgs().get("channel").getAsChannel().asTextChannel();
                    } else {
                        try {
                            newDestination = command.getChannel().asTextChannel();
                        } catch (IllegalStateException e) {
                            throw new BotErrorException("Destination can only be a text channel");
                        }
                    }

                    Bot.getInstance().requirePermission(newDestination, Permission.MESSAGE_EMBED_LINKS);

                    setDestination(newDestination.getId());

                    reply.send(Icon.SETTING, "Destination set to %s `(%s)`%n",
                        newDestination.getAsMention(), newDestination.getId());
                }),
            new Command("blacklist", "manages the blacklist")
                .addOption(new OptionData(OptionType.STRING, "action", "action", true)
                    .addChoice("add", "add")
                    .addChoice("remove", "remove")
                    .addChoice("show", "show")
                    .addChoice("clear", "clear")
                )
                .addOption(new OptionData(OptionType.USER, "user", "user", false))
                .setAction((command, reply) -> {
                    String action = command.getArgs().get("action").getAsString();
                    if (!command.getArgs().containsKey("user")
                        && ("add".equals(action) || "remove".equals(action))) {
                        throw new BotErrorException("Please specify the user");
                    }

                    if ("show".equals(action)) {
                        if (blacklist.isEmpty()) {
                            throw new BotWarningException("No users are added to the blacklist");
                        }

                        long[] blacklistIds = new long[blacklist.size()];
                        int i = 0;
                        for (String s : blacklist) {
                            blacklistIds[i] = Long.parseLong(s);
                            ++i;
                        }
                        command.getGuild()
                            .retrieveMembersByIds(blacklistIds)
                            .onSuccess(list -> {
                                String[] strings = fillAbsent(blacklist, list, ISnowflake::getId,
                                    IMentionable::getAsMention).toArray(String[]::new);
                                reply.send(new EmbedBuilder()
                                    .setColor(Colors.TRANSPARENT)
                                    .setTitle(getTitle() + " blacklist")
                                    .setDescription(String.join("\n", strings))
                                    .build());
                            });
                        return;
                    }

                    if ("clear".equals(action)) {
                        if (blacklist.isEmpty()) {
                            throw new BotWarningException("The blacklist is already empty");
                        }

                        blacklist.clear();
                        getDatabaseManager().removeSetting("blacklist")
                            .thenRun(() ->
                                reply.ok("Blacklist cleared")
                            );
                        return;
                    }

                    boolean add = "add".equals(action);
                    String msg;

                    if (add) {
                        msg = "added to";
                    } else {
                        msg = "removed from";
                    }

                    Member member = command.getArgs().get("user").getAsMember();

                    if (member != null) {
                        blacklist(member, add, msg, reply);
                        return;
                    }
                    throw new BotErrorException("The given member was not found");
                })
        );
    }

    private void blacklist(Member member, boolean add, String msg, Reply reply) {
        User user = member.getUser();
        String userId = member.getId();

        if (add) {
            if (blacklist.contains(userId)) {
                throw new BotWarningException("%s is already on the blacklist", member.getAsMention());
            }

            blacklist.add(userId);
            getDatabaseManager().addSetting("blacklist", userId);
        } else {
            if (!blacklist.contains(userId)) {
                throw new BotWarningException("%s is not on the blacklist", member.getAsMention());
            }

            blacklist.remove(userId);
            getDatabaseManager().removeSetting("blacklist", userId);
        }

        reply.ok("%s %s the blacklist", user.getAsMention(), msg);
    }

    @Override
    public void disable() {
        super.disable();
    }

    public Set<String> getBlacklist() {
        return blacklist;
    }

    protected abstract void handleDirect(Message message, Reply reply);

    protected void stop(Reply reply) {
        getServer().getDirectHandler().removeListener(getName());
        this.running = false;
        if (autoRun) {
            getDatabaseManager().setSetting("running", "false");
        }
    }

    protected void start(Reply reply) {
        this.running = true;
        getServer().getDirectHandler().addListener(getName(), receiver);
        if (autoRun) {
            getDatabaseManager().setSetting("running", "true");
        }
    }

    public final void setDestination(String destination) {
        this.destination = destination;
        getDatabaseManager().setSetting("destination", destination);
    }

    public final TextChannel getDestination() {
        if (destination == null) {
            return null;
        }
        return getServer().getGuild().getTextChannelById(destination);
    }

    public boolean isRunning() {
        return running && isEnabled();
    }

}
