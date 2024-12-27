package net.guess.ClientUtil;

import java.io.IOException;

@FunctionalInterface
public interface EventHandler {
	void handle(String message) throws IOException;
}
