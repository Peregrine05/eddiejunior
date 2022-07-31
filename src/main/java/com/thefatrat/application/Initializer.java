package com.thefatrat.application;

import com.thefatrat.application.components.Feedback;
import com.thefatrat.application.components.Manager;
import com.thefatrat.application.components.ModMail;
import com.thefatrat.database.DatabaseAuthenticator;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;

public class Initializer {

    public static void main(String[] args) {
        DatabaseAuthenticator.getInstance().authenticate();

        final String token = getToken();
        final JDA jda;

        try {
            jda = JDABuilder.createDefault(token)
                .enableIntents(
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.DIRECT_MESSAGES,
                    GatewayIntent.GUILD_PRESENCES
                )
                .setChunkingFilter(ChunkingFilter.ALL)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(Bot.getInstance())
                .build();
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }

        Bot.getInstance().setComponents(
            Manager.class,
            ModMail.class,
            Feedback.class
        );

        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getToken() {
        try (InputStream config = Initializer.class.getClassLoader()
            .getResourceAsStream("config.cfg")) {

            Properties properties = new Properties();
            properties.load(config);

            return Objects.requireNonNull(properties.getProperty("bot_token"));

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}