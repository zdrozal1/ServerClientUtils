package net.guess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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
	private ConnectionEvent onPostConnect = () -> System.out.println("Connection established successfully!");
	private ConnectionEvent onPreDisconnect = () -> System.out.println("Preparing to disconnect...");
	private ConnectionEvent onPostDisconnect = () -> System.out.println("Disconnection completed!");
	private MessageEvent onMessageSent = message -> System.out.println("Message sent: " + message);
	private MessageEvent onMessageReceived = message -> System.out.println("Message received: " + message);
	
	public Client(String serverAddress, int serverPort) {
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
		setDefaultListenerBehavior();
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
	
	public void setSerializer(MessageSerializer serializer) {
		this.serializer = serializer;
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
	
	public void setOnPostConnect(ConnectionEvent onPostConnect) {
		this.onPostConnect = onPostConnect;
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
				if (enableEvents) {
					onPostConnect.onEvent();
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
	
	private void startListener() {
		listenerThread = new Thread(() -> {
			try {
				String message;
				while ((message = reader.readLine()) != null) {
					onMessageReceived.onMessage(message);
					listenerBehavior.onMessage(message);
				}
			} catch (IOException e) {
				printDebug("Connection lost: " + e.getMessage());
			}
		});
		listenerThread.start();
	}
	
	private void setDefaultListenerBehavior() {
		this.listenerBehavior = message -> {
			printDebug("Received: " + message);
			String[] parts = message.split(" ", 2);
			if (parts.length > 0) {
				String command = parts[0];
				String data = parts.length > 1 ? parts[1] : "";
				if (responseHandlers.containsKey(command)) {
					responseHandlers.get(command).handle(data);
				}
			} else {
				printDebug("Received empty or invalid message: " + message);
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
}
