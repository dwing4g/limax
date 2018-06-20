package chat.chatclient;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Collection;
import java.util.Enumeration;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import limax.codec.CodecException;
import limax.endpoint.Endpoint;
import limax.endpoint.EndpointConfig;
import limax.endpoint.EndpointListener;
import limax.endpoint.EndpointManager;
import limax.endpoint.LoginConfig;
import limax.net.Config;
import limax.net.Manager;
import limax.net.SizePolicyException;
import limax.net.Transport;
import chat.chatclient.chatviews.ChatRoom;
import chat.chatclient.chatviews.CommonInfo;
import chat.chatclient.chatviews.UserInfo;
import chat.chatviews.ChatMessage;
import chat.chatviews.ErrorCodes;
import chat.chatviews.RoomChatHallInfo;
import chat.chatviews.ViewChatRoomInfo;

public class MainFrame {

	private static int providerId = 100;
	private static MainFrame instance;

	private EndpointManager endpointManager;

	private final JFrame mainFrame = new JFrame("chat client");
	private final JTree roomtree;

	private final JButton btnLogin = new JButton("login");
	private final JButton btnJoin = new JButton("join");
	private final JButton btnRename = new JButton("rename");
	private final JButton btnLogout = new JButton("logout");

	private ChatRoomFrame currentChatRoomFrame = null;

	public static MainFrame getInstance() {
		return instance;
	}

	MainFrame() {

		instance = this;

		JPanel mainpanel = new JPanel();
		mainpanel.setLayout(new BorderLayout(10, 10));
		mainFrame.getContentPane().add(mainpanel);
		mainFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				Endpoint.closeEngine(null);
			}
		});
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		{
			roomtree = new JTree(new DefaultMutableTreeNode());
			roomtree.setRootVisible(false);
			roomtree.setPreferredSize(new Dimension(300, 200));
			roomtree.addTreeSelectionListener(e -> onTreeSelectChange());
			JScrollPane sptree = new JScrollPane(roomtree);
			mainpanel.add(sptree, BorderLayout.CENTER);
		}

		{

			JPanel btns = new JPanel();
			btns.setLayout(new BoxLayout(btns, BoxLayout.Y_AXIS));

			btnLogin.addActionListener(e -> onLogin());
			btns.add(btnLogin);

			btnLogout.setEnabled(false);
			btnLogout.addActionListener(e -> onLogout());
			btns.add(btnLogout);

			btnJoin.setEnabled(false);
			btnJoin.addActionListener(e -> onJoin());
			btns.add(btnJoin);

			btnRename.setEnabled(false);
			btnRename.addActionListener(e -> onRename());
			btns.add(btnRename);

			final JLabel iconlabel = new JLabel();
			final ImageIcon imageiconsource = new ImageIcon(MainFrame.class.getClassLoader().getResource("limaxc.png"));
			final ImageIcon imageicon = new ImageIcon(
					imageiconsource.getImage().getScaledInstance(100, 61, Image.SCALE_DEFAULT));
			iconlabel.setIcon(imageicon);
			btns.add(iconlabel);

			mainpanel.add(btns, BorderLayout.EAST);
		}
	}

	JFrame getMainFrame() {
		return mainFrame;
	}

	EndpointManager getEndpointManager() {
		return endpointManager;
	}

	void show() {
		mainFrame.pack();
		mainFrame.setLocationRelativeTo(null);
		mainFrame.setVisible(true);
	}

	private void onLogout() {
		endpointManager.close();
	}

	private void onLogin() {
		final LoginFrame lf = new LoginFrame(mainFrame);
		lf.show();

		if (lf.isDoLogin()) {
			btnLogin.setEnabled(false);

			final EndpointListener listener = new EndpointListener() {

				@Override
				public void onAbort(Transport transport) throws Exception {
					btnLogin.setEnabled(true);

					JOptionPane.showMessageDialog(mainFrame, "connect to server failed!");
				}

				@Override
				public void onManagerInitialized(Manager manager, Config config) {
					endpointManager = (EndpointManager) manager;
				}

				@Override
				public void onManagerUninitialized(Manager manager) {
					endpointManager = null;
				}

				@Override
				public void onTransportAdded(Transport transport) throws Exception {
					CommonInfo.getInstance().registerListener("halls", e -> updateHalls(e.getValue()));
					UserInfo.getInstance().registerListener("name",
							e -> mainFrame.setTitle("chat client [" + ((UserInfo.name) e.getValue()).nickname + "]"));
					UserInfo.getInstance().registerListener("name", e -> {
						if (((UserInfo.name) e.getValue()).nickname.isEmpty())
							onUserNickName();
					});
					UserInfo.getInstance().registerListener("lasterror", e -> onRecvErrorCode((int) e.getValue()));
					UserInfo.getInstance().registerListener("recvedmessage",
							e -> onReceiveMessage((ChatMessage) e.getValue()));
					UserInfo.getInstance().registerListener("sendedmessage",
							e -> onSendMessage((ChatMessage) e.getValue()));
					btnRename.setEnabled(true);
					btnLogout.setEnabled(true);
				}

				@Override
				public void onTransportRemoved(Transport transport) throws Exception {
					btnLogin.setEnabled(true);
					btnLogout.setEnabled(false);
					btnJoin.setEnabled(false);
					btnRename.setEnabled(false);
					roomtree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode()));
				}

				@Override
				public void onSocketConnected() {
				}

				@Override
				public void onKeyExchangeDone() {
				}

				@Override
				public void onKeepAlived(int ms) {
				}

				@Override
				public void onErrorOccured(int source, int code, Throwable e) {
					if (null != e)
						e.printStackTrace();
					JOptionPane.showMessageDialog(mainFrame,
							"onErrorOccured = " + source + " code = " + code + " e  = " + e);
				}
			};
			final EndpointConfig config = Endpoint
					.createEndpointConfigBuilder(lf.getServerIP(), lf.getServerPort(),
							LoginConfig.plainLogin(lf.getUsername(), lf.getPassword(), lf.getPlatflag()))
					.staticViewClasses(chat.chatclient.chatviews.ViewManager.createInstance(providerId))
					.executor(r -> SwingUtilities.invokeLater(r)).build();
			Endpoint.start(config, listener);
		}
	}

	static private interface NodeObject {
		boolean isRoomNode();

		long getId();
	}

	static private class HallNodeObject implements NodeObject {
		final String name;
		final long hallid;

		HallNodeObject(String name, long hallid) {
			this.name = name;
			this.hallid = hallid;
		}

		@Override
		public String toString() {
			return name + "(" + hallid + ")";
		}

		@Override
		public boolean isRoomNode() {
			return false;
		}

		@Override
		public long getId() {
			return hallid;
		}
	}

	static private class RoomNodeObject implements NodeObject {
		final String name;
		final long roomid;

		RoomNodeObject(String name, long roomid) {
			this.name = name;
			this.roomid = roomid;
		}

		@Override
		public String toString() {
			return name + "(" + roomid + ")";
		}

		@Override
		public boolean isRoomNode() {
			return true;
		}

		@Override
		public long getId() {
			return roomid;
		}
	}

	private void updateHalls(Object value) {
		@SuppressWarnings("unchecked")
		final Collection<RoomChatHallInfo> hallinfos = (Collection<RoomChatHallInfo>) value;

		final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
		final DefaultTreeModel treemodel = new DefaultTreeModel(root);
		for (RoomChatHallInfo hallinfo : hallinfos) {
			final HallNodeObject hno = new HallNodeObject(hallinfo.name, hallinfo.hallid);
			final DefaultMutableTreeNode hallnode = new DefaultMutableTreeNode(hno);
			treemodel.insertNodeInto(hallnode, root, root.getChildCount());

			for (ViewChatRoomInfo roominfo : hallinfo.rooms) {
				final RoomNodeObject rno = new RoomNodeObject(roominfo.name, roominfo.roomid);
				final DefaultMutableTreeNode roomnode = new DefaultMutableTreeNode(rno);
				treemodel.insertNodeInto(roomnode, hallnode, hallnode.getChildCount());
			}
		}
		roomtree.setModel(treemodel);
		final TreePath rootpath = new TreePath(root);
		for (Enumeration<?> e = root.children(); e.hasMoreElements();)
			roomtree.expandPath(rootpath.pathByAddingChild(e.nextElement()));
	}

	private void onTreeSelectChange() {
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) roomtree.getLastSelectedPathComponent();
		if (null == node) {
			btnJoin.setEnabled(false);
		} else {
			NodeObject no = (NodeObject) node.getUserObject();
			UserInfo.getInstance().visitName(
					v -> btnJoin.setEnabled(no.isRoomNode() && !v.nickname.isEmpty() && null == currentChatRoomFrame));
		}
	}

	private void onUserNickName() {
		while (true) {
			final String name = JOptionPane.showInputDialog(mainFrame, "nickname");
			if (null == name) {
				endpointManager.close();
				break;
			}
			if (!name.isEmpty()) {
				sendUserInfoMessage("nickname=" + name.trim());
				break;
			}
		}
	}

	private void onRename() {
		while (true) {
			final String name = JOptionPane.showInputDialog(mainFrame, "nickname");
			if (null == name)
				break;
			if (name.isEmpty())
				continue;
			final String nickname = name.trim();

			final VariableSaver<Boolean> same = new VariableSaver<>();

			UserInfo.getInstance().visitName(n -> same.value = n.nickname.equalsIgnoreCase(nickname));
			if (same.value)
				JOptionPane.showMessageDialog(mainFrame, "error name unmodify");
			else
				sendUserInfoMessage("nickname=" + nickname);
			break;
		}
	}

	private void onRecvErrorCode(int code) {
		final String codemsg;
		switch (code) {
		case ErrorCodes.EC_NAME_EXISTING:
			codemsg = "name already existing";
			break;
		case ErrorCodes.EC_NAME_UNMODIFIED:
			codemsg = "name unmodify";
			break;
		case ErrorCodes.EC_BAD_ROOM_ID:
			codemsg = "bad room id";
			break;
		default:
			codemsg = "[unknow code value]";
			break;
		}
		JOptionPane.showMessageDialog(mainFrame, "error :  code = " + code + " msg = " + codemsg);
	}

	private void onJoin() {
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) roomtree.getLastSelectedPathComponent();
		NodeObject no = (NodeObject) node.getUserObject();
		if (!no.isRoomNode())
			return;
		sendUserInfoMessage("join=" + no.getId());
		btnJoin.setEnabled(false);
	}

	private void sendUserInfoMessage(String msg) {
		try {
			UserInfo.getInstance().sendMessage(msg);
		} catch (InstantiationException | ClassCastException | SizePolicyException | CodecException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(mainFrame, "sendUserInfoMessage exception " + e);
		}
	}

	class VariableSaver<E> {
		public E value = null;
	}

	public ChatRoomFrame showChatRoom(ChatRoom view) {
		currentChatRoomFrame = new ChatRoomFrame(this, view);
		currentChatRoomFrame.show();
		return currentChatRoomFrame;
	}

	private void checkEnableJoinButtonWhileCloseView() {
		boolean enabled = false;
		DefaultMutableTreeNode node = (DefaultMutableTreeNode) roomtree.getLastSelectedPathComponent();
		if (null != node) {
			NodeObject no = (NodeObject) node.getUserObject();
			if (null != no)
				enabled = no.isRoomNode();
		}
		btnJoin.setEnabled(enabled);
	}

	void closeChatRoom() {
		currentChatRoomFrame = null;
		checkEnableJoinButtonWhileCloseView();
	}

	private void onReceiveMessage(ChatMessage msg) {
		if (msg.user == endpointManager.getSessionId())
			currentChatRoomFrame.showMessage(msg.msg);
		else
			currentChatRoomFrame.showPrivateMessage(msg.user, msg.msg, true);
	}

	private void onSendMessage(ChatMessage msg) {
		currentChatRoomFrame.showPrivateMessage(msg.user, msg.msg, false);
	}

}
