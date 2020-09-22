package chat.chatclient;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import limax.codec.CodecException;
import limax.endpoint.ViewChangedType;
import limax.net.SizePolicyException;
import chat.chatclient.chatviews.ChatRoom;
import chat.chatclient.chatviews.UserInfo;
import chat.chatviews.ChatMessage;

public class ChatRoomFrame {

	private final ChatRoom view;
	private final MainFrame mainClass;

	private final JDialog mainFrame;
	private final JTextArea messages = new JTextArea();
	private final JScrollPane spmsgs = new JScrollPane();
	private final JList<MemData> members = new JList<>();
	private final DefaultListModel<MemData> memmodel = new DefaultListModel<>();
	private final JLabel msgto = new JLabel();
	private final JTextField msg = new JTextField();
	private final JButton send = new JButton("send");

	private static class MemData {
		final String name;
		final long sessionid;

		MemData(String name, long sessionid) {
			this.name = name;
			this.sessionid = sessionid;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	ChatRoomFrame(MainFrame mainClass, ChatRoom view) {

		this.mainClass = mainClass;
		this.view = view;
		mainFrame = new JDialog(mainClass.getMainFrame());
		mainFrame.setModal(false);
		mainFrame.setLocationRelativeTo(mainClass.getMainFrame());

		mainFrame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				sendChatRoomMessage("leave=");
			}
		});
		view.visitInfo(info -> mainFrame.setTitle("chat room : " + info.name));

		final JPanel mainpanel = new JPanel();
		mainpanel.setLayout(new BorderLayout(10, 10));
		mainFrame.getContentPane().add(mainpanel);

		messages.setEditable(false);
		spmsgs.setViewportView(messages);
		mainpanel.add(spmsgs, BorderLayout.CENTER);

		{
			final JScrollPane spmems = new JScrollPane(members);
			members.setFixedCellWidth(200);
			members.setFixedCellHeight(20);
			memmodel.addElement(new MemData("[all]", -1L));
			members.setModel(memmodel);
			members.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			members.setSelectedIndex(0);
			mainpanel.add(spmems, BorderLayout.EAST);

			members.addListSelectionListener(e -> {
				MemData md = members.getSelectedValue();
				if (null != md)
					msgto.setText(md.name);
			});
		}

		{
			final JPanel sendpanel = new JPanel();
			sendpanel.setLayout(new BorderLayout(10, 10));

			msgto.setText("[all]");
			sendpanel.add(msgto, BorderLayout.WEST);
			sendpanel.add(msg, BorderLayout.CENTER);
			sendpanel.add(send, BorderLayout.EAST);

			send.setDefaultCapable(true);
			send.addActionListener(e -> {
				sendMessage(members.getSelectedValue().sessionid, msg.getText().trim());
				msg.setText("");
			});

			mainpanel.add(sendpanel, BorderLayout.SOUTH);
		}

		mainFrame.setSize(new Dimension(500, 500));

		view.visitNames(m -> makeMembers(m));
		view.registerListener("names", e -> updateName(e.getSessionId(), ((UserInfo.name) e.getValue()).nickname));
		view.registerListener("lastmessage", e -> {
			if (ViewChangedType.REPLACE == e.getType() || ViewChangedType.TOUCH == e.getType()) {
				ChatMessage msg = (ChatMessage) e.getValue();
				showMessageToAll(msg.user, msg.msg);
			}
		});
	}

	void show() {
		mainFrame.setVisible(true);
	}

	public void onViewClose() {
		mainClass.closeChatRoom();
		mainFrame.setVisible(false);
	}

	private static class GetString {
		String value = null;
	}

	public void onMemberAttach(long sessionid) {
		GetString gs = new GetString();
		view.visitNames(v -> {
			UserInfo.name name = v.get(sessionid);
			if (null != gs.value)
				gs.value = name.nickname;
		});
		if (null == gs.value)
			SwingUtilities.invokeLater(() -> onMemberAttach(sessionid));
		else
			showMessage("[user \"" + gs.value + "\" enter room]");
	}

	public void onMemberDetach(long sessionid, int code) {
		final String msg = code >= 0 ? "leave room" : "disconnected";
		view.visitNames(map -> {
			final String name = map.get(sessionid).nickname;
			if (null != name)
				showMessage("[user \"" + name + "\" " + msg + "]");
		});
		Collections.list(memmodel.elements()).stream().filter(d -> d.sessionid == sessionid)
				.forEach(d -> memmodel.removeElement(d));
		if (null == members.getSelectedValue()) {
			final int count = memmodel.getSize();
			for (int i = 0; i < count; i++) {
				if (-1L == memmodel.elementAt(i).sessionid) {
					members.setSelectedIndex(i);
					break;
				}
			}
		}
	}

	private void makeMembers(Map<Long, chat.chatclient.chatviews.UserInfo.name> mems) {
		final Set<Long> newkeys = new HashSet<>(mems.keySet());
		newkeys.remove(mainClass.getEndpointManager().getSessionId());
		final ArrayList<MemData> mdlist = Collections.list(memmodel.elements());
		mdlist.stream().filter(d -> -1L != d.sessionid && !mems.containsKey(d.sessionid))
				.forEach(d -> memmodel.removeElement(d));
		mdlist.stream().filter(d -> mems.containsKey(d.sessionid)).forEach(d -> newkeys.remove(d.sessionid));
		newkeys.remove(mainClass.getEndpointManager().getSessionId());
		newkeys.stream().map(k -> new MemData(mems.get(k).nickname, k))
				.forEach(d -> memmodel.insertElementAt(d, memmodel.getSize() - 1));
	}

	private void updateName(long sessionid, String name) {
		if (mainClass.getEndpointManager().getSessionId() == sessionid)
			return;
		Collection<MemData> cs = Collections.list(memmodel.elements()).stream().filter(d -> d.sessionid == sessionid)
				.collect(Collectors.toList());
		String oldname = null;
		if (!cs.isEmpty()) {
			cs.forEach(d -> memmodel.removeElement(d));
			oldname = cs.iterator().next().name;
		}
		memmodel.insertElementAt(new MemData(name, sessionid), memmodel.getSize() - 1);
		if (null != oldname)
			showMessage("[user '" + oldname + "' change name to '" + name + "']");
	}

	private void showMessageToAll(long sessionid, String msg) {
		final StringBuilder sb = new StringBuilder();
		view.visitNames(m -> sb.append(m.get(sessionid).nickname));
		sb.append(" to all : ").append(msg);
		showMessage(sb.toString());
	}

	public void showPrivateMessage(long sessionid, String msg, boolean recved) {
		final StringBuilder sb = new StringBuilder();
		if (!recved)
			sb.append("you to ");
		view.visitNames(m -> sb.append(m.get(sessionid).nickname));
		if (recved)
			sb.append(" to you");
		sb.append(" : ").append(msg);
		showMessage(sb.toString());
	}

	public void showMessage(String msg) {
		messages.append(msg);
		messages.append("\r\n");

		final JScrollBar sb = spmsgs.getVerticalScrollBar();
		sb.setValue(sb.getMaximum() - 1);
	}

	private void sendMessage(long to, String msg) {
		if (msg.equalsIgnoreCase(".quit"))
			sendChatRoomMessage("leave=");
		else if (!msg.isEmpty())
			sendChatRoomMessage("message=" + to + "," + msg);
	}

	private void sendChatRoomMessage(String msg) {
		try {
			view.sendMessage(msg);
		} catch (InstantiationException | ClassCastException | SizePolicyException | CodecException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(mainFrame, "sendChatRoomMessage exception " + e);
		}
	}

}
