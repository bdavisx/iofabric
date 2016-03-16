package com.iotracks.iofabric;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.text.ParseException;
import java.util.Set;

import com.iotracks.iofabric.command_line.CommandLineClient;
import com.iotracks.iofabric.command_line.CommandLineParser;
import com.iotracks.iofabric.command_line.CommandLineServer;
import com.iotracks.iofabric.supervisor.Supervisor;
import com.iotracks.iofabric.utils.Constants;
import com.iotracks.iofabric.utils.configuration.Configuration;
import com.iotracks.iofabric.utils.configuration.ConfigurationItemException;
import com.iotracks.iofabric.utils.logging.LoggingService;

public class Start {

	private static CommandLineClient client;
	private static Thread commandlineListener;

	private static boolean isAnotherInstanceRunning() {
		client = new CommandLineClient();
		return client.startClient();
	}

	private static void setupEnvironment() {
		final File daemonFilePath = new File("/var/run/iofabric");
		if (!daemonFilePath.exists()) {
			try {
				daemonFilePath.mkdirs();

				UserPrincipalLookupService lookupservice = FileSystems.getDefault().getUserPrincipalLookupService();
				final GroupPrincipal group = lookupservice.lookupPrincipalByGroupName("iofabric");
				Files.getFileAttributeView(daemonFilePath.toPath(), PosixFileAttributeView.class,
						LinkOption.NOFOLLOW_LINKS).setGroup(group);
				Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwx---");
				Files.setPosixFilePermissions(daemonFilePath.toPath(), perms);
			} catch (Exception e) {
			}
		}

	}
	
	public static void main(String[] args) throws ParseException {
		try {
			Configuration.loadConfig();
		} catch (ConfigurationItemException e) {
			System.out.println("invalid configuration item(s).");
			System.out.println(e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			System.out.println("error accessing /etc/iofabric/config.xml");
			System.exit(1);
		}

		setupEnvironment();

		if (isAnotherInstanceRunning()) {
			if (args.length > 0 && args[0].equals("start")) {
				System.out.println("iofabric is already running.");
				System.exit(1);
			}
			
			String command = "";
			for (String str : args)
				command += str + " ";

			if (command.trim().equals(""))
				command = "help";
			client.sendMessage(command.trim());
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}
			if (command.trim().startsWith("stop")) {
				System.out.println("Stopping iofabric service...");
				System.out.flush();
			}
			client.stopClient();
			System.out.println();
			System.exit(0);
		}

		if (args.length > 0) {
			if (args[0].equals("help") || args[0].equals("--help") || args[0].equals("-h") || args[0].equals("-?") || 
					args[0].equals("version") || args[0].equals("--version") || args[0].equals("-v")) {
				System.out.println(CommandLineParser.parse(args[0]));
				System.out.flush();
				System.exit(0);
			} else if (!args[0].equals("start")) {
				System.out.println("iofabric is not running.");
				System.out.flush();
				System.exit(1);
			}
		}

		try {
			LoggingService.setupLogger();
		} catch (IOException e) {
			System.out.println("Error starting logging service\n" + e.getMessage());
			System.exit(1);
		}
		LoggingService.logInfo("Main", "configuration loaded.");

		// port System.out to null
		Constants.systemOut = System.out;
		if (!Configuration.debugging) {
			System.setOut(new PrintStream(new OutputStream() {
				@Override
				public void write(int b) {
					// DO NOTHING
				}
			}));
	
			System.setErr(new PrintStream(new OutputStream() {
				@Override
				public void write(int b) {
					// DO NOTHING
				}
			}));
		}
		
		
		LoggingService.logInfo("Main", "starting command line listener");
		commandlineListener = new Thread(new CommandLineServer(), "Command Line Server");
		commandlineListener.start();

		LoggingService.logInfo("Main", "starting supervisor");
		Supervisor supervisor = new Supervisor();
		supervisor.start();

		// port System.out to standard
		System.setOut(Constants.systemOut);
	}

}