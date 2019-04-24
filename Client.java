import java.io.*;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class Client {
	static Socket clientSocket;
	static String folderPath;
	
	public static void main(String[] args) throws IOException {
		Scanner scan = new Scanner(System.in);
		
		System.out.println("Enter Folder Path:");
		folderPath = scan.nextLine();
		
		clientSocket = new Socket("localhost", 6789); 
		System.out.println("Starting connection with server");
		sync(folderPath);

		Path dir = Paths.get(folderPath);
		scan.close();
		
        
		Wait wait = new Wait(clientSocket, folderPath);
		Thread thread = new Thread(wait);
		thread.start();
		
        try {
			new WatchDir(dir, clientSocket).processEvents();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void sync(String folderPath) throws IOException {
		HashMap<String, Long> fileNamesOnClient = new HashMap<String, Long>();
		File folder = new File(folderPath);
		File[] listOfFiles = folder.listFiles();
				
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				fileNamesOnClient.put(listOfFiles[i].getName(), listOfFiles[i].lastModified());
			}
		}
        
        DataOutputStream outToServer = 
                new DataOutputStream(clientSocket.getOutputStream()); 
              
        BufferedReader inFromServer = 
                      new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
       
        String fileNamesToServer = "";
        
       Iterator it = fileNamesOnClient.entrySet().iterator();
       
       while(it.hasNext()) {
    	   		Map.Entry pair = (Map.Entry) it.next();
    	   		fileNamesToServer += pair.getKey()+":"+pair.getValue()+", ";
       }
               
        outToServer.writeBytes(fileNamesToServer+ '\n');
        
        String requestedFiles = inFromServer.readLine();
        sendFilesToServer(requestedFiles, clientSocket);
        
        getFilesFromServer(clientSocket);
	}

	private static void sendFilesToServer(String requestedFiles, Socket clientSocket) throws IOException {
		DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
		String split[] = requestedFiles.split(":");
        int numOfFiles = Integer.parseInt(split[0]);
        if(numOfFiles > 0) {
        		split = split[1].split(", ");
            
            dos.writeInt(numOfFiles);
            dos.flush();
            
            for(int i = 0; i < split.length; i++) {
            		dos.writeUTF(split[i]);
            		sendFile(split[i], dos);
            		dos.flush();
            }
           
        }
        else {
    			dos.writeInt(0);
    			dos.flush();
        }
	}

	private static void sendFile(String fileName, DataOutputStream dos2) throws IOException {
		
		int n = 0;
        byte[] buf = new byte[4092];
        
        System.out.println("Sending "+ fileName+" to server");
		FileInputStream fis = new FileInputStream(folderPath+"/"+fileName);
		File file = new File(folderPath+"/"+fileName);
		dos2.writeDouble(file.length());
		
		long lastMod = file.lastModified();
		dos2.writeLong(lastMod);
		
		while((n = fis.read(buf)) != -1) {
			dos2.write(buf,0,n);
			dos2.flush();
		}
		fis.close();
		file.setLastModified(lastMod);
		
	}

	private static void getFilesFromServer( Socket clientSocket) throws IOException {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));

		int number  = dis.readInt();
		if(number > 0) {
			for(int i = 0; i < number; i++) {
				File file = new File(folderPath+"/"+dis.readUTF());
				System.out.println("Receiving "+ file.getName()+" from Server");
				getFile(file,dis);
			}	
		}     
	}

	private static void getFile (File file, DataInputStream dis) throws IOException {
		int n = 0;
		byte[] buf = new byte[4092];
		
		FileOutputStream fos = new FileOutputStream(folderPath+"/"+ file.getName());
		double fileSize = dis.readDouble();
		
		long lastMod = dis.readLong();
		
		while(fileSize > 0 && (n = dis.read(buf, 0, (int)Math.min(buf.length, fileSize))) != -1) {
			fos.write(buf,0,n);
			fileSize -= n;
		}
		
		fos.close();
		file.setLastModified(lastMod);
		
	}

}
