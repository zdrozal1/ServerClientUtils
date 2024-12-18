package net.guess;

public class Main {
	static class ServerMain {
		public static void main(String[] args) {
			System.out.println("SERVER OUTPUT:");
			Server server = new Server("localhost", 8080);
			server.setOnConnect(() -> System.out.println("Successfully connected!"));
			server.setOnDisconnect(() -> System.out.println("Disconnected from server."));
			server.setMaxClients(1);
			server.setDebugEnabled(true);
			server.startServerAsync(90000);
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
			client.addResponseHandler("STOP", _ -> client.disconnect());
			client.connectAsync(90000, 90000);
		}
	}
}