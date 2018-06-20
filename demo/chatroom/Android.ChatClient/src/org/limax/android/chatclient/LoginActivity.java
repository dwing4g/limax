package org.limax.android.chatclient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.limax.android.chatclient.ndk.LimaxInterface;

import limax.endpoint.ViewChangedType;
import limax.endpoint.variant.Variant;
import limax.util.Resource;
import limax.util.Trace;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

public class LoginActivity extends Activity {

	private Resource notifieMainFrame = null;
	private Resource notifieChatRoom = null;
	private long currentTargetUser = -1;
	private Runnable currentBackTask = null;
	@SuppressLint("UseSparseArrays")
	private Map<Long, String> namescache = new HashMap<Long, String>();

	public LoginActivity() {
		LimaxInterface.initializeLimaxLib();
	}

	private static class ServerItemData {
		final String serverip;
		final int serverport;

		public ServerItemData(String sip, int sport) {
			serverip = sip;
			serverport = sport;
		}

		@Override
		public String toString() {
			return serverip + ":" + serverport;
		}
	}

	private void prepareServers() {
		Spinner s = (Spinner) findViewById(R.id.spinnerServers);
		ArrayAdapter<ServerItemData> adapter = new ArrayAdapter<ServerItemData>(
				this, android.R.layout.simple_spinner_item);
		adapter.add(new ServerItemData("172.16.0.45", 10000));
		adapter.add(new ServerItemData("192.168.1.101", 10000));
		adapter.add(new ServerItemData("192.168.1.102", 10000));
		adapter.add(new ServerItemData("192.168.1.103", 10000));
		adapter.add(new ServerItemData("192.168.1.104", 10000));
		s.setAdapter(adapter);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Trace.open(new Trace.Destination() {
			@Override
			public void printTo(String msg, Throwable e) {
				Log.wtf("chatclient", msg, e);
			}
		}, Trace.INFO);

		showLoginFrame();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (event.getRepeatCount() == 10 && null != currentBackTask)
				currentBackTask.run();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	void showLoginFrame() {
		if (null != notifieMainFrame) {
			notifieMainFrame.close();
			notifieMainFrame = null;
		}
		if (null != notifieChatRoom) {
			notifieChatRoom.close();
			notifieChatRoom = null;
		}

		setContentView(R.layout.activity_login);
		currentBackTask = null;

		prepareServers();

		{
			Button b = (Button) findViewById(R.id.buttonLogin);
			b.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					doLogin();
				}
			});
		}
	}

	private String getEditTextValue(int id) {
		return ((EditText) findViewById(id)).getText().toString().trim();
	}

	void showMessage(String msg) {
		AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
		dlgAlert.setMessage(msg);
		dlgAlert.setTitle(R.string.app_name);
		dlgAlert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
			}
		});
		dlgAlert.setCancelable(true);
		dlgAlert.create().show();
	}

	private void doLogin() {

		final String username = getEditTextValue(R.id.editTextUsername);
		final String token = getEditTextValue(R.id.editTextPassword);
		final String platflag = getEditTextValue(R.id.editTextPlatFlag);

		if (username.isEmpty()) {
			showMessage("username is empty!");
			return;
		}
		if (token.isEmpty()) {
			showMessage("password is empty!");
			return;
		}
		if (platflag.isEmpty()) {
			showMessage("platflag is empty!");
			return;
		}

		final boolean usndk = ((Switch) findViewById(R.id.switchUseNDK))
				.isChecked();

		final Spinner spinner = (Spinner) findViewById(R.id.spinnerServers);
		final ServerItemData itemdata = (ServerItemData) spinner
				.getSelectedItem();

		LimaxEngineNotifyManager.getInstance().setLoginActivity(this);

		LimaxEngine le = usndk ? LimaxEngineManager.createNdkEngin()
				: LimaxEngineManager.createJavaEngin();
		le.startLogin(username, token, platflag, itemdata.serverip,
				itemdata.serverport, new Executor() {

					@Override
					public void execute(Runnable command) {
						runOnUiThread(command);
					}
				});

		final Button btnLogin = (Button) findViewById(R.id.buttonLogin);
		btnLogin.setEnabled(false);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.login, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	void connectAbort() {
		showMessage("connect abort!");
		final Button btnLogin = (Button) findViewById(R.id.buttonLogin);
		btnLogin.setEnabled(true);
	}

	private static String getErrorString(int code) {
		switch (code) {
		case 2:
			return "name already existing";
		case 1:
			return "name unmodify";
		case 11:
			return "bad room id";
		case 12:
			return "bad args";
		case 13:
			return "bad hall name";
		case 14:
			return "bad room name";
		default:
			return "[unknow code value]";
		}
	}

	void showMainFrame() {

		if (null != notifieChatRoom) {
			notifieChatRoom.close();
			notifieChatRoom = null;
		}

		setContentView(R.layout.activity_halls);
		notifieMainFrame = Resource.createRoot();

		currentBackTask = new Runnable() {
			@Override
			public void run() {
				LimaxEngineManager.getCurrentEngine().closeLogin();
			}
		};

		final LimaxEngine le = LimaxEngineManager.getCurrentEngine();
		Resource.create(notifieMainFrame, le.registerNotify(
				"chatviews.CommonInfo", "halls", new LimaxFieldNotify() {
					@Override
					public void onFieldNotify(LimaxFieldArgs args) {
						showRoomList(args.getValue());
					}
				}));
		Resource.create(notifieMainFrame, le.registerNotify(
				"chatviews.UserInfo", "name", new LimaxFieldNotify() {
					@Override
					public void onFieldNotify(LimaxFieldArgs args) {
						String name = args.getValue().getString("nickname");
						if (name.isEmpty())
							resetNickName();
						else
							showNickName(name);
					}
				}));
		Resource.create(notifieMainFrame, le.registerNotify(
				"chatviews.UserInfo", "lasterror", new LimaxFieldNotify() {
					@Override
					public void onFieldNotify(LimaxFieldArgs args) {
						showMessage(getErrorString(args.getValue()
								.getIntValue()));
					}
				}));

		showNickName(le.getFieldValue("chatviews.UserInfo", "name").getString(
				"nickname"));
		{
			final ListView view = (ListView) findViewById(R.id.listviewRooms);
			view.setItemsCanFocus(false);
			view.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
			view.setOnItemClickListener(new ListView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View v,
						int position, long id) {
					final String name = le.getFieldValue("chatviews.UserInfo",
							"name").getString("nickname");
					final Button btnJoin = (Button) findViewById(R.id.buttonJoin);
					btnJoin.setEnabled(!name.isEmpty());
					view.setItemChecked(position, true);
				}
			});
			showRoomList(le.getFieldValue("chatviews.CommonInfo", "halls"));
		}

		{
			final Button btnJoin = (Button) findViewById(R.id.buttonJoin);
			btnJoin.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					btnJoin.setClickable(false);
					final ListView view = (ListView) findViewById(R.id.listviewRooms);
					final int position = view.getCheckedItemPosition();
					if (ListView.INVALID_POSITION == position)
						return;
					RoomItemData data = (RoomItemData) view
							.getItemAtPosition(position);
					if (null == data)
						return;
					final String name = le.getFieldValue("chatviews.UserInfo",
							"name").getString("nickname");
					if (!name.isEmpty())
						le.sendMessage("chatviews.UserInfo", "join="
								+ data.roomid);
				}
			});
		}
		{
			final Button btnRename = (Button) findViewById(R.id.buttonRename);
			btnRename.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					resetNickName();
				}
			});
		}
		{
			final Button btnLogout = (Button) findViewById(R.id.buttonLogout);
			btnLogout.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					le.closeLogin();
				}
			});
		}
	}

	private static class RoomItemData {
		final String hallname;
		final String roomname;
		final long roomid;

		RoomItemData(String hn, String rn, long rid) {
			hallname = hn;
			roomname = rn;
			roomid = rid;
		}

		@Override
		public String toString() {
			return hallname + " | " + roomname;
		}
	}

	private void showRoomList(Variant var) {
		ListView view = (ListView) findViewById(R.id.listviewRooms);
		ArrayAdapter<RoomItemData> adapter = new ArrayAdapter<RoomItemData>(
				this, android.R.layout.simple_list_item_single_choice);

		for (Variant hv : var.getCollectionValue()) {
			final String hallname = hv.getString("name");
			final Variant rs = hv.getVariant("rooms");

			for (Variant rv : rs.getCollectionValue()) {
				final String roomname = rv.getString("name");
				final long rid = rv.getLong("roomid");
				adapter.add(new RoomItemData(hallname, roomname, rid));
			}
		}
		view.setAdapter(adapter);
	}

	private void showNickName(String var) {
		TextView tv = (TextView) findViewById(R.id.textViewInfo);
		tv.setText(var);
	}

	private void resetNickName() {

		final String oldname = LimaxEngineManager.getCurrentEngine()
				.getFieldValue("chatviews.UserInfo", "name").getStringValue();
		final EditText input = new EditText(this);
		input.setText(oldname);
		AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
		dlgAlert.setMessage("input nickname");
		dlgAlert.setTitle(R.string.app_name);
		dlgAlert.setView(input);
		dlgAlert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				final String newname = input.getText().toString().trim();
				if (newname.isEmpty() || newname.equals(oldname))
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							resetNickName();
						}
					});
				else
					LimaxEngineManager.getCurrentEngine().sendMessage(
							"chatviews.UserInfo", "nickname=" + newname);
			}
		});
		dlgAlert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				});
		dlgAlert.setCancelable(true);
		dlgAlert.create().show();
	}

	void showChatRoom() {
		if (null != notifieMainFrame) {
			notifieMainFrame.close();
			notifieMainFrame = null;
		}
		setContentView(R.layout.activity_chatroom);
		notifieChatRoom = Resource.createRoot();
		namescache.clear();

		final LimaxEngine le = LimaxEngineManager.getCurrentEngine();

		currentBackTask = new Runnable() {
			@Override
			public void run() {
				le.sendMessage("chatviews.ChatRoom", "leave=");
			}
		};

		Resource.create(notifieChatRoom, le.registerNotify(
				"chatviews.ChatRoom", "info", new LimaxFieldNotify() {
					@Override
					public void onFieldNotify(LimaxFieldArgs args) {
						if (Trace.isInfoEnabled())
							Trace.info(args.toString());
						outputMessage("[enter room \""
								+ args.getValue().getString("name") + "]");
					}
				}));
		Resource.create(notifieChatRoom, le.registerNotify(
				"chatviews.ChatRoom", "names", new LimaxFieldNotify() {
					@Override
					public void onFieldNotify(LimaxFieldArgs args) {
						if (Trace.isInfoEnabled())
							Trace.info(args.toString());
						updateNamesCache(args.getSessionId(), args.getValue().getString("nickname"));
					}
				}));
		Resource.create(notifieChatRoom, le.registerNotify(
				"chatviews.ChatRoom", "lastmessage", new LimaxFieldNotify() {
					@Override
					public void onFieldNotify(LimaxFieldArgs args) {
						if (ViewChangedType.REPLACE == args.getType()
								|| ViewChangedType.TOUCH == args.getType())
							outputMessageToAll(args.getValue());
					}
				}));

		Resource.create(notifieChatRoom, le.registerNotify(
				"chatviews.UserInfo", "recvedmessage", new LimaxFieldNotify() {
					@Override
					public void onFieldNotify(LimaxFieldArgs args) {
						final Variant var = args.getValue();
						final long user = var.getLong("user");
						if (user == le.getSessionId())
							outputMessage(var.getString("msg"));
						else
							showPrivateMessage(user, var.getString("msg"), true);
					}
				}));
		Resource.create(notifieChatRoom, le.registerNotify(
				"chatviews.UserInfo", "sendedmessage", new LimaxFieldNotify() {
					@Override
					public void onFieldNotify(LimaxFieldArgs args) {
						final Variant var = args.getValue();
						showPrivateMessage(var.getLong("user"),
								var.getString("msg"), false);
					}
				}));

		{
			final Button btnSend = (Button) findViewById(R.id.buttonSend);
			btnSend.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					final EditText et = (EditText) findViewById(R.id.editTextInputMessage);
					final String msg = et.getText().toString().trim();
					if (msg.isEmpty())
						return;
					et.setText("");
					if (msg.equalsIgnoreCase(".quit"))
						le.sendMessage("chatviews.ChatRoom", "leave=");
					else if (!msg.isEmpty())
						le.sendMessage("chatviews.ChatRoom", "message="
								+ currentTargetUser + "," + msg);
				}
			});
		}

		{
			final TextView target = (TextView) findViewById(R.id.textViewTarget);
			target.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ShowMembers();
				}
			});
		}
	}

	private void outputMessage(String msg) {
		TextView view = (TextView) findViewById(R.id.textViewMessages);
		view.setText(view.getText() + " \r\n" + msg);
	}

	private void updateNamesCache(long sessionid, String newname) {
		final String oldname = namescache.put(sessionid, newname);
		if (null != oldname)
			outputMessage("[\"" + oldname + "\" changed name to \"" + newname
					+ "\"]");
	}

	private String getMemberName(long sessionid) {
		final String name = namescache.get(sessionid);
		if (null == name)
			return "";
		else
			return name;
	}

	private void outputMessageToAll(Variant var) {
		final long sessionid = var.getLong("user");
		outputMessage(getMemberName(sessionid) + " to all : "
				+ var.getString("msg"));
	}

	private void showPrivateMessage(long sessionid, String msg, boolean recved) {
		final StringBuilder sb = new StringBuilder();
		if (!recved)
			sb.append("you to ");
		sb.append(getMemberName(sessionid));
		if (recved)
			sb.append(" to you");
		sb.append(" : ").append(msg);
		outputMessage(sb.toString());
	}

	private static class MemberItemData {
		final long sid;
		final String name;

		MemberItemData(long sid, String name) {
			this.sid = sid;
			this.name = name;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	private void ShowMembers() {
		final ListView view = new ListView(this);
		{
			final ArrayAdapter<MemberItemData> adapter = new ArrayAdapter<MemberItemData>(
					this, android.R.layout.simple_list_item_single_choice);
			adapter.add(new MemberItemData(-1, "[all]"));
			final LimaxEngine le = LimaxEngineManager.getCurrentEngine();
			final Variant var = le.getFieldValue("chatviews.ChatRoom", "names");
			for (Map.Entry<Variant, Variant> e : var.getMapValue().entrySet()) {
				final long sid = e.getKey().getLongValue();
				if (sid == le.getSessionId())
					continue;
				adapter.add(new MemberItemData(sid, e.getValue().getString(
						"nickname")));
			}
			view.setAdapter(adapter);
		}

		final AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
		dlgAlert.setMessage("select talk to :");
		dlgAlert.setTitle(R.string.app_name);
		dlgAlert.setView(view);
		dlgAlert.setCancelable(true);
		final AlertDialog dlg = dlgAlert.create();

		view.setOnItemClickListener(new ListView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View v,
					int position, long id) {
				MemberItemData data = (MemberItemData) view
						.getItemAtPosition(position);
				if (null != data) {
					currentTargetUser = data.sid;
					final TextView target = (TextView) findViewById(R.id.textViewTarget);
					target.setText(data.name);
					dlg.cancel();
				}
			}
		});

		dlg.show();
	}

	void onChatRoomAttach(final long sessionid) {
		getCurrentFocus().post(new Runnable() {
			@Override
			public void run() {
				final String name = namescache.get(sessionid);
				if (null == name)
					getCurrentFocus().post(new Runnable() {
						@Override
						public void run() {
							onChatRoomAttach(sessionid);
						}
					});
				else
					outputMessage("[" + name + " enter chat room]");
			}
		});
	}

	void onChatRoomDetach(long sessionid, int reason) {
		if (reason >= 0)
			outputMessage("[" + getMemberName(sessionid) + " leave chat room]");
		else
			outputMessage("[" + getMemberName(sessionid) + " disconnect]");
		namescache.remove(sessionid);
	}
}
