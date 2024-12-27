package net.guess.ServerUtil;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ServerEventManager {
	private Consumer<Integer> onServerInit = port -> {
		System.out.println("ServerUtil initialized on port: " + port);
	};
	private Consumer<String> onBroadcastStopped = message -> {
		System.out.println("Broadcasting stopped");
	};
	private Consumer<String> onClientConnectHandler = clientID -> {
		System.out.println("Client connected: " + clientID);
	};
	private Consumer<String> onClientDisconnectHandler = clientID -> {
		System.out.println("Client disconnected: " + clientID);
	};
	private BiConsumer<String, Exception> onServerErrorHandler = (message, exception) -> {
		System.out.println(message + ": " + exception.getMessage());
	};
	private Consumer<Integer> onServerStartHandler = port -> {
		System.out.println("ServerUtil started on port: " + port);
	};
	private Consumer<String> onServerStopHandler = message -> {
		System.out.println("ServerUtil stopped: " + message);
	};
	private Consumer<Integer> onBroadcastHandler = port -> {
		System.out.println("Broadcasting on port: " + port);
	};
	private Consumer<String> onClientMessageHandler = message -> {
		System.out.println("Sent: " + message);
	};
	private Consumer<String> onHeartbeatReceivedHandler = message -> {
		System.out.println("Heartbeat Received");
	};
	private Consumer<String> onHeartbeatLostHandler = message -> {
		System.out.println("Heartbeat lost: " + message);
	};
	private BiConsumer<String, Integer> onFileSendingHandler = (file, length) -> {
		System.out.println("Sending file: " + file + " of length: " + length+" bytes");
	};
	private BiConsumer<String, Integer> onFileSentHandler = (file, length) -> {
		System.out.println("File sent: " + file + " of length: " + length+" bytes");
	};
	
	// Setters
	public void setOnBroadcastStopped(Consumer<String> handler) {
		this.onBroadcastStopped = handler;
	}
	
	public void setOnServerInit(Consumer<Integer> handler) {
		this.onServerInit = handler;
	}
	
	public void setOnClientConnect(Consumer<String> handler) {
		this.onClientConnectHandler = handler;
	}
	
	public void setOnClientDisconnect(Consumer<String> handler) {
		this.onClientDisconnectHandler = handler;
	}
	
	public void setOnServerError(BiConsumer<String, Exception> handler) {
		this.onServerErrorHandler = handler;
	}
	
	public void setOnServerStart(Consumer<Integer> handler) {
		this.onServerStartHandler = handler;
	}
	
	public void setOnServerStop(Consumer<String> handler) {
		this.onServerStopHandler = handler;
	}
	
	public void setOnBroadcast(Consumer<Integer> handler) {
		this.onBroadcastHandler = handler;
	}
	
	public void setOnClientMessage(Consumer<String> handler) {
		this.onClientMessageHandler = handler;
	}
	
	public void setOnHeartbeatReceived(Consumer<String> handler) {
		this.onHeartbeatReceivedHandler = handler;
	}
	
	public void setOnHeartbeatLost(Consumer<String> handler) {
		this.onHeartbeatLostHandler = handler;
	}
	
	public void setOnFileSending(BiConsumer<String, Integer> handler) {
		this.onFileSendingHandler = handler;
	}
	
	public void setOnFileSent(BiConsumer<String, Integer> handler) {
		this.onFileSentHandler = handler;
	}
	
	// Triggers
	public void triggerBroadcastStopped(String message) {
		onBroadcastStopped.accept(message);
	}
	
	public void triggerServerInit(Integer port) {
		onServerInit.accept(port);
	}
	
	public void triggerClientConnect(String clientID) {
		onClientConnectHandler.accept(clientID);
	}
	
	public void triggerClientDisconnect(String clientID) {
		onClientDisconnectHandler.accept(clientID);
	}
	
	public void triggerServerError(String message, Exception e) {
		onServerErrorHandler.accept(message, e);
	}
	
	public void triggerServerStart(Integer port) {
		onServerStartHandler.accept(port);
	}
	
	public void triggerServerStop(String message) {
		onServerStopHandler.accept(message);
	}
	
	public void triggerBroadcast(Integer port) {
		onBroadcastHandler.accept(port);
	}
	
	public void triggerClientMessage(String message) {
		onClientMessageHandler.accept(message);
	}
	
	public void triggerHeartbeatReceived(String message) {
		onHeartbeatReceivedHandler.accept(message);
	}
	
	public void triggerHeartbeatLost(String message) {
		onHeartbeatLostHandler.accept(message);
	}
	
	public void triggerFileSending(String file, Integer length) {
		onFileSendingHandler.accept(file, length);
	}
	
	public void triggerFileSent(String file, Integer length) {
		onFileSentHandler.accept(file, length);
	}
}
