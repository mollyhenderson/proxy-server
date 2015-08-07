import java.io.IOException;
import java.net.Socket;

public class ResponseThread extends Thread {

	private Socket serverSocket;
	private Socket clientSocket;

	public ResponseThread(Socket serverSocket, Socket clientSocket) {
		this.serverSocket = serverSocket;
		this.clientSocket = clientSocket;
	}
	
	public void run() {
		try {
			proxyd.handleResponse(serverSocket, clientSocket);
		} catch (IOException e) {
			return;
		}
	}
}
