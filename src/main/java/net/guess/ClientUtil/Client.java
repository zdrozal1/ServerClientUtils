package net.guess.ClientUtil;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class Client {
	private final Map<String, EventHandler> messageHandlers = new HashMap<>();
	private boolean isConnected = false;
	private Socket socket;
	private PrintWriter writer;
	private String serverHost;
	private int serverPort;
	private ClientEventManager clientEventManager = new ClientEventManager();
	
	public ClientEventManager getClientEventManager() {
		return clientEventManager;
	}
	
	public Client() {
		registerMessageHandler("SHUTDOWN", this::handleShutdown);
		registerMessageHandler("HEARTBEAT", msg -> {
			clientEventManager.triggerHeartbeatReceived(msg);
			writer.println("HEARTBEAT");
		});
	}
	
	public synchronized void receiveFileFromServer(String name) {
		try (Socket sock = new Socket(serverHost, serverPort);
		     InputStream is = sock.getInputStream();
		     DataInputStream dataIn = new DataInputStream(is);  // To read the file size
		     FileOutputStream fos = new FileOutputStream(name);
		     BufferedOutputStream bos = new BufferedOutputStream(fos)) {
			
			// Read the file size first
			int fileSize = dataIn.readInt();  // Read the file size
			
			byte[] fileByteArray = new byte[fileSize];
			int bytesRead;
			
			// Read the file data
			while ((bytesRead = is.read(fileByteArray)) != -1) {
				bos.write(fileByteArray, 0, bytesRead);
			}
			
			bos.flush();
			clientEventManager.triggerFileReceived(name, fileSize);
		} catch (IOException e) {
			clientEventManager.triggerClientError("Error receiving file", e);
		}
	}
	
	
	public void connectToServer(String address, int port) throws IOException {
		if (socket != null && !socket.isClosed()) {
			clientEventManager.triggerClientError("Closing existing socket before reconnecting.", null);
			socket.close();
		}
		
		new Thread(() -> {
			try {
				clientEventManager.triggerClientInit(port);
				socket = new Socket();
				clientEventManager.triggerClientConnectAttempt(address, port);
				socket.connect(new InetSocketAddress(address, port), 10000);
				
				socket.setSoTimeout(10000);
				clientEventManager.triggerSocketTimeoutSet();
				
				isConnected = true;
				serverHost = address;
				serverPort = port;
				
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				new Thread(() -> receiveMessages(in)).start();
				
				writer = new PrintWriter(socket.getOutputStream(), true);
				clientEventManager.triggerClientConnect(address, port);
				
			} catch (IOException e) {
				isConnected = false;
				clientEventManager.triggerClientError("Failed to connect", e);
				disconnectFromServer();
			}
		}).start();
	}
	
	public void receiveMessages(BufferedReader in) {
		new Thread(() -> {
			try {
				String fromServer;
				while ((fromServer = in.readLine()) != null) {
					String command = fromServer.split(" ")[0];
					EventHandler handler = messageHandlers.get(command);
					if (handler != null) {
						handler.handle(fromServer);
					} else {
						clientEventManager.triggerServerMessage(fromServer);
					}
				}
			} catch (SocketTimeoutException e) {
				isConnected = false;
				clientEventManager.triggerClientError("Read timed out", e);
				disconnectFromServer();
			} catch (IOException e) {
				isConnected = false;
				clientEventManager.triggerClientError("Error reading from server", e);
				disconnectFromServer();
			}
		}).start();
	}
	
	public void disconnectFromServer() {
		try {
			isConnected = false;
			if (socket != null && !socket.isClosed()) {
				socket.close();
				clientEventManager.triggerClientDisconnect("");
			}
		} catch (IOException e) {
			clientEventManager.triggerClientError("Error disconnecting from server", e);
		}
	}
	
	public void listenForServerBroadcasts(int broadcastPort) {
		clientEventManager.triggerBroadcastPortUsed(broadcastPort);
		
		try (DatagramSocket socket = new DatagramSocket(broadcastPort, InetAddress.getByName("0.0.0.0"))) {
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
						
						clientEventManager.triggerServerDiscovery(serverAddress, serverPort);
						try {
							connectToServer(serverAddress, serverPort);
						} catch (IOException e) {
							clientEventManager.triggerClientError("Error connecting to server; " + serverAddress + ":" + serverPort + " ", e);
						}
					}
				} else {
					clientEventManager.triggerClientAlreadyConnected(socket.getInetAddress().getHostAddress());
					break;
				}
			}
		} catch (IOException e) {
			clientEventManager.triggerClientError("Error listening for broadcasts", e);
		}
	}
	
	public void registerMessageHandler(String command, EventHandler handler) {
		messageHandlers.put(command, handler);
	}
	
	private void handleShutdown(String message) {
		clientEventManager.triggerShutdownReceived(message);
		disconnectFromServer();
	}
}
