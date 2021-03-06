package net.artifactgaming.carlbot.modules.statistics;

import net.artifactgaming.carlbot.Utils;
import net.artifactgaming.carlbot.listeners.MessageReader;
import net.artifactgaming.carlbot.modules.statistics.ChannelStatistic.WeeklyChannelStatistics;
import net.artifactgaming.carlbot.modules.statistics.DatabaseSQL.SettingsDatabaseHandler;
import net.artifactgaming.carlbot.modules.statistics.DatabaseSQL.StatisticsDatabaseHandler;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.text.ParseException;

public class MessageStatisticCollector implements MessageReader {

    private Logger logger = LoggerFactory.getLogger(Statistics.class);

    private SettingsDatabaseHandler settingsDatabaseHandler;

    private StatisticsDatabaseHandler statisticsDatabaseHandler;

    MessageStatisticCollector(SettingsDatabaseHandler _settingsDatabaseHandler, StatisticsDatabaseHandler _statisticsDatabaseHandler){
        settingsDatabaseHandler = _settingsDatabaseHandler;
        statisticsDatabaseHandler = _statisticsDatabaseHandler;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (messageFromBotOrNonGuild(event)){
            return;
        }

        if (guildHasStatisticsEnabled(event.getGuild())){
            updateChannelStatisticsWithNewMessage(event.getTextChannel(), event.getMessage());
        }
    }

    private void updateChannelStatisticsWithNewMessage(TextChannel channel, Message newMessage){
        try {
            WeeklyChannelStatistics thisChannelWeeklyStatistics = statisticsDatabaseHandler.getWeeklyChannelStatistics(channel.getGuild(), channel);

            thisChannelWeeklyStatistics.setChannelID(channel.getId());
            thisChannelWeeklyStatistics.setChannelName(channel.getName());

            thisChannelWeeklyStatistics.incrementNoOfMessagesSent();
            if (Utils.messageContainsImage(newMessage)){
                thisChannelWeeklyStatistics.incrementNoOfMessagesSentWithImage();
            }

            statisticsDatabaseHandler.updateWeeklyChannelStatistics(channel.getGuild(), thisChannelWeeklyStatistics);

        } catch (SQLException e){
            logger.error("Error trying update data on a channel statistics :: " + e.getMessage());
        } catch (ParseException e){
            logger.error("Error trying to parse the date :: " + e.getMessage());
        }
    }

    private boolean guildHasStatisticsEnabled(Guild guild){
        try {
            StatisticsSettings statsSettings = settingsDatabaseHandler.getStatisticSettingsInGuild(guild);
            return statsSettings.isEnabled();
        } catch (SQLException e){
            logger.error("Error trying to determine if a channel is tracked by statistics :: " + e.getMessage());
            return false;
        }
    }

    private boolean messageFromBotOrNonGuild(MessageReceivedEvent event){
        return event.getAuthor().isBot() || event.getGuild() == null;
    }
}
