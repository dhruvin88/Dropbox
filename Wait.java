import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Wait implements Runnable {
	
	Socket socket;
	String folderPath;
	
	public Wait(Socket socket, String folderPath) {
		this.socket = socket;
		this.folderPath = folderPath;
	}

	@Override
	public void run() {
		try {
			waitRespond();
		}catch(IOException e) {
			e.printStackTrace();
		}
		
	}

	private void waitRespond() throws IOException {
		DataOutputStream  dos = 
                new DataOutputStream(socket.getOutputStream());
        DataInputStream dis = 
        		new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        
        System.out.println("waiting");
        int respond = dis.readInt();
        
        if(respond==0) { //delete file from server
        		System.out.println("Deleting");
            String fileName = dis.readUTF();
        		File file = new File(folderPath+"/"+fileName);
        		if(file.delete()){
                    System.out.println("File deleted successfully");
            }
            else{
                    System.out.println("Failed to delete the file");
            }
        }
        else if (respond == 1){ //get file
            String fileName = dis.readUTF();
            long lastMod = dis.readLong();
           
    			File file = new File(folderPath+"/"+fileName);
    			
    			//save the file content only if it is not the same lastMod date
    			if(file.lastModified() < lastMod) {
    				System.out.println("getting "+fileName+':'+lastMod);
    				double fileSize = dis.readDouble();
                		
                	int n = 0;
                	byte[] buf = new byte[4092];

                	FileOutputStream fos = new FileOutputStream(folderPath+"/"+fileName);
                	while(fileSize > 0 && (n = dis.read(buf, 0, (int)Math.min(buf.length, fileSize))) != -1) {
                		fos.write(buf,0,n);
                		fileSize -= n;
                	}
                	fos.close();
                	file.setLastModified(lastMod); 
    			}
    			else {
    				//get the file content but do not save
    				double fileSize = dis.readDouble();
            	
                	int n = 0;
                	byte[] buf = new byte[4092];

                	while(fileSize > 0 && (n = dis.read(buf, 0, (int)Math.min(buf.length, fileSize))) != -1) {
                		fileSize -= n;
                	}
    			}
        		
        }
        else {
        		waitRespond();
        }
        waitRespond();
	}

}
