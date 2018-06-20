package chat.chatclient;

import java.awt.GridLayout;
import java.awt.Window;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public class LoginFrame {

	public static final String platflag = "test";

	private final JDialog mainFrame;
	private final JTextField username = new JTextField("javatestaccount");
	private final JPasswordField password = new JPasswordField("123456");
	private final JTextField serverip = new JTextField("127.0.0.1");
	private final JTextField serverport = new JTextField("10000");
	private boolean doLogin = false;

	LoginFrame(Window owner) {
		mainFrame = new JDialog(owner, "login");
		mainFrame.setModal(true);
		mainFrame.setLocationRelativeTo(owner);

		JPanel mainpanel = new JPanel();
		mainFrame.getContentPane().add(mainpanel);
		mainpanel.setLayout(new GridLayout(2, 2));

		{
			JPanel labels = new JPanel();
			labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
			labels.add(new JLabel("username"));
			labels.add(new JLabel("password"));
			labels.add(new JLabel("platflag"));
			labels.add(new JLabel("serverip"));
			labels.add(new JLabel("serverport"));
			mainpanel.add(labels);
		}

		{
			JPanel texts = new JPanel();
			texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
			texts.add(username);
			texts.add(password);
			texts.add(new JLabel(platflag));
			texts.add(serverip);
			texts.add(serverport);
			mainpanel.add(texts);
		}

		mainpanel.add(new JPanel());

		{
			JPanel btns = new JPanel();
			btns.setLayout(new BoxLayout(btns, BoxLayout.X_AXIS));
			final JButton ok = new JButton("OK");
			ok.addActionListener(e -> startLogin());
			btns.add(ok);

			final JButton cancel = new JButton("Cancel");
			cancel.addActionListener(e -> mainFrame.setVisible(false));
			btns.add(cancel);

			mainpanel.add(btns);
		}

	}

	void show() {
		mainFrame.pack();
		mainFrame.setVisible(true);
	}

	private void startLogin() {
		mainFrame.setVisible(false);
		doLogin = true;
	}

	boolean isDoLogin() {
		return doLogin;
	}

	String getUsername() {
		return username.getText().trim();
	}

	String getPassword() {
		return new String(password.getPassword()).trim();
	}

	String getPlatflag() {
		return platflag;
	}

	String getServerIP() {
		return serverip.getText().trim();
	}

	int getServerPort() {
		return Integer.parseInt(serverport.getText().trim());
	}

}
