import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class proxyd {
	
	static final int DEFAULT_SERVER_PORT = 80;
	static final String REQUEST_END = "\r\n\r\n";
	static final String LINE_END = "\r\n";
	static final int CACHE_TIME = 30000;
	
	static int portNum = 5023;
	
	private static Map<String, InetAddress> ips = new HashMap<String, InetAddress>();
	private static Map<Long, String> dnsTimes = new HashMap<Long,String>();
	
	public static void main(String[] args) {
		if(args.length == 2) {
			if(args[0].equals("-port")) {
				portNum = Integer.parseInt(args[1]);
			}
			else {
				usageErr();
			}
		}
		else if(args.length == 1) {
			usageErr();
		}
		else if(args.length != 0) {
			usageErr();
		}
		
		
		try {		
			ServerSocket clientSocket = new ServerSocket(portNum);
			run(clientSocket);
			clientSocket.close();
		} catch (IOException e) {
			System.err.println("Encountered an I/O exception; exiting");
			System.exit(-1);
		}
	}
	
	private static void usageErr() {
		System.err.println("usage: proxyd [-port portnum]");
		System.exit(1);
	}
	
	private static void run(ServerSocket clientSocket) throws IOException {
		DNSCacheThread cacheThread = new DNSCacheThread();
		cacheThread.start();
	
		while(true) {
			Socket connectionSocket = clientSocket.accept(); 
			
			RequestThread requestThread = new RequestThread(connectionSocket);
			requestThread.start();
		}
	}
	
	protected static void handleRequest(Socket connectionSocket) throws IOException {
		DataInputStream clientInStream = new DataInputStream(connectionSocket.getInputStream());
		ByteBuffer clientInBuffer = ByteBuffer.allocate(connectionSocket.getReceiveBufferSize());
		
		//read input from the client
		byte[] inBuffBytes = {};
		while(isBlank(new String(inBuffBytes))) {
			while(clientInStream.available() > 0) {
				clientInBuffer.put(clientInStream.readByte());
			}
			inBuffBytes = clientInBuffer.array();
		}
		String clientInStr = new String(inBuffBytes);
		
		String [] requests = clientInStr.split(REQUEST_END);
		
		for(String request : requests) {
			if(isBlank(request)) {
				break;
			}
			
			//stop if this is a CONNECT request - no support for that!
			if(request.contains("CONNECT")) {
				System.err.println("This proxy does not accept CONNECT requests, sorry!");
				break;
			}
			
			String[] lines = request.split(LINE_END);
			
			//do some minimal parsing!
			boolean connectFound = false;
			String line;
			for(int i = 0; i < lines.length; i++) {
				line = lines[i];
				
				//we'll want to close this connection once we've received the data
				if(line.equalsIgnoreCase("connection: keep-alive") ||
						line.equalsIgnoreCase("proxy-connection: keep-alive")) {
					lines[i] = "Connection: close";
					connectFound = true;
				}
			}
			
			StringBuffer sb = new StringBuffer();
			for(int i = 0; i < lines.length; i++) {
				sb.append(lines[i] + LINE_END);
			}
			if(!connectFound) {
				sb.append("Connection: close" + LINE_END);
			}
			request = sb.toString();
			
			request += REQUEST_END;
			byte[] requestBytes = request.getBytes();
			
			//get the IP address
			String hostname = getValue(request, "Host: ");

			int serverPort = DEFAULT_SERVER_PORT;

			//check if there's a port number specified
			if(hostname.contains(":")) {
				String[] hostParts = hostname.split(":");
				hostname = hostParts[0];
				serverPort = Integer.parseInt(hostParts[1]);
			}
			
			InetAddress ip = getInetAddress(hostname);
			
			//connect a new socket to the appropriate server
			Socket serverSocket = new Socket(ip, serverPort);
			
			//start the thread for reading responses from this server
			ResponseThread responseThread = new ResponseThread(serverSocket, connectionSocket);
			responseThread.start();
			
			DataOutputStream serverOutStream = new DataOutputStream(serverSocket.getOutputStream());
			
			//write the request to the server
			serverOutStream.write(requestBytes, 0, requestBytes.length);
			serverOutStream.flush();
		}
	}
	
	private static String getValue(String request, String header) {
		//get the value of the given header field
		int startIndex = request.indexOf(header) + header.length();
		int endIndex = request.indexOf(LINE_END, startIndex);
		
		return request.substring(startIndex, endIndex);
	}
	
	private static InetAddress getInetAddress(String hostname) throws UnknownHostException {
		//check the map for the hostname first
		if(ips.containsKey(hostname)) {
			InetAddress addr = ips.get(hostname);
			return addr;
		}
		//if the hostname isn't cached, find the associated IP (and cache it)
		InetAddress addr = InetAddress.getAllByName(hostname)[0];
		ips.put(hostname, addr);
		dnsTimes.put(new Date().getTime(), hostname);
		
		return addr;
	}
	
	protected static void handleDNS() {
		//remove mappings that were created >= 30 seconds ago
		long longestTime = new Date().getTime() - CACHE_TIME;
		
		Set<Long> times = dnsTimes.keySet();
		Iterator<Long> itr = times.iterator();

		Set<Long> removeTimes = new HashSet<Long>();
		//iterate over all times; keep track of the ones that are more than 30 seconds ago
		while(itr.hasNext()) {
			Long next = itr.next();
			if(next <= longestTime) {
				removeTimes.add(next);
			}
		}
		Iterator<Long> removeItr = removeTimes.iterator();
		//remove the ips associated with times that are more than 30 seconds ago
		while(removeItr.hasNext()) {			
			Long next = removeItr.next();
			//remove this instance from both removeTimes and ips
			ips.remove(dnsTimes.remove(next));
		}
	}
	
	protected static void handleResponse(Socket serverSocket, Socket clientSocket) throws IOException {
		InputStream serverInStream = serverSocket.getInputStream();
		ByteBuffer serverInBuffer = ByteBuffer.allocate(serverSocket.getReceiveBufferSize());
		
		byte[] responseBytes = {};
		//keep trying to read from the server until it sends something
		while(isBlank(new String(responseBytes))) {
			//read input from the server
			byte nextByte = (byte)serverInStream.read();
			while(nextByte != -1) {
				serverInBuffer.put(nextByte);
				nextByte = (byte)serverInStream.read();
			}
			responseBytes = serverInBuffer.array();
		}
		
		DataOutputStream clientOutStream = new DataOutputStream(clientSocket.getOutputStream());
		
		//write the response to the client
		clientOutStream.write(responseBytes, 0, responseBytes.length);
		clientOutStream.flush();
		
		clientOutStream.close();
	}
	
	private static boolean isBlank(String str) {
		// if there's nothing but white space, consider the string blank
		return str.trim().length() == 0;
	}
}