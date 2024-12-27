package net.guess.Other;

import java.io.IOException;

@FunctionalInterface
public interface MessageHandler {
	void handle(String message) throws IOException;
}
