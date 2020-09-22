package org.limaxproject.demo.chatroom.android.client.sdk;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import limax.endpoint.Endpoint;
import limax.endpoint.EndpointConfig;
import limax.endpoint.LoginConfig;
import limax.endpoint.ViewChangedType;
import limax.endpoint.variant.TemporaryViewHandler;
import limax.endpoint.variant.Variant;
import limax.endpoint.variant.VariantView;
import limax.util.Trace;

public class MainActivity extends AppCompatActivity {

    public static final short pvid_chat = 100;

    private final LimaxClientNotify notify;
    private final Map<Long, String> namescache = new HashMap<>();
    private long currentTargetUser = -1;

    public MainActivity() {
        this.notify = new LimaxClientNotify(this);
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

        try {
            Endpoint.openEngine();
        } catch (Exception e) {
            Trace.fatal(" Endpoint.openEngine", e);
        }
        showLoginFrame();
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
        adapter.add(new ServerItemData("127.0.0.1", 10000));
        adapter.add(new ServerItemData("10.0.0.191", 10000));
        s.setAdapter(adapter);
    }

    void showLoginFrame() {

        setContentView(R.layout.activity_login);

        prepareServers();

        {
            Button b = (Button) findViewById(R.id.buttonLogin);
            b.setOnClickListener(v -> doLogin());
        }
    }

    private String getEditTextValue(int id) {
        return ((EditText) findViewById(id)).getText().toString().trim();
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

        final Spinner spinner = (Spinner) findViewById(R.id.spinnerServers);
        final ServerItemData itemdata = (ServerItemData) spinner
                .getSelectedItem();

        final EndpointConfig config = Endpoint.createEndpointConfigBuilder(itemdata.serverip, itemdata.serverport, LoginConfig.plainLogin(username, token, platflag))
                .executor(this::runOnUiThread).variantProviderIds(pvid_chat).build();
        Endpoint.start( config, notify);

        final Button btnLogin = (Button) findViewById(R.id.buttonLogin);
        btnLogin.setEnabled(false);
    }

    void showMainFrame() {
        setContentView(R.layout.activity_halls);

        final VariantView ciview = notify.getVariantManager().getSessionOrGlobalView("chatviews.CommonInfo");
        ciview.registerListener("halls", e-> showRoomList(e.getValue()));

        final VariantView uiview = notify.getVariantManager().getSessionOrGlobalView("chatviews.UserInfo");
        uiview.registerListener("name", e-> {
            final String name = e.getValue().getString("nickname");
            if (name.isEmpty())
                resetNickName();
            else
                showNickName(name);
        });
        uiview.registerListener("lasterror", e -> showMessage(getErrorString(e.getValue()
                        .getIntValue())));

        {
            final String name;
            {
                final StringBuilder sb = new StringBuilder();
                uiview.visitField("name", e-> sb.append(e.getString("nickname")));
                name = sb.toString();
            }
            showNickName(name);
        }

        {
            final ListView view = (ListView) findViewById(R.id.listviewRooms);
            view.setItemsCanFocus(false);
            view.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            view.setOnItemClickListener((parent, v, position, id) ->{
                final String name;
                {
                    final StringBuilder sb = new StringBuilder();
                    uiview.visitField("name", e-> sb.append(e.getString("nickname")));
                    name = sb.toString();
                }
                final Button btnJoin = (Button) findViewById(R.id.buttonJoin);
                btnJoin.setEnabled(!name.isEmpty());
                view.setItemChecked(position, true);
            });
            ciview.visitField("halls", this::showRoomList);
        }

        {
            final Button btnJoin = (Button) findViewById(R.id.buttonJoin);
            btnJoin.setOnClickListener(v -> {
                btnJoin.setClickable(false);
                final ListView view = (ListView) findViewById(R.id.listviewRooms);
                final int position = view.getCheckedItemPosition();
                if (ListView.INVALID_POSITION == position)
                    return;
               final  RoomItemData data = (RoomItemData) view
                        .getItemAtPosition(position);
                if (null == data)
                    return;
                final String name;
                {
                    final StringBuilder sb = new StringBuilder();
                    uiview.visitField("name", e-> sb.append(e.getString("nickname")));
                    name = sb.toString();
                }
                if (!name.isEmpty())
                    notify.sendMessage(uiview, "join=" + data.roomid);
            });
        }
        {
            final Button btnRename = (Button) findViewById(R.id.buttonRename);
            btnRename.setOnClickListener(e -> resetNickName());
        }
        {
            final Button btnLogout = (Button) findViewById(R.id.buttonLogout);
            btnLogout.setOnClickListener(v -> notify.closeLogin());
        }
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
        final VariantView view = notify.getVariantManager().getSessionOrGlobalView("chatviews.UserInfo");
        final String oldname;
        {
            final StringBuilder sb = new StringBuilder();
            view.visitField("name", e-> sb.append(e.getString("nickname")));
            oldname = sb.toString();
        }
        final EditText input = new EditText(this);
        input.setText(oldname);
        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setMessage("input nickname");
        dlgAlert.setTitle(R.string.app_name);
        dlgAlert.setView(input);
        dlgAlert.setPositiveButton("Ok", (dialog, which) -> {
                final String newname = input.getText().toString().trim();
                if (newname.isEmpty() || newname.equals(oldname))
                    runOnUiThread(this::resetNickName);
                else
                    notify.sendMessage(view,"nickname=" + newname);
            });
        dlgAlert.setNegativeButton("Cancel",
                (d,i)->{});
        dlgAlert.setCancelable(true);
        dlgAlert.create().show();
    }

    void connectAbort() {
        showMessage("connect abort!");
        final Button btnLogin = (Button) findViewById(R.id.buttonLogin);
        btnLogin.setEnabled(true);
    }

    void showMessage(String msg) {
        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setMessage(msg);
        dlgAlert.setTitle(R.string.app_name);
        dlgAlert.setPositiveButton("Ok", (d,i)->{} );
        dlgAlert.setCancelable(true);
        dlgAlert.create().show();
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

    private void showMembers() {
        final ListView view = new ListView(this);
        {
            final ArrayAdapter<MemberItemData> adapter = new ArrayAdapter<MemberItemData>(
                    this, android.R.layout.simple_list_item_single_choice);
            adapter.add(new MemberItemData(-1, "[all]"));
            final VariantView crview = notify.getChatRoomView();

            crview.visitField("names", var -> {
                for (Map.Entry<Variant, Variant> e : var.getMapValue().entrySet()) {
                    final long sid = e.getKey().getLongValue();
                    if (sid == notify.getSessionId())
                        continue;
                    adapter.add(new MemberItemData(sid, e.getValue().getString(
                            "nickname")));
                }
                view.setAdapter(adapter);
            });
        }

        final AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setMessage("select talk to :");
        dlgAlert.setTitle(R.string.app_name);
        dlgAlert.setView(view);
        dlgAlert.setCancelable(true);
        final AlertDialog dlg = dlgAlert.create();

        view.setOnItemClickListener((parent, v, position, id) -> {
            MemberItemData data = (MemberItemData) view
                    .getItemAtPosition(position);
            if (null != data) {
                currentTargetUser = data.sid;
                final TextView target = (TextView) findViewById(R.id.textViewTarget);
                target.setText(data.name);
                dlg.cancel();
            }
        });

        dlg.show();
    }

    void showChatRoom() {
        setContentView(R.layout.activity_chatroom);

        final VariantView view = notify.getChatRoomView();

        view.registerListener("info", e-> outputMessage("[enter room \""
                        + e.getValue().getString("name") + "]"));
        view.registerListener("names", e-> updateNamesCache(e.getSessionId(), e.getValue().getString("nickname")));
        view.registerListener("lastmessage", e-> {
            if (ViewChangedType.REPLACE == e.getType()
                    || ViewChangedType.TOUCH == e.getType())
                outputMessageToAll(e.getValue());
        });

        final VariantView uiview = notify.getVariantManager().getSessionOrGlobalView("chatviews.UserInfo");
        uiview.registerListener("recvedmessage", e-> {
            final Variant var = e.getValue();
            final long user = var.getLong("user");
            if (user == notify.getSessionId())
                outputMessage(var.getString("msg"));
            else
                showPrivateMessage(user, var.getString("msg"), true);
        });

        uiview.registerListener("sendedmessage", e-> {
            final Variant var = e.getValue();
            showPrivateMessage(var.getLong("user"),
                    var.getString("msg"), false);
        });

        {
            final Button btnSend = (Button) findViewById(R.id.buttonSend);
            btnSend.setOnClickListener(v -> {
                final EditText et = (EditText) findViewById(R.id.editTextInputMessage);
                final String msg = et.getText().toString().trim();
                if (msg.isEmpty())
                    return;
                et.setText("");
                if (msg.equalsIgnoreCase(".quit"))
                    notify.sendMessage(view, "leave=");
                else if (!msg.isEmpty())
                    notify.sendMessage(view, "message="
                            + currentTargetUser + "," + msg);
            });
        }

        {
            final TextView target = (TextView) findViewById(R.id.textViewTarget);
            target.setOnClickListener(v -> showMembers());
        }
    }

    void onChatRoomAttach(final long sessionid) {
        getCurrentFocus().post(() -> {
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
