import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Server {
	public static void main(String argv[]) throws Exception{
		//Set the port number
		int port = 6789;
		
		//Establish the listen socket
		ServerSocket WebSocket = new ServerSocket(port);
		System.out.println("Starting Server...");
			     
		//Process HTTP service requests in an infinite loop
		while (true) {
			// Listen for a TCP connection request.
			Socket connectionSocket = WebSocket.accept();
			      
			//Construct object to process HTTP request message
			clientConnection request = new clientConnection(connectionSocket);
			      
			//Create a new thread to process the request
			Thread thread = new Thread(request);
			//Start the thread
			thread.start();
		}
	}
}

final class clientConnection implements Runnable{
	Socket socket;
	//change dir for server folder
	String serverFolder = "/Users/dhruvinpatel/Documents/workspace/561 Final Project/ServerTest";
	HashMap<String, Long> fileNamesOnServer = new HashMap<String, Long>();
	HashMap<String, Long> fileNamesOnClient = new HashMap<String, Long>();
	
	
	public clientConnection(Socket connectionSocket) {
		socket = connectionSocket;
	}

	@Override
	public void run() {
		try{
			startSync();
			Wait wait = new Wait(socket,serverFolder);
			Thread thread = new Thread(wait);
			thread.start();
			
			Path dir = Paths.get(serverFolder);
			new WatchDir(dir, socket).processEvents();
		}catch(Exception e){
			System.out.println(e);
		}
	}

	private void startSync() throws IOException {
		BufferedReader inFromClient = 
	              new BufferedReader(new InputStreamReader(socket.getInputStream())); 
		
	    //server's files
		File folder = new File(serverFolder);
		File[] listOfFiles = folder.listFiles();
		
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				fileNamesOnServer.put(listOfFiles[i].getName(), listOfFiles[i].lastModified());
		    }
		}
		
		String clientFiles = inFromClient.readLine();
		if(!clientFiles.equals("")) {
			String[] splitFiles = clientFiles.split(", ");
			for(int i = 0; i < splitFiles.length; i++){
				String[] tmp = splitFiles[i].split(":");
				fileNamesOnClient.put(tmp[0], Long.parseLong(tmp[1]));
			}
		}

		//compares strings and files
		//checks for files not on the server
		Iterator it = fileNamesOnServer.entrySet().iterator();
		
		ArrayList<String> sendFileList = new ArrayList<String>();
		ArrayList<String> requestFileList = new ArrayList<String>();
	       
	    while(it.hasNext()) {
	    	   	Map.Entry pair = (Map.Entry) it.next();
	    	   	if(fileNamesOnClient.containsKey(pair.getKey())) {
	    	   		if(Long.parseLong(pair.getValue().toString()) > 
	    	   			Long.parseLong(fileNamesOnClient.get(pair.getKey()).toString())) {
	    	   			sendFileList.add(pair.getKey().toString());
	    	   			System.out.println(pair.getKey().toString() + " not in Client");
	    	   		}
	    	   	}else {
	    	   		sendFileList.add(pair.getKey().toString());
	    	   		System.out.println(pair.getKey().toString() + " not in Client");
	    	   	}
	    }
	       
		
		//checks for files not on client
	    it = fileNamesOnClient.entrySet().iterator();
	    
	    while(it.hasNext()) {
	    		Map.Entry pair = (Map.Entry) it.next();
	    		if(fileNamesOnServer.containsKey(pair.getKey())) {
	    			if(Long.parseLong(pair.getValue().toString()) > 
	    				Long.parseLong(fileNamesOnServer.get(pair.getKey()).toString())) {
	    	   			requestFileList.add(pair.getKey().toString());
	    	   			System.out.println(pair.getKey().toString() + " not in Server");
	    	   		}
	    		}else {
	    			requestFileList.add(pair.getKey().toString());
	    			System.out.println(pair.getKey().toString()+ " not on Server");
	    		}
	    }
	    
	    //create a string with the # of files and filenames to get from client
	    String returnFileNamesRequest = requestFileList.size()+":";
	    
	    for(int i = 0;i < requestFileList.size(); i++) {
	    		returnFileNamesRequest += requestFileList.get(i)+", ";
	    }
	    
	    //sent request for files missing on the server
	    requestFiles(returnFileNamesRequest,socket);
	    receiveFiles(socket);
	    
	    //sent file missing from the server
	    sendFiles(sendFileList, socket);
	    
	    
	    
	}

	private void receiveFiles(Socket socket) throws IOException {
		DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));		
		int number  = dis.readInt();
		
		if(number != 0) {
			for(int i = 0; i < number; i++) {
				File file = new File(serverFolder+"/"+dis.readUTF());
				System.out.println("recieving "+file.getName()+" from client");
				getFile(file,dis);
			}
		}
	}

	private void getFile(File file, DataInputStream dis) throws IOException {
		int n = 0;
		byte[] buf = new byte[4092];
		
		FileOutputStream fos = new FileOutputStream(serverFolder+"/"+ file.getName());
		double fileSize = dis.readDouble();
		long lastMod = dis.readLong();
	
		while(fileSize > 0 && (n = dis.read(buf, 0, (int)Math.min(buf.length, fileSize))) != -1) {
			fos.write(buf,0,n);
			fileSize -= n;
		}
		fos.close();
		file.setLastModified(lastMod);
		
	}

	private void sendFiles(ArrayList<String> sendFileList, Socket socket) throws IOException {
		DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
	
		int numOfFiles = sendFileList.size();
		if(numOfFiles != 0) {
			dos.writeInt(numOfFiles);
			dos.flush();
			
			for(int i = 0; i < sendFileList.size(); i++) {
				dos.writeUTF(sendFileList.get(i));
				System.out.println("Sending "+ sendFileList.get(i)+ " to Client");
				sendFile(sendFileList.get(i), dos);
				dos.flush();
			}
		}
		else {
			dos.writeInt(0);
			dos.flush();
		}
		
	}

	private void sendFile(String fileName, DataOutputStream dos) throws IOException {
		int n = 0;
        byte[] buf = new byte[4092];
        
        FileInputStream fis = new FileInputStream(serverFolder+"/"+fileName);
		File file = new File(serverFolder+"/"+fileName);
		dos.writeDouble(file.length());
		dos.flush();
		
		long lastMod = file.lastModified();
		dos.writeLong(lastMod);
		dos.flush();
		
		while((n = fis.read(buf)) != -1) {
			dos.write(buf,0,n);
			dos.flush();
		}
		fis.close();
		file.setLastModified(lastMod);
		
	}

	private void requestFiles(String request, Socket socket) throws IOException {
		DataOutputStream  outToClient = 
                new DataOutputStream(socket.getOutputStream());
		outToClient.writeBytes(request+"\n");
	}
	
}
