package net.guess;

import net.guess.Other.MessageHandler;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class Client {
	private boolean isConnected = false;
	private Socket socket;
	private PrintWriter writer;
	private final Map<String, MessageHandler> messageHandlers = new HashMap<>();
	private String serverHost;
	private int serverPort;
	
	public Client() {
		// Register default message handlers
		registerMessageHandler("SHUTDOWN", this::handleShutdown);
		registerMessageHandler("UPDATE_MESSAGE", this::handleUpdateMessage);
	}
	
	public synchronized void receiveFileFromServer(int fileSize, String name) throws IOException {
		int bytesRead;
		FileOutputStream fos = null;
		BufferedOutputStream bos = null;
		Socket sock = null;
		try {
			sock = new Socket(serverHost, serverPort);
			byte[] mybytearray = new byte[fileSize];
			InputStream is = sock.getInputStream();
			fos = new FileOutputStream(name);
			bos = new BufferedOutputStream(fos);
			
			while ((bytesRead = is.read(mybytearray)) != -1) {
				bos.write(mybytearray, 0, bytesRead);
			}
			
			bos.flush();
		} finally {
			try {
				if (bos != null) {
					bos.close();
				}
				if (fos != null) {
					fos.close();
				}
				if (sock != null) {
					sock.close();
				}
			} catch (IOException e) {
				System.err.println("Could Not Close All Elements: " + e.getMessage());
			}
		}
	}
	
	public void connectToServer(String address, int port) throws IOException {
		if (socket != null && !socket.isClosed()) {
			System.out.println("Closing existing socket before reconnecting.");
			socket.close();
		}
		
		new Thread(() -> {
			try {
				System.out.println("Initializing socket.");
				socket = new Socket();
				System.out.println("Attempting to connect to " + address + ":" + port);
				socket.connect(new InetSocketAddress(address, port), 10000);
				System.out.println("Socket connected successfully.");
				
				socket.setSoTimeout(10000);
				System.out.println("Socket timeout set.");
				
				isConnected = true;
				serverHost = address;
				serverPort = port;
				
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				new Thread(() -> receiveMessages(in)).start();
				
				writer = new PrintWriter(socket.getOutputStream(), true);
				System.out.println("CONNECTED: " + address + ":" + port);
				
			} catch (IOException e) {
				isConnected = false;
				System.err.println("Failed to connect: " + e.getMessage());
			}
		}).start();
	}
	
	public void receiveMessages(BufferedReader in) {
		new Thread(() -> {
			try {
				String fromServer;
				while ((fromServer = in.readLine()) != null) {
					String command = fromServer.split(" ")[0];
					MessageHandler handler = messageHandlers.get(command);
					if (handler != null) {
						handler.handle(fromServer);
					} else {
						System.out.println("Received: " + fromServer);
					}
				}
			} catch (SocketTimeoutException e) {
				isConnected = false;
				System.err.println("Read timed out.");
			} catch (IOException e) {
				isConnected = false;
				System.err.println("Error reading from server: " + e.getMessage());
			}
		}).start();
	}
	
	public void disconnectFromServer() {
		try {
			isConnected = false;
			if (socket != null && !socket.isClosed()) {
				socket.close();
				System.out.println("Client socket closed.");
			}
		} catch (IOException e) {
			System.err.println("Error disconnecting from server: " + e.getMessage());
		}
	}
	
	public void listenForServerBroadcasts() {
		int broadCastPort = 8888;
		System.out.println("Using broadcastPort: " + broadCastPort);
		
		try (DatagramSocket socket = new DatagramSocket(broadCastPort, InetAddress.getByName("0.0.0.0"))) {
			socket.setBroadcast(true);
			while (true) {
				if (!isConnected) {
					byte[] buffer = new byte[256];
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					socket.receive(packet);
					
					String message = new String(packet.getData(), 0, packet.getLength());
					if (message.startsWith("SERVER_DISCOVERY:")) {
						String[] parts = message.split(":");
						String serverAddress = packet.getAddress().getHostAddress();
						int serverPort = Integer.parseInt(parts[1]);
						
						System.out.println("Discovered server at " + serverAddress + ":" + serverPort);
						try {
							connectToServer(serverAddress, serverPort);
						} catch (IOException e) {
							System.err.println("Error connecting to server; " + serverAddress + ":" + serverPort + " | " + e.getMessage());
						}
					}
				} else {
					System.out.println("Already connected");
					break;
				}
			}
		} catch (IOException e) {
			System.err.println("Error listening for broadcasts: " + e.getMessage());
		}
	}
	
	public void registerMessageHandler(String command, MessageHandler handler) {
		messageHandlers.put(command, handler);
	}
	
	private void handleShutdown(String message) {
		System.out.println("Received shutdown, Disconnecting...");
		disconnectFromServer();
	}
	
	private void handleUpdateMessage(String message) {
		System.out.println("Received update message: " + message);
		// Add code to handle the update
	}
}
