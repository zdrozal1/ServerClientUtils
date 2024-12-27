package net.guess;

import net.guess.Other.FileWatcher;

import java.io.IOException;
import java.nio.file.StandardWatchEventKinds;
import java.util.Scanner;

public class Main {
	static class ServerMain {
		public static void main(String[] args) {
			Server server = new Server();
			FileWatcher testFileWatcher = new FileWatcher("test.txt", 2000, path -> {
				System.out.println("test.txt has changed, sending file. Path: " + path);
				server.sendMessage("FILE_UPDATE");
				try {
					server.sendFileToClient("test.txt");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}, StandardWatchEventKinds.ENTRY_CREATE);
			server.addFileWatcher(testFileWatcher);
			server.startBroadcasting(8888);
			server.startServerAsync(8888);
			
			Scanner scanner = new Scanner(System.in);
			while (true) {
				String line = scanner.nextLine();
				server.sendMessage(line);
			}
		}
	}
	
	static class ClientMain {
		public static void main(String[] args) {
			Client client = new Client();
			client.registerMessageHandler("TEST_COMMAND", msg -> {
				System.out.println("Client received: " + msg);
			});
			client.registerMessageHandler("FILE_UPDATE", msg -> {
				System.out.println("Client received: " + msg);
				client.receiveFileFromServer(1024, "Client_Received.txt");
			});
			new Thread(client::listenForServerBroadcasts).start();
		}
	}
}
