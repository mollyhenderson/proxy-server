import java.io.IOException;
import java.net.Socket;

public class RequestThread extends Thread {

	private Socket connectionSocket;

	public RequestThread(Socket connectionSocket) {
		this.connectionSocket = connectionSocket;
	}
	
	public void run() {
		try {
			proxyd.handleRequest(connectionSocket);
		} catch (IOException e) {
			return;
		}
	}
}
