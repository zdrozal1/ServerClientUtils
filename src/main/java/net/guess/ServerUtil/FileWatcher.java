package net.guess.ServerUtil;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

public class FileWatcher {
	private final Path filePath;
	private final String fileNameToWatch;
	private final Consumer<Path> onChange;
	private final long debounceTime;
	private final WatchEvent.Kind<Path>[] watchEventKinds;
	private Path dir;
	private long lastUpdateTime = 0;
	private boolean isValid = true;
	
	@SafeVarargs
	public FileWatcher(String filePath, long debounceTime, Consumer<Path> onChange, WatchEvent.Kind<Path>... watchEventKinds) {
		this.filePath = Paths.get(filePath);
		this.dir = this.filePath.getParent();
		
		// Ensure that dir is never null
		if (this.dir == null) {
			this.dir = Paths.get(""); // Fallback to current directory
		}
		
		// Check if the parent directory is valid
		if (!Files.isDirectory(this.dir)) {
			System.err.println("Directory " + dir + " is not valid");
			isValid = false;
		}
		
		this.fileNameToWatch = this.filePath.getFileName().toString();
		this.onChange = onChange;
		this.debounceTime = debounceTime;
		this.watchEventKinds = watchEventKinds;
	}
	
	public void startWatching() {
		if (!isValid) {
			System.err.println("FileWatcher is not valid for: " + fileNameToWatch + " Aborting FileWatcher.");
			return;
		}
		
		Thread watchThread = new Thread(() -> {
			try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
				dir.register(watcher, watchEventKinds);
				
				while (true) {
					WatchKey key;
					try {
						key = watcher.take();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						return;
					}
					
					for (WatchEvent<?> event : key.pollEvents()) {
						WatchEvent.Kind<?> kind = event.kind();
						
						if (kind == StandardWatchEventKinds.OVERFLOW) {
							continue;
						}
						
						Path fileName = ((WatchEvent<Path>) event).context();
						if (fileName.toString().equals(fileNameToWatch)) {
							Path file = dir.resolve(fileName);
							
							// Handle ENTRY_CREATE event
							if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
								if (!Files.exists(file)) {
									System.err.println("File " + file + " was created but does not exist yet, skipping.");
									continue;
								}
							}
							
							// Handle ENTRY_MODIFY event
							if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
								if (!Files.exists(file) || !Files.isRegularFile(file)) {
									System.err.println("File " + file + " does not exist or is not a regular file, skipping.");
									continue;
								}
							}
							
							long currentTime = System.currentTimeMillis();
							if (currentTime - lastUpdateTime >= debounceTime) {
								onChange.accept(file);
								lastUpdateTime = currentTime;
							}
						}
					}
					
					boolean valid = key.reset();
					if (!valid) {
						System.err.println("Watch key is no longer valid. Stopping watcher.");
						break;
					}
				}
			} catch (IOException e) {
				System.err.println("Error setting up file watch: " + e.getMessage());
			}
		});
		watchThread.setDaemon(true);
		watchThread.start();
	}
}
