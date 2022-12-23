package com.thefatrat.application.components;

import com.thefatrat.application.Bot;
import com.thefatrat.application.entities.Command;
import com.thefatrat.application.exceptions.BotWarningException;
import com.thefatrat.application.sources.Server;
import com.thefatrat.application.util.Colors;
import com.thefatrat.application.util.Icons;
import com.thefatrat.database.Database;
import com.thefatrat.database.DatabaseException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class Manager extends Component {

    public static final String NAME = "Main";

    public Manager(Server server) {
        super(server, NAME, true);

        addCommands(
            new Command("help", "show the available commands")
                .addOption(new OptionData(OptionType.STRING, "component", "component name", false))
                .setAction((command, reply) -> {
                    if (command.getArgs().containsKey("component")) {
                        String componentString = command.getArgs().get("component").getAsString();
                        Component component = getServer().getComponent(componentString);

                        if (component == null) {
                            componentNotFound(componentString);
                            return;
                        }

                        reply.send(component.getHelp());
                    } else {
                        reply.send(getHelp());
                    }
                }),

            new Command("ping", "check the RTT of the connection in milliseconds")
                .setAction((command, reply) -> reply.send(
                    new EmbedBuilder()
                        .setColor(Colors.BLUE)
                        .addField("WebSocket", Bot.getInstance().getJDA().getGatewayPing() + " ms", true)
                        .build(),
                    message -> {
                        MessageEmbed embed = message.getEmbeds().get(0);
                        try {
                            long start2 = System.currentTimeMillis();
                            Database database = Database.getInstance().connect();
                            long time2 = System.currentTimeMillis() - start2;
                            message.editMessageEmbeds(new EmbedBuilder(embed)
                                .addField("Database", time2 + " ms", true)
                                .build()
                            ).queue();
                            database.close();
                        } catch (DatabaseException e) {
                            message.editMessageEmbeds(new EmbedBuilder(embed)
                                .addField("Database", ":x:", true)
                                .build()
                            ).queue();
                        }
                    }
                )),

            new Command("enable", "enable a specific component by name")
                .addOption(new OptionData(OptionType.STRING, "component", "component name", true))
                .setAction((command, reply) -> {
                    String componentString = command.getArgs().get("component").getAsString();
                    Component component = getServer().getComponent(componentString);

                    if (component == null) {
                        componentNotFound(componentString);
                        return;
                    }
                    if (component.isAlwaysEnabled()) {
                        throw new BotWarningException("This component is always enabled");
                    }
                    component.getDatabaseManager().toggleComponent(true)
                        .thenRun(() -> {
                            getServer().toggleComponent(component, true);
                            reply.send(Icons.ENABLE, Colors.WHITE, "Component `%s` enabled", componentString);
                        });
                }),

            new Command("disable", "disable a specific component by name")
                .addOption(new OptionData(OptionType.STRING, "component", "component name", true))
                .setAction((command, reply) -> {
                    String componentString = command.getArgs().get("component").getAsString();

                    Component component = getServer().getComponent(componentString);
                    if (component == null) {
                        componentNotFound(componentString);
                        return;
                    }

                    if (component.isAlwaysEnabled()) {
                        throw new BotWarningException("This component is always enabled");
                    }

                    if (component instanceof DirectComponent direct) {
                        if (direct.isRunning()) {
                            direct.stop(reply);
                        }
                    }

                    component.getDatabaseManager().toggleComponent(false)
                        .thenRun(() -> {
                            getServer().toggleComponent(component, false);

                            reply.send(Icons.DISABLE, Colors.WHITE, "Component `%s` disabled", componentString);
                        });
                }),

            new Command("components", "shows a list of all the components")
                .setAction((command, reply) -> {
                    StringBuilder builder = new StringBuilder();
                    for (Component component : getServer().getComponents()) {
                        if (component.isAlwaysEnabled()) {
                            continue;
                        }
                        builder.append(component.getTitle());

                        if (component.isEnabled()) {
                            builder.append(" ").append(Icons.ENABLE);

                            if (component instanceof DirectComponent direct && direct.isRunning()) {
                                builder.append(" ").append(Icons.OK);
                            }
                        }
                        builder.append("\n");
                    }
                    builder.deleteCharAt(builder.length() - 1);
                    reply.send(new EmbedBuilder()
                        .setColor(Colors.BLUE)
                        .addField("Components", builder.toString(), false)
                        .build()
                    );
                }),

            new Command("status", "shows the current status of the bot")
                .addOption(new OptionData(OptionType.STRING, "component", "component name", false))
                .setAction((command, reply) -> {
                    Component component;

                    if (command.getArgs().containsKey("component")) {
                        String componentString = command.getArgs().get("component").getAsString();
                        component = getServer().getComponent(componentString);

                        if (component == null) {
                            componentNotFound(componentString);
                            return;
                        }

                    } else {
                        component = this;
                    }

                    MessageEmbed embed = new EmbedBuilder()
                        .setColor(Colors.BLUE)
                        .setDescription(component.getStatus())
                        .setFooter(component.getName())
                        .build();

                    reply.send(embed);
                })
        );
    }

    @Override
    public String getStatus() {
        int count = 0;
        for (Component component : getServer().getComponents()) {
            if (component.isEnabled() && !component.isAlwaysEnabled()) {
                ++count;
            }
        }
        return String.format("""
                Components enabled: %d
                Uptime: %s
                """,
            count,
            Bot.getInstance().getUptime());
    }

}
