package net.guess;

import net.guess.Other.SimpleFileHandler;

import java.util.Timer;
import java.util.TimerTask;

public class Main {
	static class ServerMain {
		public static void main(String[] args) {
			System.out.println("SERVER OUTPUT:");
			Server server = new Server("localhost", 8080);
			server.setOnConnect(() -> {
				System.out.println("Successfully connected!");
			});
			server.setOnDisconnect(() -> System.out.println("Disconnected from server."));
			server.setMaxClients(1);
			server.setDebugEnabled(true);
			server.startServerAsync(90000);
			
			Timer timer = new Timer();
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					server.sendFileData(server.getConnectedClients().getFirst(), "testDir/file.txt", "standard");
				}
			}, 3 * 1000);
			
		}
	}
	
	static class ClientMain {
		public static void main(String[] args) {
			System.out.println("CLIENT OUTPUT:");
			Client client = new Client("localhost", 8080);
			client.setRetryDelay(1000);
			client.setMaxRetries(10);
			client.setOnConnect(() -> System.out.println("Successfully connected!"));
			client.setOnDisconnect(() -> System.out.println("Disconnected from server."));
			client.setDebugEnabled(true);
			
			client.setFileHandler(new SimpleFileHandler());
			
			client.connectAsync(90000, 90000);
		}
	}
}
