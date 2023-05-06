package BatchApp.ServerLauncher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.security.auth.login.LoginException;

// Seriously eclipse why can't you let me use *
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceMoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.Presence;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class App extends ListenerAdapter
{
	private Process serverP = null;								// Global constant stuff things
	final String cmdChannel = "server-commands";							// DC command input
	final String logchannel = "server-log";									// Server log
	final String revChannel = "revives";									// Revive log
	final String lobbyChannel = "lobby-commands";							// Lobby command input
	final String mcDir = "C:\\Users\\JoeRogang\\Documents\\Fabric_Server_1";		// Folder directory for server.jar
	final String backupDir = "C:\\Users\\JoeRogang\\Documents\\MCBackups\\Fabric_Server_1";		// Folder where zip backups get placed
	final String startPerm = "Start";										// Basic perm name
	final String cmdPerm = "Command";										// Advanced perm name
	final String secretVcId = "805044000667074580";											// Shhh... Its a secret.
	final String fakeStart = "807471991345905664";											// For Fiction :)
	final String logChan = "hmm";															// Log channel
	final String lobby1name = "lobby1", lobby2name = "lobby2", lobby3name = "lobby3";		// Names for lobby roles
	private Boolean lobby1lock = false, lobby2lock = false, lobby3lock = false;				// Locked status for lobby roles
	private Boolean backupLock = false;
	private DiscordSyncPipe dsp = null;
	
	final Boolean canRevive = false;			// Enables/disables the revive command
	
	final Boolean expectingHeavy = true;		// Enable heavy mode or not
	final int howHeavy = 1;					// How many lines to send in heavy mode before going light, -1 for unlimited
	
	User verifyCheck = null; 				// Used for y/n responses 
	String verifyName = null;				// Same purpose
	Timer verifyQuery = null;				// Timer for query timeout
	
	private Map<User, Long> userDict = new HashMap<User, Long>();		// This is just here to count 30 min for each user
	
	@Override
	public void onMessageReceived(MessageReceivedEvent event)
	{
		String message = event.getMessage().getContentDisplay();			// Starting variables to reduce function calls
		MessageChannel msgC = event.getChannel();
		String chName = msgC.getName();
		Boolean notBot = !event.getAuthor().isBot();
		
		if(chName.equals(logchannel) && notBot) {				// Discord message to server commands
			event.getGuild().getTextChannelsByName(logChan, false).get(0).sendMessage(event.getMember().getEffectiveName()+" ("+event.getMember().getAsMention()+") passed `"+ message.replace('`', '\'').substring(0, message.length() >= 2000 ? 1900 : message.length()) +"` via logchannel at "+event.getMessage().getTimeCreated()).queue();
			passCommand(message);
		}
		else if(message.startsWith("%") && notBot) {
			
			if(chName.equals(lobbyChannel)) {								// Lobby Commands
				// Join
				if(message.startsWith("join ", 1)) {
					String roleName = message.substring(6).trim().toLowerCase();	// Get the lobby to join
					List<Role> addList = event.getGuild().getRolesByName(roleName, false);
					
					if(!addList.isEmpty()) {
						/*if(false && hasPermission(cmdPerm, event)) {
								event.getGuild().getController().addRolesToMember(event.getMember(), addList).queue();			// Quick permission override to skip removal from other lobbies
								event.getMessage().addReaction("U+26CF").queue();			// Pick emoji for minecraft get it haha
						}
						else*/ 
						if(!isLocked(roleName)) {
							List<Role> remList = buildRemList(event.getGuild());
							//System.out.println(remList.size());
							remList.remove(addList.get(0));
							event.getGuild().modifyMemberRoles(event.getMember(), addList, remList).queue();	// Add requested lobby, remove other lobbies
							event.getMessage().addReaction("\u26CF").queue();			// Pick emoji for minecraft get it haha
							
							checkLast(remList);						// If last person in lobby leaves, unlock role(s)
						}
						else {
							event.getMessage().addReaction("\uD83D\uDD12").queue();			// Locked
						}
					}
				}
				// Leave
				else if(message.startsWith("leave ", 1)) {
					String roleName = message.substring(7).trim().toLowerCase();				// Leaves the specified lobby role
					List<Role> remList = event.getGuild().getRolesByName(roleName, false);
					
					if(!remList.isEmpty()) {
						event.getGuild().removeRoleFromMember(event.getMember(), remList.get(0)).queue();
						event.getMessage().addReaction("\u26CF").queue();
						
						checkLast(remList);							// If last person in lobby leaves, unlock role
					}
				}
				// Lock
				else if(message.startsWith("lock ", 1)) {
					String roleName = message.substring(6).trim().toLowerCase();
					List<Role> rList = event.getGuild().getRolesByName(roleName, false);
					
					if(!rList.isEmpty() && event.getMember().getRoles().contains(rList.get(0))) {
						setLock(roleName, true);
						event.getMessage().addReaction("\uD83D\uDD12").queue();							// Locked!!
					}
						
					
				}
				// Unlock
				else if(message.startsWith("unlock ", 1)) {
					String roleName = message.substring(8).trim().toLowerCase();
					List<Role> rList = event.getGuild().getRolesByName(roleName, false);
					
					if(!rList.isEmpty() && event.getMember().getRoles().contains(rList.get(0))) {
						setLock(roleName, false);
						event.getMessage().addReaction("\uD83D\uDD13").queue();							// Unlocked
					}
				}
				//Status
				else if(message.startsWith("status", 1)) {							// Output lobby role name, lobby lock status, and lobby members
					Guild efficiency = event.getGuild();
					String m1 = "", m2 = "", m3 = "";
					
					Iterator<Member> i = efficiency.getMembersWithRoles(efficiency.getRolesByName(lobby1name, false)).iterator();
					while(i.hasNext()) {
						m1 = m1.concat(i.next().getEffectiveName()) + ", ";
					}
					//m1 = m1.concat("\b\b");
					
					i = efficiency.getMembersWithRoles(efficiency.getRolesByName(lobby2name, false)).iterator();
					while(i.hasNext()) {
						m2 = m2.concat(i.next().getEffectiveName()) + ", ";
					}
					//m2 = m2.concat("\b\b");
					
					i = efficiency.getMembersWithRoles(efficiency.getRolesByName(lobby3name, false)).iterator();
					while(i.hasNext()) {
						m3 = m3.concat(i.next().getEffectiveName()) + ", ";
					}
					//m3 = m3.concat("\b\b");
					try {
						msgC.sendMessage("**"+lobby1name + "**| Locked: " + isLocked(lobby1name) 
								+ "\nMembers: " + m1
								+ "\n\n**"+lobby2name+"**| Locked: "+ isLocked(lobby2name)
								+ "\nMembers: " + m2
								+ "\n\n**"+lobby3name+"**| Locked: "+ isLocked(lobby3name)
								+ "\nMembers: "+ m3
								).queue();
					}
					catch(IllegalArgumentException e) {				// If the nicknames and number of people cause the message to be over 2000 characters (shouldn't happen too often, so this isnt very optimized)
						msgC.sendMessage("**"+lobby1name + "**| Locked: " + isLocked(lobby1name) 
						+ "\nMembers: " + efficiency.getMembersWithRoles(efficiency.getRolesByName(lobby1name, false)).size()
						+ "\n\n**"+lobby2name+"**| Locked: "+ isLocked(lobby2name)
						+ "\nMembers: " + efficiency.getMembersWithRoles(efficiency.getRolesByName(lobby2name, false)).size()
						+ "\n\n**"+lobby3name+"**| Locked: "+ isLocked(lobby3name)
						+ "\nMembers: "+ efficiency.getMembersWithRoles(efficiency.getRolesByName(lobby3name, false)).size()
						).queue();
					}
				}
				//Help
				else if(message.startsWith("help", 1)) {
					msgC.sendMessage("Check "+ event.getGuild().getTextChannelsByName("lobby-info", false).get(0).getAsMention() +" for command info.").queue();
					
				}
			}
			
			if(chName.equals(cmdChannel)) {											// Server Commands
				// Start
				if(message.startsWith("start", 1)) {
					msgC.sendTyping().queue();
					event.getGuild().getTextChannelsByName(logChan, false).get(0).sendMessage(event.getMember().getEffectiveName()+" ("+event.getMember().getAsMention()+") requested a server start at "+event.getMessage().getTimeCreated()).queue();
					
					if(!hasPermission(startPerm, event)) {
						msgC.sendMessage("No permission: Need "+startPerm+" role.").queue();
						return;
					}
					if(backupLock) {
						msgC.sendMessage("Cannot start server, backup in progress.").queue();
						return;
					}
					
					/*try {
						wait();
					} catch (InterruptedException e) {		//Simulated typing
						e.printStackTrace();
					}*/
					
					msgC.sendMessage("Attempting to start server...").queue();
					if(startServer(event.getGuild().getTextChannelsByName(logchannel, false).get(0), msgC)) {		// start server and pipe output to log channel
						event.getJDA().getPresence().setStatus(OnlineStatus.ONLINE);
						event.getJDA().getPresence().setActivity(Activity.playing("Minecraft"));
						msgC.sendMessage("Server starting!").queue();
					}
					else
						msgC.sendMessage("Launching sequence failed.").queue();
				}
				// Stop
				else if(message.startsWith("stop", 1)) {
					msgC.sendTyping().queue();
					event.getGuild().getTextChannelsByName(logChan, false).get(0).sendMessage(event.getMember().getEffectiveName()+" (" + event.getMember().getAsMention()+") requested a server stop at "+event.getMessage().getTimeCreated()).queue();
					
					if(event.getMember().getRoles().contains(event.getGuild().getRoleById(fakeStart))) {	// Fakes the Stop command
						msgC.sendMessage("Attempting to stop the server...").queue();
						if(serverP == null)
							//msgC.sendMessage("Stopping sequence failed.").queue();
							msgC.sendMessage("Server is already stopped!").queue();
						else
							msgC.sendMessage("Server stopped.").queue();
						return;
					}
					
					if(!hasPermission(startPerm, event)) {
						msgC.sendMessage("No permission: Need "+startPerm+" role.").queue();
						return;
					}
					
					// While stopServer() already checks for this, a little redundancy can go a long way if I change up the logic
					if(serverP == null || !serverP.isAlive()) {
						msgC.sendMessage("Server is already stopped!").queue();
						return;
					}
					
					/*try {
						this.wait(1200);
					} catch (InterruptedException e) {		//Simulated typing
						e.printStackTrace();
					}*/
					
					if(message.startsWith("b", 5) || message.startsWith(" backup", 5)) {		// User queuing up a backup after server stop
						dsp.queueBackup();
						event.getGuild().getTextChannelsByName(logChan, false).get(0).sendMessage(event.getMember().getEffectiveName()+" additionally requested to backup the world").queue();
					}
					
					msgC.sendMessage("Attempting to stop the server...").queue();		// Soft stop of server first
					if(message.startsWith("!", 5)) {								// Hard stop requested
							msgC.sendTyping().queue();
							/*try {
								this.wait(1900);
							} catch (InterruptedException e) {		//Simulated typing
								e.printStackTrace();
							}*/
							msgC.sendMessage("Now force quitting the server.").queue();
							System.out.println("Force quit success: "+ stopServer(1));
							event.getJDA().getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
							event.getJDA().getPresence().setActivity(null);
					}
					else if(stopServer(0)) {
						//event.getJDA().getPresence().setStatus(OnlineStatus.IDLE);
						//msgC.sendMessage("Server stopped.").queue();
						// No longer send message here, instead send message in the thread itself to accurately display when the thread ends
					}
					else {
						msgC.sendMessage("Stopping sequence failed.").queue();
						// server hard stop used to be here
					}
				}
				// Status
				else if(message.startsWith("status", 1)) {		// Simple check for server life
					Presence pres = event.getJDA().getPresence();
					if(serverP != null && serverP.isAlive()) {
						pres.setStatus(OnlineStatus.ONLINE);
						pres.setActivity(Activity.playing("Minecraft"));
						msgC.sendMessage("Server is online!\nPing: "+event.getJDA().getGatewayPing()+"\nRevive enabled: "+canRevive).queue();
					}
					else if(backupLock) {
						pres.setStatus(OnlineStatus.DO_NOT_DISTURB);
						msgC.sendMessage("Server is backing up...").queue();
					}
					else {
						pres.setStatus(OnlineStatus.IDLE);
						pres.setActivity(null);
						msgC.sendMessage("Server is offline.").queue();
					}
				}
				// CMD
				else if(message.startsWith("cmd ", 1)) {
					event.getGuild().getTextChannelsByName(logChan, false).get(0).sendMessage(event.getMember().getEffectiveName()+" ("+event.getMember().getAsMention()+") passed command `"+ message.replace('`', '\'').substring(5, message.length() >= 2000 ? 1900 : message.length()) +"` at "+event.getMessage().getTimeCreated()).queue();
					
					if(!hasPermission(cmdPerm, event)) {
						msgC.sendMessage("No permission: Need "+cmdPerm+" role.").queue();
						return;
					}
					
					if(passCommand(message.substring(5)))
						msgC.sendMessage("Command pass success!").queue();
					else
						msgC.sendMessage("Command pass failure.").queue();
				}
				// Revive
				else if(message.startsWith("revive ", 1) && canRevive) {
					if(!hasPermission(startPerm, event)) {
						msgC.sendMessage("No permission: Need "+startPerm+" role.").queue();
						return;
					}
					
					verifyName = message.substring(8).trim();					// Start y/n process for User
					verifyCheck = event.getAuthor();
					msgC.sendMessage("Please ensure "+ verifyName + " is on the server and currently in spectator mode. The effects of this command will be permanently recorded. Revive user " + verifyName + "? (y/n)" ).queue();
					verifyQuery = new Timer();									// Please don't cause a memory leak. its Java that should be hard to do right?
					verifyQuery.schedule(new TimerHelper(this, msgC), 10000);	
					//msgC.sendTyping().queue();									// Fake typing
				}
				// Backup
				else if(message.startsWith("backup", 1)) {
					msgC.sendTyping().queue();
					event.getGuild().getTextChannelsByName(logChan, false).get(0).sendMessage(event.getMember().getEffectiveName()+" ("+event.getMember().getAsMention()+") requested to backup the world at "+event.getMessage().getTimeCreated()).queue();
					
					// Check for start permission
					if(!hasPermission(startPerm, event)) {
						msgC.sendMessage("No permission: Need "+startPerm+" role.").queue();
						return;
					}
					// Check if a backup is already in progress
					if(backupLock) {
						msgC.sendMessage("Server backup already in progress!").queue();
						return;
					}
					// Ensure the server is not online
					if(serverP != null && serverP.isAlive()) {
						msgC.sendMessage("Server is still running! Shutdown server to enable backup.").queue();
						return;
					}
					
					// Checks have been passed, lock the server to prepare for backup
					backupLock = true;
					
					// Backup Process
					Presence pres = event.getJDA().getPresence();
					msgC.sendMessage("Backing up the world...").queue();
					pres.setActivity(Activity.listening("dank compression algorithm beats"));
					pres.setStatus(OnlineStatus.DO_NOT_DISTURB);
					Date date = new Date();
					SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd-HH-mm-ss");
					int errorCode = new ZipDirectory().ZipIt(mcDir+"//world", backupDir+"//Backup "+sdf.format(date)+".zip");
					if(errorCode > 0) 
						msgC.sendMessage("Backup Success!").queue();
					else
						msgC.sendMessage("Backup Failed! (Error code: "+errorCode+".").queue();
					
					// Unlock backup
					backupLock = false;
					pres.setStatus(OnlineStatus.IDLE);
					pres.setActivity(null);
					
				}
				//Help
				else if(message.startsWith("help", 1)) {
					msgC.sendMessage("Check " + event.getGuild().getTextChannelsByName("info", false).get(0).getAsMention() + " for command info and server details.").queue();
				}
			}
		}
		else if(verifyCheck != null && notBot) {	// Check for y/n, make sure its not self/bot
			if(message.trim().toLowerCase().equals("y") && event.getAuthor().equals(verifyCheck)) {	// If 'y' and user is original revive requester 
				verifyQuery.cancel(); 									// Theoretically this shouldn't ever be null at this point so I don't need error checking
				if(passCommand("gamemode survival " + verifyName)) {	// Ok i don't really like all these nested if statements but idk i dont really have a choice here
					event.getGuild().getTextChannelsByName(revChannel, true).get(0).sendMessage(verifyCheck.getName()+"#"+verifyCheck.getDiscriminator()+
							" ("+verifyCheck.getAsMention()+") revived player "+verifyName).queue();
				}
				else {
					event.getChannel().sendMessage("Revive command pass failed. (Is the server started?)").queue();			// If revive command cant be passed (ex: if bot is down)
				}
				verifyName = null;
				verifyCheck = null;			// Reset y/n query
			}
			else if(message.trim().toLowerCase().equals("n") && event.getAuthor().equals(verifyCheck)) {	// n means cancel the request
				verifyQuery.cancel();
				event.getChannel().sendMessage("Revive cancelled.").queue();
				verifyName = null;
				verifyCheck = null;			// Reset y/n query
			}
		}
	}
	
	@Override
	public void onGuildVoiceMove(GuildVoiceMoveEvent e) {
		//System.out.print("move " + e.getMember().getOnlineStatus() + " " + e.getMember().getActiveClients());
		Guild gld = e.getGuild();
		if(e.getChannelJoined() == gld.getAfkChannel() && e.getMember().getOnlineStatus() == OnlineStatus.IDLE) {
			try {
				e.getGuild().moveVoiceMember(e.getMember(), gld.getVoiceChannelById(secretVcId)).queue();
			}
			catch(IllegalStateException err) {
				System.err.println("This broke somehow?");
			}
			
			gld.getAudioManager().openAudioConnection(gld.getVoiceChannelById(secretVcId));
			User u = e.getMember().getUser();
			long cTime = System.currentTimeMillis();
			// Only message if user hasn't been messaged before or 30 minutes in milliseconds have passed since the last time they were
			if(!userDict.containsKey(u) || userDict.get(u).longValue() + 7200000 < cTime) {	
				try {
					u.openPrivateChannel().flatMap(channel -> channel.sendMessage("I took you to my special place :)")).queue();
				}
				catch(InsufficientPermissionException ipe) {
					System.err.println("Error: couldn't send dm. "+ipe.getLocalizedMessage());
				}
				userDict.put(u, cTime);
			}
            /*.delay(30, TimeUnit.SECONDS) // RestAction with delayed response
            .flatMap(Message::delete);*/
		}
		else if(e.getChannelLeft() == gld.getVoiceChannelById(secretVcId)) {		// Disconnect when channel is empty
			if(gld.getVoiceChannelById(secretVcId).getMembers().size() <= 1) {
				gld.getAudioManager().closeAudioConnection();
			}
		}
	}
	
	@Override
	public void onGuildVoiceLeave(GuildVoiceLeaveEvent e) {
		VoiceChannel chan = e.getGuild().getVoiceChannelById(secretVcId);		// Disconnect when channel is empty
		if(e.getChannelLeft() == chan && chan.getMembers().size() == 1)
			e.getGuild().getAudioManager().closeAudioConnection();
	}
	


	public Boolean startServer(MessageChannel ch, MessageChannel outpCh) {			// Starts server, then pipes server console text to messagechannel 
		if(serverP != null && serverP.isAlive())
			return false;
		// Listed here are various process builder lines that I have used for various servers. Uncomment to include them
		ProcessBuilder pb = new ProcessBuilder(
		// 		System.getProperty("java.home")+File.separator+"bin"+File.separator+"java","-Xms1024M", "-Xmx4096M", "-jar", "server.jar", "nogui"							// MC starting commands
		// 		System.getProperty("java.home")+File.separator+"bin"+File.separator+"java","-Xms1024M", "-Xmx4096M", "-jar", "forge-1.12.2-14.23.5.2854.jar", "nogui"		// Modded Forge MC starting commands
		// 		System.getProperty("java.home")+File.separator+"bin"+File.separator+"java","-Xms1024M", "-Xmx4096M", "-jar", "fabric-server-launch.jar", "nogui"			// Modded Fabric MC starting command
		 		System.getProperty("java.home")+File.separator+"bin"+File.separator+"java","-Xms256M", "-Xmx6G", "-jar", "fabric-server-mc.1.19.1-loader.0.14.8-launcher.0.11.0.jar", "nogui"			// Unmodded Fabric MC starting command
		//		System.getProperty("java.home")+File.separator+"bin"+File.separator+"java", "-Xmx4096M", "-Xms256M", "-Dsun.rmi.dgc.server.gcInterval=2147483646", "-XX:+UnlockExperimentalVMOptions", "-XX:G1NewSizePercent=20", "-XX:G1ReservePercent=20", "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M", "-jar", "forge-1.16.5-36.2.2.jar", "nogui"	// Heavy Duty Modded Forge MC starting commands
		);
		File dir = new File(mcDir);
		pb.redirectErrorStream(true);
		pb.directory(dir);
		try {
			serverP = pb.start();
			new Thread(dsp = new DiscordSyncPipe(serverP.getInputStream(), ch, expectingHeavy, howHeavy, outpCh, this)).start();		// Starts input thread
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		/*try {
			serverP.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
			//return(false);
		}*/
		
		return true;
	}
	
	public Boolean stopServer(int type) {					// Stops server nicely (0) or less nicely (1)
		if(serverP == null || !serverP.isAlive()) {
			System.err.println("Server is already stopped?");
			return false;
		}
		
		if(type == 0)	{	//Soft stop
			PrintWriter stdin = new PrintWriter(serverP.getOutputStream());
			stdin.println("stop");
			stdin.flush();
		
			return true;
		}
		
		if(type == 1) {		// Less soft stop
			try {
				return serverP.destroyForcibly().waitFor(500, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
				return false;
			}
		}
		return false;
	}
	
	public Boolean passCommand(String cmd) {		// Pass command into server
		if(serverP == null || !serverP.isAlive() || cmd.isEmpty())
			return false;
		PrintWriter stdin = new PrintWriter(serverP.getOutputStream());
		stdin.println(cmd);
		stdin.flush();
		return true;
	}
	
	public int backupWorld(MessageChannel msgC, Presence status) {					// Backup current world
		if(backupLock == true)
			return -1;
		
		// Checks have been passed, lock the server to prepare for backup
		backupLock = true;
		
		// Backup Process
		msgC.sendMessage("Backing up the world...").queue();
		status.setActivity(Activity.listening("dank compression algorithm beats"));
		status.setStatus(OnlineStatus.DO_NOT_DISTURB);
		Date date = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd-HH-mm-ss");
		int errorCode = new ZipDirectory().ZipIt(mcDir+"//world", backupDir+"//Backup "+sdf.format(date)+".zip");
		
		// Unlock backup
		backupLock = false;
		status.setStatus(OnlineStatus.IDLE);
		status.setActivity(null);
		
		return errorCode;
	}
	
	public Boolean hasPermission(String perm, MessageReceivedEvent e) {		// Returns true if user has role 
		try {
			List<Role> rlist = e.getGuild().getRolesByName(perm, false);
			List<Role> mlist = e.getMember().getRoles();
			boolean hasperm = false;
			for(int c = 0; c < rlist.size(); c++)
				hasperm = hasperm || mlist.contains(rlist.get(c));
			return hasperm;
			//return e.getMember().getRoles().contains(e.getGuild().getRolesByName(perm, false).get(0));
		}
		catch(NullPointerException n) {
			return false;
		}
	}
	
	private void setLock(String roleLock, boolean state) {				// Lock/Unlock role 
		switch(roleLock) {
		case lobby1name:
			lobby1lock = state;
			break;
		case lobby2name:
			lobby2lock = state;
			break;
		case lobby3name:
			lobby3lock = state;
		}
	}
	
	private boolean isLocked(String roleLock) {
		switch(roleLock) {
		case lobby1name:
			return lobby1lock;
		case lobby2name:
			return lobby2lock;
		case lobby3name:
			return lobby3lock;
		}
		return false;
	}
	
	private void checkLast(List<Role> cList) {		// If role has no members, unlock it
		if(cList.isEmpty())
			return;
		
		Guild g = cList.get(0).getGuild();							// Kinda bad code, assumes roles in list will always be from the same guild
		for(int i=0; i < cList.size(); i++) {						// Iterate through the list because I still cant fully get forEach to work
			if(g.getMembersWithRoles(cList.get(i)).size() <= 1) {		// Check if there are no members with Role - or if there is 1 member in the role (who is about to leave)
				setLock(cList.get(i).getName(), false);				// Unlock lobby lock
			}
		}
	}

	private List<Role> buildRemList(Guild g) {			// Builds up a list of all lobbyname roles
		List<Role> combList = g.getRolesByName(lobby1name, false);
		combList.addAll(g.getRolesByName(lobby2name, false));
		combList.addAll(g.getRolesByName(lobby3name, false));
		return combList;
	}
	
	/*
	private boolean isLobbyName(String name) {			// Just did this to shorten an if statement
		return name.equals(lobby1name) || name.equals(lobby2name) || name.equals(lobby3name);
	}*/
	
	private void resetLobbyRoles(Guild gld) {		// Remove the lobby roles from all members
		List<Role> roleList = buildRemList(gld);
		gld.getMembers().forEach((member) -> gld.modifyMemberRoles(member, null, roleList).queue());
		//System.out.println("Removed roles");
	}
	
    public static void main( String[] args ) throws LoginException, InterruptedException
    {
    	
    	String token = "";		// Doing the smart thing and reading the token from a file
    	try {
			Scanner sc = new Scanner(new File("token.txt"));
			token = sc.useDelimiter("\\Z").next().strip();
			sc.close();
		} catch (FileNotFoundException e) {
			System.out.println("Error reading token from file token.txt");
			return;
		} finally {
    		if(token == "") {
    			System.out.println("Error: token read as empty");
    			return;
    		}
    	}
    	
    	App bot = new App();
    	JDA jda = JDABuilder.createDefault(token)
    			.addEventListeners(bot)
    			.enableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MEMBERS)
    			.enableCache(CacheFlag.CLIENT_STATUS)
    			.setMemberCachePolicy(MemberCachePolicy.ALL)
    			.setStatus(OnlineStatus.IDLE)
    			.setActivity(null)
    			.build();
    	jda.awaitReady();
        System.out.println("MC Bot Ready");
        bot.resetLobbyRoles(jda.getGuilds().get(0));	// Remove lobby roles on bot startup
    }
}

//Copied from: https://stackoverflow.com/questions/4157303/how-to-execute-cmd-commands-via-java
/*class SyncPipe implements Runnable
{
	public SyncPipe(InputStream istrm, OutputStream ostrm) {
	   istrm_ = istrm;
	   ostrm_ = ostrm;
	}
	public void run() {
	   try
	   {
	       final byte[] buffer = new byte[1024];
	       for (int length = 0; (length = istrm_.read(buffer)) != -1; )
	       {
	           ostrm_.write(buffer, 0, length);
	       }
	   }
	   catch (Exception e)
	   {
	       e.printStackTrace();
	   }
	}
	private final OutputStream ostrm_;
	private final InputStream istrm_;
}*/


class DiscordSyncPipe implements Runnable
{
	private final InputStream istrm_;
	private final MessageChannel chan_;
	private final Boolean heavyLoad;
	private final int heavyStart;
	private final MessageChannel cmdChan_;
	private final App bot_;		// Only has this so it can stop itself
	private final Pattern ipRegEx;	// Use for checking and removing IP addresses from the output
	
	private Boolean doBackup;	// Whether we do a backup when server stops 
	
	public DiscordSyncPipe(InputStream istrm,  MessageChannel chan, Boolean isHeavy, int starts, MessageChannel cmdChan, App bot) {
		istrm_ = istrm;
		chan_ = chan;
		heavyLoad = isHeavy;			// Whether there will be a heavy input load 
		heavyStart = starts;			// Number of lines to run as a heavy load before transitioning to a regular load, -1 for infinite (i think)
		cmdChan_ = cmdChan;				// Output a final message when stream closes
		bot_ = bot;						// Here to pass through the stop command. Pretty wacky, aint it?
		doBackup = false;
		
		ipRegEx = Pattern.compile("\\[\\/\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+\\]");	// Hardcoding IP regex from server format
	}
	
	public void queueBackup() {
		doBackup = true;
	}
	
	public void run() {
		try 
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(istrm_));		// Turns out buffered reader works fine and I don't need to use a byte array
			if(heavyLoad) {				// Expecting there to be a large number of input lines
				int maxedLines = heavyStart;
				String buff = "";
				for(String in = " "; (in = reader.readLine()) != null;)				// Continues to read until there is no more possible input
				{
					System.out.println(in);
					if(in.startsWith("[")) {	// Filters out lines that do not contain a timestamp (often error tracebacks, etc)
						//[10:27:20 INFO]: [Server] stopnow:1234
						if(in.startsWith("[Server] stopnow:1234", 33)) {
							// Message from server that means it needs to shutdown
							System.out.println("No activity threshold reached, stopping server automatically");
							cmdChan_.sendMessage("Server has been online for without players for 1 hour. Automatically stopping...").queue();
							bot_.stopServer(0);
						}
						else {
							in = ipRegEx.matcher(in).replaceAll("[/**.**.**.***:****]");	// Strip away IP addresses from the log output (no more doxxing!!!)
							buff = buff.concat(in);
						/*
						try {
							chan_.sendMessage(in).queue();
						}
						catch(IllegalArgumentException e) {
							chan_.sendMessage(in.substring(0, 1996) + "-").queue();				// Shortened version w/ '-' to show it being cut off
						}
						 */
							if(maxedLines != 0) {
								if(buff.length() >= 2000) {
									chan_.sendMessage(buff.substring(0, 2000).trim()).queue();
									buff = buff.substring(2000).concat("\n");
									if(maxedLines > 0)  
										maxedLines--;
								}
								else {
									buff = buff.concat("\n");
								}
							}
							else {
								if(buff.length() >= 2000) {
									chan_.sendMessage(buff.substring(0, 2000).trim()).queue();
									buff = buff.substring(2000).concat("\n");
								}
								else {
									chan_.sendMessage(buff.trim()).queue();
									buff = "";
								}
							}
						}
					}
				}
				
				if(buff.length() > 0)
					chan_.sendMessage(buff).queue();
			}
			else {			// No large input lines, can handle normally
				for(String in = " "; (in = reader.readLine()) != null;)				// Continues to read until there is no more possible input
				{
					System.out.println(in);
					//[20:12:38] [Server thread/INFO]: [Server] stopnow:1234
					if(in.startsWith("[") && in.startsWith("[Server] stopnow:1234", 33)) {
						// Message from server that means it needs to shutdown
						System.out.println("No activity threshold reached, stopping server automatically");
						cmdChan_.sendMessage("Server has been online for without players for 1 hour. Automatically stopping...").queue();
						bot_.stopServer(0);
					}
					else {
						in = ipRegEx.matcher(in).replaceAll("[/\\*\\*.\\*\\*.\\*\\*.\\*\\**:\\***\\*]");	// Strip away IP addresses from the log output (no more doxxing!!!)
						
						try {
							chan_.sendMessage(in).queue();
						}
						catch(IllegalArgumentException e) {
							chan_.sendMessage(in.substring(0, 1996) + "-").queue();				// Shortened version w/ '-' to show it being cut off
						}
					}
				}
			}
			
			cmdChan_.sendMessage("Server stopped.").queue();					// Sends a message to indicate server is stopped
			chan_.getJDA().getPresence().setStatus(OnlineStatus.IDLE);			// Sets presence to idle once the server is stopped
			chan_.getJDA().getPresence().setActivity(null);
			
			if(doBackup) {		// Here's where we do the queued up backup
				int errCode = bot_.backupWorld(cmdChan_, chan_.getJDA().getPresence());
				System.out.println("Backup result: " + errCode);
				if(errCode > 0) 
					cmdChan_.sendMessage("Backup Success!").queue();
				else
					cmdChan_.sendMessage("Backup Failed! (Error code: "+errCode+".").queue();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}

class TimerHelper extends TimerTask 	// I hope I'm doing this right
{
	App botPointer;				// I have no idea if this is efficient, if I need to use a timer, or if this even works but hey whats the worst that can happen 
	MessageChannel timeout;				// I'm deleting this comment later
	
	public TimerHelper(App a, MessageChannel t) {
		botPointer = a;
		timeout = t;
	}
	
	public void run() {
		botPointer.verifyCheck = null;		// Resets the parameters
		botPointer.verifyName = null;
		
		timeout.sendMessage("Query timeout! It takes a lot of resources to do this so make a decision next time, ok?").queue();       // This is a lie I'm going through the process anyways
	}
	
}

	
