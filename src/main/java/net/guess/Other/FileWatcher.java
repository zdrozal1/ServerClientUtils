package net.guess.Other;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

public class FileWatcher {
	private final Path filePath;
	private Path dir;
	private final String fileNameToWatch;
	private final Consumer<Path> onChange;
	private final long debounceTime;
	private long lastUpdateTime = 0;
	private final WatchEvent.Kind<Path>[] watchEventKinds;
	private boolean isValid = true;
	
	@SafeVarargs
	public FileWatcher(String filePath, long debounceTime, Consumer<Path> onChange, WatchEvent.Kind<Path>... watchEventKinds) {
		this.filePath = Paths.get(filePath);
		this.dir = this.filePath.getParent();
		
		// Skip file existence checks if only ENTRY_CREATE event is passed
		boolean checkFileExistence = true;
		for (WatchEvent.Kind<Path> kind : watchEventKinds) {
			if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
				checkFileExistence = false;
				break;
			}
		}
		
		if (checkFileExistence) {
			if (!Files.exists(this.filePath)) {
				System.err.println("File " + filePath + " does not exist");
				isValid = false;
			}
			
			if (!Files.isRegularFile(this.filePath)) {
				System.err.println("File " + filePath + " is not a regular file");
				isValid = false;
			}
			
			if (!Files.isDirectory(this.dir)) {
				System.err.println("File " + filePath + " is not in a valid directory");
				isValid = false;
			}
		} //TODO make sure this works
		if (this.dir == null) {
			this.dir = Path.of("");
		}
		
		this.fileNameToWatch = this.filePath.getFileName().toString();
		this.onChange = onChange;
		this.debounceTime = debounceTime;
		this.watchEventKinds = watchEventKinds;
	}
	
	public void startWatching() {
		if (!isValid) {
			System.err.println("FileWatcher is not valid for: " + fileNameToWatch + " Aborting.");
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
							long currentTime = System.currentTimeMillis();
							if (currentTime - lastUpdateTime >= debounceTime) {
								onChange.accept(dir.resolve(fileName));
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
