package net.artifactgaming.carlbot.modules.schedule;

import net.artifactgaming.carlbot.*;
import net.artifactgaming.carlbot.modules.authority.Authority;
import net.artifactgaming.carlbot.modules.authority.AuthorityManagement;
import net.artifactgaming.carlbot.modules.authority.AuthorityRequiring;
import net.artifactgaming.carlbot.modules.persistence.Persistence;
import net.artifactgaming.carlbot.modules.persistence.PersistentModule;
import net.artifactgaming.carlbot.modules.persistence.Table;
import net.artifactgaming.carlbot.modules.quotes.Quotes;
import net.artifactgaming.carlbot.modules.selfdocumentation.Documented;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class Schedules implements Module, AuthorityRequiring, PersistentModule, Documented {

    private AuthorityManagement authorityManagement;
    private Persistence persistence;

    private Logger logger = LoggerFactory.getLogger(Schedules.class);

    private CarlBot carlBot;
    private Map<String, SchedulableCommand> schedulableModules = new HashMap<>();

    private ArrayList<Schedule> schedules;

    @Override
    public void setup(CarlBot carlbot) {
        this.carlBot = carlBot;

        // Get the authority module.
        authorityManagement = (AuthorityManagement) carlbot.getModule(AuthorityManagement.class);

        if (authorityManagement == null) {
            logger.error("Authority module is not loaded.");
            carlbot.crash();
        }

        // Get the persistence module.
        persistence = (Persistence) carlbot.getModule(Persistence.class);

        if (persistence == null) {
            logger.error("Persistence module is not loaded.");
            carlbot.crash();
        }

        loadAllSchedulableCommandsIntoHashMap();
        loadAllSchedulesFromDatabase();
    }

    private void loadAllSchedulableCommandsIntoHashMap(){
        for (Module module : carlBot.getModules()) {
            if (module instanceof SchedulableCommand) {
                SchedulableCommand command = (SchedulableCommand) module;
                schedulableModules.put(command.getCallsign(), command);
            }
        }
    }

    private void loadAllSchedulesFromDatabase(){
        schedules = new ArrayList<Schedule>();
        // TODO: Get all schedules in the database from all guides.
    }

    private Table getScheduleTable(Guild guild) throws SQLException {
        Table table = persistence.getGuildTable(guild, this);
        Table scheduleTable = new Table(table, "schedules");

        if (!scheduleTable.exists()) {
            scheduleTable.create();

            scheduleTable.alter().add()
                    .pushValue("owner_ID varchar")
                    .pushValue("owner_name varchar")
                    .pushValue("guild_ID varchar")
                    .pushValue("channel_ID varchar")
                    .pushValue("command_rawString varchar")
                    .pushValue("interval int")
                    .execute();
        }

        return scheduleTable;
    }

    private ArrayList<Schedule> getSchedulesFromTable(Guild guild) throws SQLException {
        Table scheduleTable = getScheduleTable(guild);

        ResultSet resultSet = scheduleTable.select().execute();

        ArrayList<Schedule> fetchedSchedules = new ArrayList<>();

        // Add all schedules from the guild into the array.
        while (resultSet.next()){
            String ownerID = resultSet.getString("owner_ID");
            String guildID = resultSet.getString("guild_ID");
            String channelID = resultSet.getString("channel_ID");
            String commandRawString = resultSet.getString("command_rawString");
            int interval = resultSet.getInt("interval");

            Schedule temp = new Schedule(ownerID, guildID, channelID, commandRawString, interval, false);

            fetchedSchedules.add(temp);
        }

        return fetchedSchedules;
    }

    private void addScheduleToTable(Guild guild, Schedule schedule) throws SQLException {
        Table scheduleTable = getScheduleTable(guild);

        scheduleTable.insert()
                .set("owner_ID", schedule.getUserID())
                .set("guild_ID", schedule.getGuildID())
                .set("channel_ID", schedule.getChannelID())
                .set("command_rawString", schedule.getCommandRawString())
                .set("interval", Integer.toString(schedule.getIntervalHours()))
                .execute();
    }

    private class getScheduleCommand implements Command, Documented {

        @Override
        public String getCallsign() {
            return "get";
        }

        @Override
        public void runCommand(MessageReceivedEvent event, String rawString, List<String> tokens) throws Exception {
            ArrayList<Schedule>  guildSchedules = getSchedulesFromTable(event.getGuild());

            for (Schedule schedule :  guildSchedules){
                // TODO: Print out all the schedules in the guild.
            }
        }

        @Override
        public Module getParentModule() {
            return Schedules.this;
        }

        @Override
        public String getDocumentation() {
            return "Fetches all the currently scheduled commands in this server.";
        }

        @Override
        public String getDocumentationCallsign() {
            return "get";
        }
    }

    private class addScheduleCommand implements  Command, AuthorityRequiring, Documented {
        @Override
        public String getCallsign() {
            return "add";
        }

        @Override
        public void runCommand(MessageReceivedEvent event, String rawString, List<String> tokens) throws Exception {
            if (tokens.size() < 2){
                event.getChannel().sendMessage("Wrong number of arguments. Command should be:\n$>schedule add \"hour\" \"commandToInvoke\"").queue();
                return;
            }

            if (event.getGuild() == null){
                event.getChannel().sendMessage("This command can only be invoked in a server.").queue();
                return;
            }

            ObjectResult<SchedulableCommand> schedulableCommandObjectResult = tryGetSchedulableCommandFromTokens(tokens);

            if (schedulableCommandObjectResult.getResult()){
                SchedulableCommand commandToSchedule = schedulableCommandObjectResult.getObject();

                // TODO: Check if the user have authority to schedule the scheduled command.

                ObjectResult<Schedule> scheduleObjectResult = tryGetScheduleFromRanCommand(event, rawString, tokens);

                if (scheduleObjectResult.getResult()){
                    Schedule newSchedule = scheduleObjectResult.getObject();
                    newSchedule.setOnScheduleIntervalListener(new OnScheduleIntervalReached());

                    addScheduleToTable(event.getGuild(), newSchedule);
                } else {
                    event.getChannel().sendMessage(scheduleObjectResult.getResultMessage()).queue();
                }
            } else {
                event.getChannel().sendMessage(schedulableCommandObjectResult.getResultMessage()).queue();
            }
        }

        private ObjectResult<Schedule> tryGetScheduleFromRanCommand(MessageReceivedEvent event, String rawString, List<String> tokens){
            try {
                Schedule newSchedule = new Schedule(event.getAuthor().getId(), event.getGuild().getId(), event.getChannel().getId(), rawString, Integer.parseInt(tokens.get(0)));
                return new ObjectResult<>(newSchedule);
            } catch (IndexOutOfBoundsException e) {
                return new ObjectResult<>(null, "Wrong number of arguments. Command should be:\n$>schedule add \"hour\" \"commandToInvoke\"");
            } catch (NumberFormatException e){
                return new ObjectResult<>(null, "Argument is of wrong type.  Command should be:\n$>schedule add \"hour\" \"commandToInvoke\" \nWhere \"Hour\" is a number.");
            }
        }

        private ObjectResult<SchedulableCommand> tryGetSchedulableCommandFromTokens(List<String> tokens){
            try {
                SchedulableCommand commandToSchedule = null;

                String token = tokens.get(1);
                SchedulableCommand temp = schedulableModules.get(token);

                if (temp instanceof CommandSet) {
                    if (1 == tokens.size() - 1) {
                        return new ObjectResult<>(null, "Modules can not be scheduled.");
                    }

                    CommandSet tempCommandSet = (CommandSet) temp;

                    boolean commandToScheduleFound = false;
                    for (Command commandInCommandSet : tempCommandSet.getCommands()) {
                        // If this command can be scheduled, and it matches the callsign.
                        if (commandInCommandSet instanceof SchedulableCommand) {
                            if (commandInCommandSet.getCallsign().equals(tokens.get(2))) {
                                commandToSchedule = (SchedulableCommand) commandInCommandSet;
                                commandToScheduleFound = true;
                            }
                        }

                        if (commandToScheduleFound) {
                            break;
                        }
                    }
                } else {
                    commandToSchedule = temp;
                }

                String resultMessage = Utils.STRING_EMPTY;
                if (commandToSchedule == null){
                    resultMessage = "Either the command could not be scheduled, or the command could not be found.";
                }
                return new ObjectResult<>(commandToSchedule, resultMessage);
            } catch (IndexOutOfBoundsException e) {
                // TODO: Error message for the user.
                return new ObjectResult<>(null, "Wrong number of arguments. ");
            }
        }

        @Override
        public Authority[] getRequiredAuthority() {
            return new Authority[] { new UseSchedules() };
        }

        @Override
        public Module getParentModule() {
            return Schedules.this;
        }

        @Override
        public String getDocumentation() {
            return "Adds a new scheduled command to this server.";
        }

        @Override
        public String getDocumentationCallsign() {
            return "add";
        }
    }

    private class ScheduleCommands implements Command, AuthorityRequiring, CommandSet {

        private CommandHandler commands;

        ScheduleCommands(CarlBot carlbot) {
            commands = new CommandHandler(carlbot);

            commands.setSubName(this.getCallsign());
            commands.addCommand(new addScheduleCommand());
            commands.addCommand(new getScheduleCommand());
        }

        @Override
        public String getCallsign() {
            return "schedule";
        }

        @Override
        public void runCommand(MessageReceivedEvent event, String rawString, List<String> tokens) {
            commands.runCommand(event, rawString, tokens);
        }

        @Override
        public Authority[] getRequiredAuthority() {

            return new Authority[] { new UseSchedules() };
        }

        @Override
        public Module getParentModule() {
            return Schedules.this;
        }

        public Collection<Command> getCommands() {
            return commands.getCommands();
        }
    }

    @Override
    public Command[] getCommands(CarlBot carlbot) {
        return new Command[] { new ScheduleCommands(carlbot) };
    }

    @Override
    public Authority[] getRequiredAuthority() {
        return new Authority[] { new UseSchedules() };
    }

    private class OnScheduleIntervalReached implements OnScheduleInterval {
        @Override
        public void onScheduleIntervalCallback(Schedule schedule){
            // TODO: Invoke command when the interval timer is reached.
        }

    }

    @Override
    public String getDocumentation() {
        return "Module that is related to scheduling commands in a server.";
    }

    @Override
    public String getDocumentationCallsign() {
        return "schedule";
    }
}
