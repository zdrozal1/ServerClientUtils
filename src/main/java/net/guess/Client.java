package net.guess;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Client {
	private final Map<String, MessageHandler> responseHandlers = new HashMap<>();
	private boolean debugEnabled = false;
	private String serverAddress;
	private int serverPort;
	private Socket socket;
	private BufferedReader reader;
	private PrintWriter writer;
	private Thread listenerThread;
	private boolean isConnected = false;
	private int maxRetries = 3;
	private int retryDelay = 2000;
	private ListenerBehavior listenerBehavior;
	private MessageSerializer serializer = Object::toString;
	private boolean enableEvents = true;
	private ConnectionEvent onConnect = () -> System.out.println("Connected!");
	private ConnectionEvent onDisconnect = () -> System.out.println("Disconnected!");
	private ConnectionEvent onPreConnect = () -> System.out.println("Preparing to connect...");
	private ConnectionEvent onPreDisconnect = () -> System.out.println("Preparing to disconnect...");
	private ConnectionEvent onPostDisconnect = () -> System.out.println("Disconnection completed!");
	private MessageEvent onMessageSent = message -> System.out.println("Message sent: " + message);
	private MessageEvent onMessageReceived = message -> System.out.println("Message received: " + message);
	private String fileSaveDirectory = "";
	private FileHandler fileHandler;
	private int BROADCAST_PORT;
	
	public Client(int BROADCAST_PORT) {
		setListeningForBroadCasts(BROADCAST_PORT);
		setDefaultListenerBehavior();
	}
	
	public Client(String serverAddress, int serverPort) {
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
		setDefaultListenerBehavior();
	}
	
	public static void runDataParse(InputStream inputStream, FileOutputStream fileOutputStream, int size) throws IOException {
		byte[] buffer = new byte[(int) size];
		int bytesRead;
		
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			String data = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
			
			if (data.contains("ENDFILE")) {
				data = data.substring(0, data.indexOf("ENDFILE"));
			}
			
			fileOutputStream.write(data.getBytes(StandardCharsets.UTF_8));
		}
	}
	
	public void setServerAddress(String serverAddress) {
		this.serverAddress = serverAddress;
	}
	
	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}
	
	public void setMaxRetries(int maxRetries) {
		this.maxRetries = maxRetries;
	}
	
	public void setRetryDelay(int retryDelay) {
		this.retryDelay = retryDelay;
	}
	
	public void setOnConnect(ConnectionEvent onConnect) {
		this.onConnect = onConnect;
	}
	
	public void setOnDisconnect(ConnectionEvent onDisconnect) {
		this.onDisconnect = onDisconnect;
	}
	
	public void setOnPreConnect(ConnectionEvent onPreConnect) {
		this.onPreConnect = onPreConnect;
	}
	
	public void setOnPreDisconnect(ConnectionEvent onPreDisconnect) {
		this.onPreDisconnect = onPreDisconnect;
	}
	
	public void setOnPostDisconnect(ConnectionEvent onPostDisconnect) {
		this.onPostDisconnect = onPostDisconnect;
	}
	
	public void setOnMessageSent(MessageEvent onMessageSent) {
		this.onMessageSent = onMessageSent;
	}
	
	public void setOnMessageReceived(MessageEvent onMessageReceived) {
		this.onMessageReceived = onMessageReceived;
	}
	
	public void setListenerBehavior(ListenerBehavior behavior) {
		this.listenerBehavior = behavior;
	}
	
	public void setDebugEnabled(boolean debugEnabled) {
		this.debugEnabled = debugEnabled;
	}
	
	public void setEnableEvents(boolean enableEvents) {
		this.enableEvents = enableEvents;
	}
	
	public synchronized void addResponseHandler(String command, MessageHandler handler) {
		responseHandlers.put(command, handler);
	}
	
	public synchronized void removeResponseHandler(String command) {
		responseHandlers.remove(command);
	}
	
	public void connect(int connectionTimeout, int readTimeout) {
		if (enableEvents) {
			onPreConnect.onEvent();
		}
		int retries = maxRetries;
		while (retries > 0) {
			try {
				printDebug("Attempting to connect...");
				socket = new Socket();
				socket.connect(new java.net.InetSocketAddress(serverAddress, serverPort), connectionTimeout);
				socket.setSoTimeout(readTimeout);
				
				reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				writer = new PrintWriter(socket.getOutputStream(), true);
				
				isConnected = true;
				if (enableEvents) {
					onConnect.onEvent();
				}
				
				startListener();
				return;
			} catch (IOException e) {
				retries--;
				printDebug("Connection failed. Retries left: " + retries);
				try {
					Thread.sleep(retryDelay);
				} catch (InterruptedException ignored) {
				}
				disconnect();
			}
		}
		printDebug("Failed to connect after " + maxRetries + " attempts.");
	}
	
	public void connectAsync(int connectionTimeout, int readTimeout) {
		new Thread(() -> connect(connectionTimeout, readTimeout)).start();
	}
	
	public void sendMessage(Object message) {
		if (message != null) {
			if (isConnected && writer != null) {
				String serialized = serializer.serialize(message);
				if (serialized != null) {
					writer.println(serialized);
					printDebug("Sent: " + serialized);
					onMessageSent.onMessage(serialized);
				}
			} else {
				printDebug("Not connected. Cannot send message.");
			}
		}
	}
	
	public void disconnect() {
		if (enableEvents) {
			onPreDisconnect.onEvent();
		}
		printDebug("Disconnecting...");
		try {
			if (reader != null) {
				reader.close();
			}
			if (writer != null) {
				writer.close();
			}
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
			if (listenerThread != null && listenerThread.isAlive()) {
				listenerThread.interrupt();
			}
			
			isConnected = false;
			if (enableEvents) {
				onDisconnect.onEvent();
			}
			if (enableEvents) {
				onPostDisconnect.onEvent();
			}
		} catch (IOException e) {
			printDebug("Error during disconnect: " + e.getMessage());
		}
	}
	
	public void setFileHandler(FileHandler fileHandler) {
		this.fileHandler = fileHandler;
	}
	
	public void setListeningForBroadCasts(int broadCastPort) {
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
						
						printDebug("Discovered server at " + serverAddress + ":" + serverPort);
						this.setServerPort(serverPort);
						this.setServerAddress(serverAddress);
					}
				} else {
					printDebug("Already connected");
					break;
				}
			}
		} catch (IOException e) {
			printDebug("Error listening for broadcasts: " + e.getMessage());
		}
	}
	
	private void startListener() {
		listenerThread = new Thread(() -> {
			System.out.println("Starting listener thread");
			try {
				String message;
				while ((message = reader.readLine()) != null) {
					onMessageReceived.onMessage(message);
					listenerBehavior.onMessage(message);  // Continue handling messages after the file transfer.
				}
			} catch (IOException e) {
				printDebug("Connection lost: " + e.getMessage());
				disconnect();  // Only disconnect when the connection is lost, not after file transfer.
			}
		});
		listenerThread.start();
	}
	
	private void defaultFileHandler(String fileName, InputStream inputStream, int size) throws IOException {
		String savePath = fileSaveDirectory.isEmpty() ? fileName : fileSaveDirectory + "/" + fileName;
		try (FileOutputStream fileOutputStream = new FileOutputStream(savePath)) {
			runDataParse(inputStream, fileOutputStream, size);
		}
		printDebug("File " + fileName + " received successfully at default location.");
	}
	
	// Adjust the listener behavior to handle file transfer
	private void setDefaultListenerBehavior() {
		this.listenerBehavior = message -> {
			printDebug("Received: " + message);
			if (message.startsWith("STARTFILE")) {
				String[] parts = message.split(" ");
				String fileName = parts[1];
				long fileSize = Long.parseLong(parts[2]);
				String fileType = parts.length > 3 ? parts[3] : "unknown"; // Default to "unknown" if no type is provided
				
				try (InputStream inputStream = socket.getInputStream()) {
					if (fileHandler != null) {
						fileHandler.handleFile(fileName, inputStream, (int) fileSize, fileType);
					} else {
						defaultFileHandler(fileName, inputStream, (int) fileSize);
					}
				} catch (IOException e) {
					printDebug("Error receiving file: " + e.getMessage());
				}
			} else if (message.equals("ENDFILE")) {
				printDebug("File transfer completed.");
			}
		};
	}
	
	private void printDebug(String message) {
		if (debugEnabled) {
			System.out.println(message);
		}
	}
	
	@FunctionalInterface
	public interface MessageHandler {
		void handle(String message);
	}
	
	@FunctionalInterface
	public interface ListenerBehavior {
		void onMessage(String message);
	}
	
	@FunctionalInterface
	public interface ConnectionEvent {
		void onEvent();
	}
	
	@FunctionalInterface
	public interface MessageEvent {
		void onMessage(String message);
	}
	
	@FunctionalInterface
	public interface MessageSerializer {
		String serialize(Object message);
	}
	
	@FunctionalInterface
	public interface FileHandler {
		void handleFile(String fileName, InputStream fileData, int size, String fileType) throws IOException;
	}
	
}
