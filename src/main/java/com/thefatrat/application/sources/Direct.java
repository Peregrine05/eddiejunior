package com.thefatrat.application.sources;

import com.thefatrat.application.Bot;
import com.thefatrat.application.entities.Reply;
import com.thefatrat.application.exceptions.BotErrorException;
import com.thefatrat.application.exceptions.BotException;
import com.thefatrat.application.exceptions.BotWarningException;
import com.thefatrat.application.handlers.MessageHandler;
import com.thefatrat.application.util.Colors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Direct extends Source {

    private final Map<String, Message> cache = new HashMap<>();

    @Override
    public void receiveMessage(Message message, Reply reply) {
        Thread thread = new Thread(() -> {

            String userId = message.getAuthor().getId();
            List<Guild> mutualGuilds = Bot.getInstance().retrieveMutualGuilds(message.getAuthor()).complete();

            if (mutualGuilds.isEmpty() || cache.containsKey(userId)) {
                return;
            }

            cache.put(message.getAuthor().getId(), message);

            if (mutualGuilds.size() == 1) {
                String serverId = mutualGuilds.get(0).getId();
                sendComponentMenu(userId, serverId, reply);
                return;
            }

            SelectMenu.Builder menu = SelectMenu.create("server")
                .setMaxValues(1);

            for (Guild guild : mutualGuilds) {
                menu.addOption(guild.getName(), guild.getId());
            }

            message.replyEmbeds(new EmbedBuilder()
                    .setColor(Colors.BLUE)
                    .setDescription("What server do you want to send this to?")
                    .build()
                )
                .addActionRow(menu.build())
                .addActionRow(Button.danger("x", "Cancel"))
                .queue();

        });
        thread.setUncaughtExceptionHandler(new ChannelExceptionHandler(() -> {}, reply));
        thread.start();
    }

    private void sendComponentMenu(String userId, String serverId, Reply reply) {
        Server server = Bot.getInstance().getServer(serverId);
        if (server == null) {
            throw new BotErrorException("Something went wrong");
        }

        Set<String> keys = server.getDirectHandler().getKeys();

        if (keys.isEmpty()) {
            cache.remove(userId);
            throw new BotWarningException("The server does not handle any messages at the moment");
        }

        SelectMenu.Builder menu = SelectMenu.create("component")
            .setMaxValues(1);
        for (String key : server.getDirectHandler().getKeys()) {
            String name = key.substring(0, 1).toUpperCase() + key.substring(1);
            menu.addOption(name, serverId + "-" + key);
        }

        reply.sendEmbed(new EmbedBuilder()
                .setColor(Colors.BLUE)
                .setDescription("What service do you want to send this to?")
                .build(),
            callback -> callback
                .editMessageComponents()
                .setComponents(
                    ActionRow.of(menu.build()),
                    ActionRow.of(Button.danger("x", "Cancel")))
                .queue()
        );

    }

    public void selectMenu(String userId, String menuId, String option, Message message, Reply reply) {
        if ("component".equals(menuId)) {
            message.delete().queue();
            String[] split = option.split("-");
            String serverId = split[0];
            String component = split[1];

            Message userMessage = cache.remove(userId);

            Server server = Bot.getInstance().getServer(serverId);
            if (server == null) {
                throw new BotErrorException("Something went wrong");
            }

            MessageHandler handler = server.getDirectHandler();

            if (!handler.getKeys().contains(component)) {
                throw new BotErrorException("Could not send to the given service, try again");
            }

            handler.handle(component, userMessage, reply);

        } else if ("server".equals(menuId)) {
            message.delete().queue();
            sendComponentMenu(userId, option, reply);
        }
    }

    public void clickButton(String userId, String buttonId, Message message, Reply reply) {
        if ("x".equals(buttonId)) {
            message.delete().queue();
            cache.remove(userId);
            reply.sendEmbedFormat(Colors.BLUE, ":stop_sign: Successfully cancelled");
        }
    }

    private record ChannelExceptionHandler(Runnable action, Reply reply)
        implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (e instanceof BotException error) {
                action.run();
                reply.sendEmbedFormat(error.getColor(), error.getMessage());
            }
        }

    }

}
