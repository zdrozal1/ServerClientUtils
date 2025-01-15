package net.guess.ServerUtil;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server {
	private final List<FileWatcher> fileWatchers = new ArrayList<>();
	public boolean isConnected = false;
	private ServerSocket serverSocket;
	private Socket clientSocket;
	private PrintWriter writer;
	private ScheduledExecutorService broadcastExecutorService;
	private ScheduledExecutorService heartBeatExecutor;
	ServerEventManager serverEventManager = new ServerEventManager();
	
	public ServerEventManager getServerEventManager() {
		return serverEventManager;
	}
	
	public synchronized void sendFileToClient(String fileToSend) throws IOException {
		File file = new File(fileToSend);
		int fileLength = (int) file.length();
		serverEventManager.triggerFileSending(fileToSend, fileLength);
		
		try (Socket sock = serverSocket.accept();
		     FileInputStream fis = new FileInputStream(fileToSend);
		     BufferedInputStream bis = new BufferedInputStream(fis);
		     OutputStream os = sock.getOutputStream()) {
			
			// Send file size first
			DataOutputStream dataOut = new DataOutputStream(os);
			dataOut.writeInt(fileLength);  // Send the file size
			
			byte[] fileByteArray = new byte[fileLength];
			int bytesRead;
			int totalBytesRead = 0;
			
			while ((bytesRead = bis.read(fileByteArray, totalBytesRead, fileByteArray.length - totalBytesRead)) != -1) {
				totalBytesRead += bytesRead;
				if (totalBytesRead == fileLength) {
					break;
				}
			}
			
			os.write(fileByteArray, 0, totalBytesRead);
			os.flush();
			serverEventManager.triggerFileSent(fileToSend, fileLength);
		} catch (IOException e) {
			serverEventManager.triggerServerError("Error sending file to client", e);
		}
	}
	
	
	public void handleClient() {
		BufferedReader reader = null;
		try {
			isConnected = true;
			reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			writer = new PrintWriter(clientSocket.getOutputStream(), true);
			
			for (FileWatcher fileWatcher : fileWatchers) {
				fileWatcher.startWatching();
			}
			
			heartBeatExecutor = Executors.newScheduledThreadPool(1);
			heartBeatExecutor.scheduleAtFixedRate(() -> {
				try {
					if (!clientSocket.isClosed() && !clientSocket.isOutputShutdown()) {
						writer.println("HEARTBEAT");
					} else {
						handleClientDisconnection(heartBeatExecutor);
					}
				} catch (Exception e) {
					serverEventManager.triggerServerError("Error handling client heartbeat", e);
					handleClientDisconnection(heartBeatExecutor);
				}
			}, 0, 5, TimeUnit.SECONDS);
			
			String inputLine;
			while ((inputLine = reader.readLine()) != null) {
				if ("HEARTBEAT".equals(inputLine)) {
					serverEventManager.triggerHeartbeatReceived(inputLine);
				}
			}
		} catch (SocketException e) {
			serverEventManager.triggerServerError("Client disconnected unexpectedly", e);
			isConnected = false;
		} catch (IOException e) {
			serverEventManager.triggerServerError("Error with client connection", e);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
				disconnectClient();
			} catch (IOException e) {
				serverEventManager.triggerServerError("Error closing reader", e);
			}
		}
	}
	
	public void addFileWatcher(FileWatcher fileWatcher) {
		fileWatchers.add(fileWatcher);
	}
	
	private void disconnectClient() {
		if (clientSocket != null && !clientSocket.isClosed()) {
			try {
				clientSocket.close();
				serverEventManager.triggerClientDisconnect(clientSocket.getInetAddress().getHostAddress());
			} catch (IOException e) {
				serverEventManager.triggerServerError("Error disconnecting client", e);
			}
		}
	}
	
	public void startServerAsync(int port) {
		new Thread(() -> {
			try {
				startServer(port);
			} catch (Exception e) {
				serverEventManager.triggerServerError("Error starting asynchronously", e);
			}
		}).start();
	}
	
	public void startServer(int port) {
		try {
			serverEventManager.triggerServerInit(port);
			serverSocket = new ServerSocket(port);
			serverEventManager.triggerServerStart(port);
			
			clientSocket = serverSocket.accept();
			serverEventManager.triggerClientConnect(clientSocket.getInetAddress().getHostAddress());
			handleClient();
		} catch (IOException e) {
			serverEventManager.triggerServerError("Error starting server", e);
		} finally {
			stopServer();
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
					serverEventManager.triggerBroadcast(port);
				} catch (IOException e) {
					serverEventManager.triggerServerError("Error broadcasting server availability", e);
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
				serverEventManager.triggerServerError("Error stopping broadcast executor service", e);
			}
			serverEventManager.triggerBroadcastStopped("");
		}
	}
	
	public void sendMessage(String message) {
		try {
			if (!clientSocket.isClosed()) {
				writer.println(message);
				serverEventManager.triggerClientMessage(message);
			}
		} catch (Exception e) {
			serverEventManager.triggerServerError("Error sending message", e);
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
				serverEventManager.triggerServerStop("Shutdown");
			}
		} catch (IOException e) {
			serverEventManager.triggerServerError("Error shutting down server", e);
		}
	}
	
	private void handleClientDisconnection(ScheduledExecutorService executor) {
		isConnected = false;
		executor.shutdown();
		serverEventManager.triggerHeartbeatLost("Client Heartbeat Lost");
	}
	
}
