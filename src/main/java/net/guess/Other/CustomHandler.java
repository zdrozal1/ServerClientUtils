package net.guess.Other;

import net.guess.Client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CustomHandler implements Client.FileHandler {
	
	@Override
	public void handleFile(String fileName, InputStream fileData, int size, String fileType) throws IOException {
		String newFileName = "custom_" + fileName;
		String customDirectory = "custom/location/";
		
		// You can now handle different file types differently
		if ("image".equalsIgnoreCase(fileType)) {
			// Handle image files
			customDirectory = "custom/images/";
		} else if ("text".equalsIgnoreCase(fileType)) {
			// Handle text files differently
			customDirectory = "custom/text/";
		}
		
		new File(customDirectory).mkdirs();
		try (FileOutputStream fileOutputStream = new FileOutputStream(customDirectory + newFileName)) {
			Client.runDataParse(fileData, fileOutputStream, size);
		}
		
		System.out.println("File saved as " + customDirectory + newFileName);
	}
}