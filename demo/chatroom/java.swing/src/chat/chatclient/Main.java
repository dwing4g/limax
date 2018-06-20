package chat.chatclient;

import javax.swing.SwingUtilities;

import limax.endpoint.Endpoint;
import limax.util.Trace;

public class Main  {

	public static void main(String[] args) throws Exception {
		Trace.openNew();
		Endpoint.openEngine();
		SwingUtilities.invokeLater(() -> new MainFrame().show());
	}
}
