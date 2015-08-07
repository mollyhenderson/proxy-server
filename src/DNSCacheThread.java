
public class DNSCacheThread extends Thread {

	public void run() {
		while(true) {
			proxyd.handleDNS();
			//sleep so that this thread doesn't hog all the resources
			try {
				sleep(100);
			} catch (InterruptedException e) {
				return;
			}
		}
	}
}
