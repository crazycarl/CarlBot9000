package net.artifactgaming.carlbot.modules.schedule;

import java.util.Timer;
import java.util.TimerTask;

public class Schedule {
    private String userID;
    private String guildID;
    private String channelID;

    private String commandRawString;

    private OnScheduleInterval onScheduleInterval;

    private int intervalHours;

    private Timer scheduleTimer;

    public Schedule(String userID, String guildID, String channelID, String commandRawString, int intervalHours) {
        this.userID = userID;
        this.guildID = guildID;
        this.channelID = channelID;
        this.commandRawString = commandRawString;
        this.intervalHours = intervalHours;
        setupScheduleTimer();
    }

    public Schedule(String userID, String guildID, String channelID, String commandRawString, int intervalHours, boolean startTimer) {
        this.userID = userID;
        this.guildID = guildID;
        this.channelID = channelID;
        this.commandRawString = commandRawString;
        this.intervalHours = intervalHours;

        if (startTimer){
            setupScheduleTimer();
        }
    }

    public void startScheduleTimer(){
        if (scheduleTimer == null){
            setupScheduleTimer();
        }
    }

    private void setupScheduleTimer(){
        scheduleTimer = new Timer();
        scheduleTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (onScheduleInterval != null){
                    onScheduleInterval.onScheduleIntervalCallback(Schedule.this);
                }
            }
        }, intervalHours*60*1000, intervalHours*60*1000);
    }

    public void setOnScheduleIntervalListener(OnScheduleInterval onScheduleInterval){
        this.onScheduleInterval = onScheduleInterval;
    }

    public void stopScheduleTimer(){
        scheduleTimer.cancel();
        scheduleTimer.purge();
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getGuildID() {
        return guildID;
    }

    public void setGuildID(String guildID) {
        this.guildID = guildID;
    }

    public String getChannelID() {
        return channelID;
    }

    public void setChannelID(String channelID) {
        this.channelID = channelID;
    }

    public String getCommandRawString() {
        return commandRawString;
    }

    public void setCommandRawString(String commandRawString) {
        this.commandRawString = commandRawString;
    }

    public int getIntervalHours() {
        return intervalHours;
    }

    public void setIntervalHours(int intervalHours) {
        this.intervalHours = intervalHours;
    }
}
