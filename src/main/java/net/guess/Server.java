package net.guess;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class Server {
	private final String address;
	private final int port;
	private final Map<String, Consumer<String>> messageHandlers = new ConcurrentHashMap<>();
	private final ExecutorService clientHandlers;
	private final CopyOnWriteArrayList<Socket> connectedClients = new CopyOnWriteArrayList<>();
	private final Map<Socket, String> clientIdentifiers = new ConcurrentHashMap<>();
	private final Map<Socket, PrintWriter> clientWriters = new ConcurrentHashMap<>();
	private ServerSocket serverSocket;
	private int maxClients = 1;
	private volatile boolean isRunning = false;
	private boolean debugEnabled = false;
	private boolean enableEvents = true;
	private Client.ConnectionEvent onConnect = () -> System.out.println("Connected!");
	private Client.ConnectionEvent onDisconnect = () -> System.out.println("Disconnected!");
	private Consumer<String> onClientMessageReceived = msg -> System.out.println("Message received: " + msg);
	private Consumer<String> onClientRegistered = client -> System.out.println("Client registered: " + client);
	private Consumer<String> onBroadcast = msg -> System.out.println("Broadcast sent: " + msg);
	private Consumer<String> onMessage = msg -> System.out.println("Message sent: " + msg);
	
	public Server(String address, int port) {
		this.address = address;
		this.port = port;
		clientHandlers = Executors.newFixedThreadPool(maxClients);
	}
	
	public static byte[] readFile(String fileName) throws IOException {
		File file = new File(fileName);
		byte[] fileData = new byte[(int) file.length()];
		
		try (FileInputStream fis = new FileInputStream(file)) {
			fis.read(fileData);
		}
		
		return fileData;
	}
	
	public void setEnableEvents(boolean enableEvents) {
		this.enableEvents = enableEvents;
	}
	
	public void setOnConnect(Client.ConnectionEvent onConnect) {
		this.onConnect = onConnect;
	}
	
	public void setOnDisconnect(Client.ConnectionEvent onDisconnect) {
		this.onDisconnect = onDisconnect;
	}
	
	public void setOnClientMessageReceived(Consumer<String> handler) {
		this.onClientMessageReceived = handler;
	}
	
	public void setOnClientRegistered(Consumer<String> handler) {
		this.onClientRegistered = handler;
	}
	
	public void setOnBroadcast(Consumer<String> handler) {
		this.onBroadcast = handler;
	}
	
	public void setOnMessage(Consumer<String> handler) {
		this.onMessage = handler;
	}
	
	public void addMessageHandler(String command, Consumer<String> handler) {
		if (!isRunning) {
			if (messageHandlers.putIfAbsent(command, handler) == null) {
				printDebug("Added server command: " + command);
			} else {
				printDebug("Server already contains command: " + command);
			}
		} else {
			printDebug("Cannot add command while server is running: " + command);
		}
	}
	
	public void startServer(int acceptTimeout) {
		addShutdownHook();
		printDebug("Starting server on " + address + ":" + port + " with accept timeout: " + acceptTimeout + "ms");
		try {
			serverSocket = new ServerSocket(port);
			serverSocket.setSoTimeout(acceptTimeout);
			isRunning = true;
			if (enableEvents) {
				onConnect.onEvent();
			}
			printDebug("Server started and listening for connections...");
			while (isRunning) {
				try {
					Socket clientSocket = serverSocket.accept();
					printDebug("Client connected: " + clientSocket.getInetAddress());
					clientHandlers.submit(() -> {
						connectedClients.add(clientSocket);
						handleClient(clientSocket);
					});
				} catch (SocketTimeoutException e) {
					printDebug("Accept timeout reached. Retrying...");
				}
			}
		} catch (IOException e) {
			printDebug("Error starting server: " + e.getMessage());
			stopServer();
		}
	}
	
	public void startServerAsync(int acceptTimeout) {
		new Thread(() -> startServer(acceptTimeout)).start();
	}
	
	private void handleClient(Socket clientSocket) {
		printDebug("Handling client: " + clientSocket.getInetAddress());
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(clientSocket.getInputStream())); PrintWriter writer = new PrintWriter(
				clientSocket.getOutputStream(), true)) {
			
			registerClient(clientSocket);
			
			String message;
			while ((message = reader.readLine()) != null) {
				printDebug("Received message from client: " + clientSocket.getInetAddress() + " - " + message);
				if (message.trim().isEmpty()) {
					continue;
				}
				onClientMessageReceived.accept(message);
				
				String[] parts = message.split(" ", 2);
				String command = parts[0];
				String arguments = parts.length > 1 ? parts[1] : "";
				
				Consumer<String> handler = messageHandlers.get(command);
				if (handler != null) {
					handler.accept(arguments);
				} else {
					printDebug("No handler found for command: " + command);
				}
			}
		} catch (IOException e) {
			if (e.getMessage().equals("Socket closed")) {
				printDebug("Client disconnected cleanly: " + clientSocket.getInetAddress());
			} else {
				printDebug("Error handling client (" + clientSocket.getInetAddress() + "): " + e.getMessage());
			}
		} finally {
			printDebug("Finalizing client handler: " + clientSocket.getInetAddress());
			connectedClients.remove(clientSocket);
			clientIdentifiers.remove(clientSocket);
			clientWriters.remove(clientSocket);
			try {
				clientSocket.close();
				printDebug("Socket closed for client: " + clientSocket.getInetAddress());
			} catch (IOException e) {
				printDebug("Error closing client socket: " + e.getMessage());
			}
		}
	}
	
	public void broadcastMessage(String message) {
		if (message != null) {
			printDebug("Broadcasting message: " + message);
			for (Socket client : connectedClients) {
				PrintWriter writer = clientWriters.get(client);
				if (writer != null) {
					writer.println(message);
					printDebug("Message: '" + message + "' sent to client: " + client.getInetAddress());
				} else {
					printDebug("Failed to send message to client (no writer): " + client.getInetAddress());
				}
			}
			onBroadcast.accept(message);
		}
	}
	
	public void sendMessageToClient(Socket client, String message) {
		if (message != null) {
			if (client.isConnected()) {
				printDebug("Sending message: '" + message + "' To client: " + client.getInetAddress().getHostName());
				PrintWriter writer = clientWriters.get(client);
				if (writer != null) {
					writer.println(message);
					printDebug("Message sent to client: " + client.getInetAddress());
				} else {
					printDebug("Failed to send message to client (no writer): " + client.getInetAddress());
				}
				onMessage.accept(message);
			} else {
				printDebug("Client is not connected to send message: " + client.getInetAddress().getHostName());
			}
		}
	}
	
	private void registerClient(Socket client) throws IOException {
		String clientName = client.getInetAddress().getHostName();
		printDebug("Attempting to register client: " + clientName);
		clientIdentifiers.put(client, clientName);
		clientWriters.put(client, new PrintWriter(client.getOutputStream(), true));
		onClientRegistered.accept(clientName);
		broadcastMessage(clientName + " has joined.");
		printDebug("Client successfully registered: " + clientName);
	}
	
	public void stopServer() {
		if (!isRunning) {
			printDebug("Server is already stopped.");
			return;
		}
		
		printDebug("Stopping server...");
		
		isRunning = false;
		clientHandlers.shutdownNow();
		if (enableEvents) {
			onDisconnect.onEvent();
		}
		
		try {
			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
				printDebug("Server stopped.");
			}
		} catch (IOException e) {
			printDebug("Error during server shutdown: " + e.getMessage());
		}
	}
	
	private void addShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer));
	}
	
	private void printDebug(String message) {
		if (debugEnabled) {
			System.out.println(message);
		}
	}
	
	public void setDebugEnabled(boolean debugEnabled) {
		this.debugEnabled = debugEnabled;
	}
	
	public void setMaxClients(int maxClients) {
		this.maxClients = maxClients;
	}
	
	public CopyOnWriteArrayList<Socket> getConnectedClients() {
		return connectedClients;
	}
	
	public void sendFileData(Socket clientSocket, String fileName, String fileType) {
		PrintWriter writer = clientWriters.get(clientSocket);
		byte[] fileData = null;
		try {
			fileData = Server.readFile("testDir/file.txt");
		} catch (IOException e) {
			printDebug("Error reading file: " + e.getMessage());
		}
		if (writer != null) {
			// Send the STARTFILE message with the filename and size
			writer.println("STARTFILE " + fileName + " " + fileData.length + " " + fileType);
			
			String dataPrefix = "FILEDATA";
			int dataSize = fileData.length;
			int chunkSize = 1024; // Define the chunk size for file transfer
			
			// Split the file data into chunks and send
			for (int i = 0; i < dataSize; i += chunkSize) {
				int end = Math.min(i + chunkSize, dataSize);
				byte[] chunk = new byte[end - i];
				System.arraycopy(fileData, i, chunk, 0, chunk.length);
				
				// Base64 encode the chunk and send it
				String encodedChunk = Base64.getEncoder().encodeToString(chunk);
				String dataMessage = dataPrefix + encodedChunk; // Prefix "FILEDATA" added here
				System.out.println("Sending chunk: " + dataMessage);  // Log chunk being sent
				writer.println(dataMessage); // Send the encoded chunk
			}
			
			// End of file transfer signal
			writer.println("ENDFILE");
		}
	}
}
