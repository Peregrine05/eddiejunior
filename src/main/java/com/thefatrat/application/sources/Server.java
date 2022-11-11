package com.thefatrat.application.sources;

import com.thefatrat.application.Bot;
import com.thefatrat.application.components.Component;
import com.thefatrat.application.entities.Command;
import com.thefatrat.application.entities.Interaction;
import com.thefatrat.application.entities.Reply;
import com.thefatrat.application.events.CommandEvent;
import com.thefatrat.application.events.InteractionEvent;
import com.thefatrat.application.exceptions.BotException;
import com.thefatrat.application.handlers.ArchiveHandler;
import com.thefatrat.application.handlers.CommandHandler;
import com.thefatrat.application.handlers.InteractionHandler;
import com.thefatrat.application.handlers.MessageHandler;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Server extends Source {

    private final String id;
    private final Map<String, String> commandIds = new HashMap<>();
    private final CommandHandler commandHandler = new CommandHandler();
    private final InteractionHandler interactionHandler = new InteractionHandler();
    private final MessageHandler directHandler = new MessageHandler();
    private final ArchiveHandler archiveHandler = new ArchiveHandler();
    private final Map<String, Component> components = new HashMap<>();
    private final DefaultMemberPermissions permissions = DefaultMemberPermissions.enabledFor(
        Permission.USE_APPLICATION_COMMANDS
    );

    public Server(String id) {
        this.id = id;
    }

    public Guild getGuild() {
        return Bot.getInstance().getJDA().getGuildById(id);
    }

    public String getId() {
        return id;
    }

    public MessageHandler getDirectHandler() {
        return directHandler;
    }

    public List<Component> getComponents() {
        return components.values().stream()
            .sorted((c1, c2) -> String.CASE_INSENSITIVE_ORDER.compare(c1.getName(), c2.getName()))
            .collect(Collectors.toList());
    }

    public void toggleComponent(Component component, boolean enable) {
        Guild guild = Objects.requireNonNull(getGuild());

        if (enable) {
            for (Command command : component.getCommands()) {
                guild.upsertCommand(command.getName(), command.getDescription())
                    .setDefaultPermissions(permissions)
                    .addOptions(command.getOptions())
                    .addSubcommands(command.getSubcommandsData())
                    .queue(c ->
                        commandIds.put(command.getName(), c.getId())
                    );
            }
            for (Interaction interaction : component.getInteractions()) {
                guild.upsertCommand(Commands.message(interaction.getName()).setGuildOnly(true))
                    .queue(c ->
                        commandIds.put(interaction.getName(), c.getId())
                    );
            }
            component.enable();
        } else {
            component.disable();
            component.getCommands().stream()
                .flatMap(command -> {
                    String id = commandIds.remove(command.getName());
                    return Stream.ofNullable(id);
                })
                .forEach(id ->
                    guild.deleteCommandById(id).queue()
                );
            component.getInteractions().stream()
                .flatMap(interaction -> {
                    String id = commandIds.remove(interaction.getName());
                    return Stream.ofNullable(id);
                })
                .forEach(id ->
                    guild.deleteCommandById(id).queue()
                );
        }
    }

    public Component getComponent(String componentName) {
        return components.get(componentName.toLowerCase());
    }

    @SafeVarargs
    public final void registerComponents(Class<? extends Component>... components) {
        try {
            for (Class<? extends Component> component : components) {
                Component instance = component.getDeclaredConstructor(Server.class)
                    .newInstance(this);
                instance.register();

                this.components.put(instance.getName(), instance);

                if (instance.isAlwaysEnabled()) {
                    continue;
                }

                if (instance.getDatabaseManager().isComponentEnabled()) {
                    toggleComponent(instance, true);
                }

            }

        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public CommandHandler getCommandHandler() {
        return commandHandler;
    }

    public InteractionHandler getInteractionHandler() {
        return interactionHandler;
    }

    public ArchiveHandler getArchiveHandler() {
        return archiveHandler;
    }

    public void receiveInteraction(InteractionEvent message, Reply reply) {
        interactionHandler.handle(message, reply);
    }

    public void receiveCommand(CommandEvent event, Reply reply) {
        commandHandler.handle(event, reply);
    }

    @Override
    public void receiveMessage(Message message, Reply reply) throws BotException {
        getMessageHandler().handle(message, reply);
    }

}
