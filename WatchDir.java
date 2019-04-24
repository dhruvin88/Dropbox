import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class WatchDir{
	
	private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private boolean trace = false;
    Socket socket;

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    //creates watch service for a given directory
    WatchDir(Path dir, Socket clientSocket) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        socket = clientSocket;
      
        System.out.format("Scanning for changes %s ...\n", dir);
        register(dir);
        
        // enable trace after initial registration
        this.trace = true;
    }

    //process events
    void processEvents() throws IOException {
    		
        while(true) {
            // wait for key to be signaled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind kind = event.kind();

                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // print out event
                System.out.format("%s: %s\n", event.kind().name(), child);
                
                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                if(event.kind() == ENTRY_CREATE || event.kind() == ENTRY_MODIFY) {
                		System.out.println("here");
                		dos.writeInt(1);
                		dos.flush();
                		
                		dos.writeUTF(child.getFileName().toString());
                		dos.flush();
                		
                		FileInputStream fis = new FileInputStream(child.toFile().getAbsolutePath());
                		File file = new File(child.toFile().getAbsolutePath());
                		
                		long lastMod = file.lastModified();
                		dos.writeLong(lastMod);
                		dos.flush();
              
                		dos.writeDouble(file.length());
                		dos.flush();
                			
                		int n = 0;
                    	byte[] buf = new byte[4092];
                    	while((n = fis.read(buf)) != -1) {
                    		dos.write(buf,0,n);
                    		dos.flush();
                    	}

                		fis.close();
                		file.setLastModified(lastMod);
                		
                }
                else if(event.kind() == ENTRY_DELETE) { //ENTRY_DELETE
                		System.out.println("delete here");
                		dos.writeInt(0);
                		dos.flush();
                		
                		dos.writeUTF(child.getFileName().toString());
                		dos.flush();
                }
                
                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (kind == ENTRY_CREATE) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            register(child);
                        }
                    } catch (IOException x) {
                        System.out.println(x);
                    }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

}
