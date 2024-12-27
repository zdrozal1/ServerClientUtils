package net.guess.ClientUtil;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ClientEventManager {
	private Consumer<Integer> onClientInit = port -> {
		System.out.println("Client initialized on port: " + port);
	};
	private BiConsumer<String, Integer> onClientConnectHandler = (String address, Integer port) -> {
		System.out.println("Client connected: " + address + ":" + port);
	};
	private Consumer<String> onClientDisconnectHandler = message -> {
		System.out.println("Client disconnected: " + message);
	};
	private BiConsumer<String, Exception> onClientErrorHandler = (message, exception) -> {
		System.out.println(message + ": " + exception.getMessage());
	};
	private Consumer<String> onServerMessageHandler = message -> {
		System.out.println("Received: " + message);
	};
	private Consumer<String> onHeartbeatReceivedHandler = message -> {
		System.out.println("Heartbeat received: " + message);
	};
	private BiConsumer<String, Integer> onFileReceivedHandler = (String name, Integer size) -> {
		System.out.println("File received: " + name + ":" + size);
	};
	private BiConsumer<String, Integer> onClientConnectAttemptHandler = (address, port) -> {
		System.out.println("Attempting to connect to " + address + ":" + port);
	};
	private Runnable onSocketTimeoutSetHandler = () -> {
		System.out.println("Socket timeout set.");
	};
	private Consumer<String> onClientAlreadyConnectedHandler = message -> {
		System.out.println("Already connected");
	};
	private BiConsumer<String, Integer> onServerDiscoveryHandler = (serverAddress, serverPort) -> {
		System.out.println("Discovered server at " + serverAddress + ":" + serverPort);
	};
	private Consumer<Integer> onBroadcastPortUsedHandler = port -> {
		System.out.println("Using broadcastPort: " + port);
	};
	private Consumer<String> onShutdownReceivedHandler = message -> {
		System.out.println("Received shutdown, Disconnecting...");
	};
	private BiConsumer<String, Integer> onFileUpdateHandler = (fileName, fileSize) -> {
		System.out.println("File update received: " + fileName + " (" + fileSize + " bytes)");
	};
	
	// Setters
	public void setOnFileUpdateHandler(BiConsumer<String, Integer> handler) {
		this.onFileUpdateHandler = handler;
	}
	
	public void setOnClientInit(Consumer<Integer> handler) {
		this.onClientInit = handler;
	}
	
	public void setOnClientConnect(BiConsumer<String, Integer> handler) {
		this.onClientConnectHandler = handler;
	}
	
	public void setOnClientDisconnect(Consumer<String> handler) {
		this.onClientDisconnectHandler = handler;
	}
	
	public void setOnClientError(BiConsumer<String, Exception> handler) {
		this.onClientErrorHandler = handler;
	}
	
	public void setOnServerMessage(Consumer<String> handler) {
		this.onServerMessageHandler = handler;
	}
	
	public void setOnHeartbeatReceived(Consumer<String> handler) {
		this.onHeartbeatReceivedHandler = handler;
	}
	
	public void setOnFileReceived(BiConsumer<String, Integer> handler) {
		this.onFileReceivedHandler = handler;
	}
	
	public void setOnClientConnectAttempt(BiConsumer<String, Integer> handler) {
		this.onClientConnectAttemptHandler = handler;
	}
	
	public void setOnSocketTimeoutSet(Runnable handler) {
		this.onSocketTimeoutSetHandler = handler;
	}
	
	public void setOnClientAlreadyConnected(Consumer<String> handler) {
		this.onClientAlreadyConnectedHandler = handler;
	}
	
	public void setOnServerDiscovery(BiConsumer<String, Integer> handler) {
		this.onServerDiscoveryHandler = handler;
	}
	
	public void setOnBroadcastPortUsed(Consumer<Integer> handler) {
		this.onBroadcastPortUsedHandler = handler;
	}
	
	public void setOnShutdownReceived(Consumer<String> handler) {
		this.onShutdownReceivedHandler = handler;
	}
	
	// Triggers
	public void triggerClientInit(Integer port) {
		onClientInit.accept(port);
	}
	
	public void triggerFileUpdate(String fileName, Integer fileSize) {
		onFileUpdateHandler.accept(fileName, fileSize);
	}
	
	public void triggerClientConnect(String address, Integer port) {
		onClientConnectHandler.accept(address, port);
	}
	
	public void triggerClientDisconnect(String message) {
		onClientDisconnectHandler.accept(message);
	}
	
	public void triggerClientError(String message, Exception e) {
		onClientErrorHandler.accept(message, e);
	}
	
	public void triggerServerMessage(String message) {
		onServerMessageHandler.accept(message);
	}
	
	public void triggerHeartbeatReceived(String message) {
		onHeartbeatReceivedHandler.accept(message);
	}
	
	public void triggerFileReceived(String name, Integer size) {
		onFileReceivedHandler.accept(name, size);
	}
	
	public void triggerClientConnectAttempt(String address, Integer port) {
		onClientConnectAttemptHandler.accept(address, port);
	}
	
	public void triggerSocketTimeoutSet() {
		onSocketTimeoutSetHandler.run();
	}
	
	public void triggerClientAlreadyConnected(String message) {
		onClientAlreadyConnectedHandler.accept(message);
	}
	
	public void triggerServerDiscovery(String serverAddress, Integer serverPort) {
		onServerDiscoveryHandler.accept(serverAddress, serverPort);
	}
	
	public void triggerBroadcastPortUsed(Integer port) {
		onBroadcastPortUsedHandler.accept(port);
	}
	
	public void triggerShutdownReceived(String message) {
		onShutdownReceivedHandler.accept(message);
	}
}
