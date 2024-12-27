package net.guess;

import net.guess.ClientUtil.Client;
import net.guess.ServerUtil.FileWatcher;
import net.guess.ServerUtil.Server;

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
			}, StandardWatchEventKinds.ENTRY_MODIFY);
			server.addFileWatcher(testFileWatcher);
			
			server.startBroadcasting(8888);
			server.startServerAsync(8888);
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
				client.receiveFileFromServer("Client_Received.txt");
			});
			client.listenForServerBroadcasts(8888);
		}
	}
}
