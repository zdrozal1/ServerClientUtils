package net.guess;

import net.guess.Other.FileWatcher;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server {
	private boolean isConnected = false;
	private ServerSocket serverSocket;
	private Socket clientSocket;
	private PrintWriter writer;
	private ScheduledExecutorService broadcastExecutorService;
	private ScheduledExecutorService heartBeatExecutor;
	private final List<FileWatcher> fileWatchers = new ArrayList<>();
	
	public synchronized void sendFileToClient(String fileToSend) throws IOException {
		try (Socket sock = serverSocket.accept(); FileInputStream fis = new FileInputStream(fileToSend); BufferedInputStream bis = new BufferedInputStream(fis); OutputStream os = sock.getOutputStream()) {
			
			byte[] mybytearray = new byte[(int) new File(fileToSend).length()];
			bis.read(mybytearray, 0, mybytearray.length);
			System.out.println("Sending " + fileToSend + "(" + mybytearray.length + " bytes)");
			os.write(mybytearray, 0, mybytearray.length);
			os.flush();
			System.out.println("Sent");
		} catch (IOException e) {
			System.err.println("Error sending file to client: " + e.getMessage());
			throw e;
		}
	}
	
	public void addFileWatcher(FileWatcher fileWatcher) {
		fileWatchers.add(fileWatcher);
	}
	
	public void startServerAsync(int port) {
		new Thread(() -> {
			try {
				startServer(port);
			} catch (Exception e) {
				System.err.println("Error starting server asynchronously: " + e.getMessage());
			}
		}).start();
	}
	
	public void startServer(int port) {
		try {
			System.out.println("Initializing server on port " + port);
			serverSocket = new ServerSocket(port);
			System.out.println("Server started successfully on port " + port);
			
			clientSocket = serverSocket.accept();
			System.out.println("Client connected from " + clientSocket.getInetAddress().getHostAddress());
			handleClient();
		} catch (BindException e) {
			System.err.println("Port " + port + " is already in use.");
		} catch (IOException e) {
			System.err.println("Error starting server: " + e.getMessage());
		} finally {
			stopServer();
			System.out.println("Server stopped.");
		}
	}
	
	public void startBroadcasting(int port) {
		broadcastExecutorService = Executors.newSingleThreadScheduledExecutor();
		broadcastExecutorService.scheduleAtFixedRate(() -> {
			if (!isConnected) {
				try (DatagramSocket socket = new DatagramSocket()) {
					socket.setBroadcast(true);
					String message = "SERVER_DISCOVERY:" + port;
					byte[] buffer = message.getBytes();
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName("255.255.255.255"), port);
					socket.send(packet);
					System.out.println("Broadcasted server on port: " + port);
				} catch (IOException e) {
					System.err.println("Error broadcasting server availability: " + e.getMessage());
				}
			}
		}, 0, 5, TimeUnit.SECONDS);
	}
	
	public void stopBroadcasting() {
		if (broadcastExecutorService != null && !broadcastExecutorService.isShutdown()) {
			broadcastExecutorService.shutdown();
			try {
				if (!broadcastExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
					System.err.println("Broadcast executor service did not terminate in time");
				}
			} catch (InterruptedException e) {
				System.err.println("Error stopping broadcast executor service: " + e.getMessage());
			}
			System.out.println("Broadcasting stopped");
		}
	}
	
	private void handleClient() {
		try {
			isConnected = true;
			BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			writer = new PrintWriter(clientSocket.getOutputStream(), true);
			
			// Start file watchers
			for (FileWatcher fileWatcher : fileWatchers) {
				fileWatcher.startWatching();
			}
			
			heartBeatExecutor = Executors.newScheduledThreadPool(1);
			heartBeatExecutor.scheduleAtFixedRate(() -> {
				try {
					if (!clientSocket.isClosed()) {
						writer.println("HEARTBEAT");
					} else {
						handleClientDisconnection(heartBeatExecutor);
					}
				} catch (Exception e) {
					System.err.println("Error sending heartbeat: " + e.getMessage());
				}
			}, 0, 5, TimeUnit.SECONDS);
			
			String inputLine;
			while ((inputLine = reader.readLine()) != null) {
				if ("HEARTBEAT".equals(inputLine)) {
					writer.println("HEARTBEAT");
				}
			}
		} catch (SocketException e) {
			System.err.println("Client disconnected");
			isConnected = false;
		} catch (IOException e) {
			System.err.println("Error with client connection: " + e.getMessage());
		} finally {
			disconnectClient();
		}
	}
	
	public void sendMessage(String message) {
		try {
			if (!clientSocket.isClosed()) {
				writer.println(message);
			} else {
				System.err.println("Client disconnected, can't send message: " + message);
			}
		} catch (Exception e) {
			System.err.println("Error sending " + message + ": " + e.getMessage());
		}
	}
	
	public void stopServer() {
		try {
			if (serverSocket != null && !serverSocket.isClosed()) {
				if (writer != null) {
					writer.println("SHUTDOWN");
				}
				serverSocket.close();
				disconnectClient();
				stopBroadcasting();
			}
		} catch (IOException e) {
			System.err.println("Error closing server socket: " + e.getMessage());
		}
	}
	
	private void disconnectClient() {
		if (clientSocket != null && !clientSocket.isClosed()) {
			try {
				clientSocket.close();
				System.out.println("Client socket closed");
			} catch (IOException e) {
				System.err.println("Error closing client socket: " + e.getMessage());
			}
		}
	}
	
	private void handleClientDisconnection(ScheduledExecutorService executor) {
		System.err.println("Client Heartbeat Lost");
		isConnected = false;
		executor.shutdown();
		System.out.println("Client Heartbeat check stopped");
	}
	
}
