package BatchApp.ServerLauncher;


/* 
 * Entire code basically stolen from: https://www.baeldung.com/java-compress-and-uncompress
 * Testing out the mysteries of the universe
 * Later turned it into an object for the bot to use
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipDirectory {
    public static void main(String[] args) throws IOException {
    	
    	// File to start compression on
        String sourceFile = "C:\\Users\\Michael\\OneDrive\\Documents\\eclipse-workspace\\ServerLauncher\\servertest\\world";
        String zipFile = "C:\\Users\\Michael\\OneDrive\\Documents\\eclipse-workspace\\ServerLauncher\\servertest\\worldCompressed.zip";
        /* Need both zip and file output streams
        FileOutputStream fos = new FileOutputStream("C:\\Users\\Michael\\OneDrive\\Documents\\eclipse-workspace\\ServerLauncher\\servertest\\worldCompressed.zip");
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        // Labeling the initial file
        File fileToZip = new File(sourceFile);
        
        zipFile(fileToZip, fileToZip.getName(), zipOut);
        zipOut.close();
        fos.close();
        */
        System.out.println(new ZipDirectory().ZipIt(sourceFile, zipFile));
    }
    
    public ZipDirectory () {
    	// Don't need anything in here
    }
    
    public int ZipIt(String inputFileName, String outputZipName) {
    	try {
    		FileOutputStream fos = new FileOutputStream(outputZipName);
            ZipOutputStream zipOut = new ZipOutputStream(fos);
            
            File fileToZip = new File(inputFileName);
            int type = zipFile(fileToZip, fileToZip.getName(), zipOut);
            zipOut.close();
            fos.close();
            return type;
    	}
    	catch(IOException e) {
    		return -1;
    	}
    	
    }

    private int zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
        	// Don't zip hidden files i guess
            return 0;
        }
        // Directory path, include file and fix name
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            // Didn't know you could do this with for thats cool
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return 1;
        }
        // File path, reads file into zip outputstream
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        // Not really sure how this works I assume it reads 1kb at a time
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
        return 1;
    }
}