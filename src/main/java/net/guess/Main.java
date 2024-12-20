package net.guess;

import net.guess.Other.CustomHandler;

public class Main {
	static class ServerMain {
		public static void main(String[] args) throws InterruptedException {
			System.out.println("SERVER OUTPUT:");
			Server server = new Server("localhost", 8080);
			server.setOnConnect(() -> {
				System.out.println("Successfully connected!");
			});
			server.setOnDisconnect(() -> System.out.println("Disconnected from server."));
			server.setMaxClients(1);
			server.setDebugEnabled(true);
			server.startServerAsync(90000);
			
			//Thread.sleep(1000);
			//server.sendFileToClient(server.getConnectedClients().getFirst(), "testDir/file.txt", "text");
			//server.sendFileToClient(server.getConnectedClients().getFirst(), "testDir/imgtext.txt", "image");
		}
	}
	
	static class ClientMain {
		public static void main(String[] args) {
			System.out.println("CLIENT OUTPUT:");
			Client client = new Client(8888);
			client.setRetryDelay(1000);
			client.setMaxRetries(10);
			client.setOnConnect(() -> System.out.println("Successfully connected!"));
			client.setOnDisconnect(() -> System.out.println("Disconnected from server."));
			client.setDebugEnabled(true);
			
			client.setFileHandler(new CustomHandler());
			
			client.connectAsync(90000, 90000);
		}
	}
}