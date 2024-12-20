package net.guess.Other;

import net.guess.Client;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

public class SimpleFileHandler implements Client.FileHandler {
	private ByteArrayOutputStream currentFileData = new ByteArrayOutputStream();
	
	@Override
	public void handleFile(String fileName, InputStream fileData, int size, String fileType) {
		currentFileData.reset();
		System.out.println("Preparing to receive file: " + fileName);
	}
	
	@Override
	public void appendFileData(String fileDataChunk) {
		try {
			byte[] decodedData = Base64.getDecoder().decode(fileDataChunk);
			currentFileData.write(decodedData);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void finishFile() {
		try (FileOutputStream fileOutputStream = new FileOutputStream("testFile.txt")) {
			currentFileData.writeTo(fileOutputStream);
			System.out.println("File " + "testFile.txt" + " received successfully.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
