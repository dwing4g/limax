using chat.chatviews;
using limax.endpoint;
using limax.net;
using limax.util;
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace chatroom
{
    public partial class MainForm : Form, EndpointListener
    {
        private static readonly int providerId = 100;
        private EndpointManager endpointManager = null;

        private static MainForm instance;

        public static MainForm getInstance()
        {
            return instance;
        }
        public static EndpointManager getEndpointManager()
        {
            return instance.endpointManager;
        }

        public MainForm()
        {
            InitializeComponent();

            instance = this;

            Endpoint.openEngine();
#if DEBUG
            Trace.open((str) => { System.Diagnostics.Debug.WriteLine(str); }, Trace.Level.Info);
#endif
            buttonJoin.Enabled = false;
        }


        private TaskScheduler scheduler = null;

        internal Executor getExecutor()
        {
            return a => { Utils.runOnUiThread(a); Task t = new Task(Utils.uiThreadSchedule); t.Start(scheduler); };
        }

        private void buttonLogin_Click(object sender, EventArgs e)
        {
            Login dialog = new Login();
            if (DialogResult.OK == dialog.ShowDialog())
            {
                if (null == scheduler)
                    scheduler = TaskScheduler.FromCurrentSynchronizationContext();
                var config = Endpoint.createEndpointConfigBuilder(dialog.getServerIP(), dialog.getServerPort(), LoginConfig.plainLogin(dialog.getUsername(), dialog.getToken(), dialog.getPlatFlag()))
                    .staticViewClasses(chat.chatclient.chatviews.ViewManager.createInstance(providerId))
                    .executor(getExecutor())
                    .build();
                Endpoint.start(config, this);
                buttonLogin.Enabled = false;
            }
        }

        public void onSocketConnected()
        {
            if (Trace.isInfoEnabled())
                Trace.info("onSocketConnected");
        }
        public void onKeyExchangeDone()
        {
            if (Trace.isInfoEnabled())
                Trace.info("onKeyExchangeDone");
        }
        public void onKeepAlived(int ms)
        {
            if (Trace.isInfoEnabled())
                Trace.info("onKeepAlived " + ms);
        }
        public void onErrorOccured(int source, int code, System.Exception e)
        {
            if (Trace.isInfoEnabled())
                Trace.info("onErrorOccured " + source + " " + code + " e = " + e);
            MessageBox.Show("error : source = " + source + " code = " + code + " e = " + e);
        }
        public void onAbort(Transport transport)
        {
            if (Trace.isInfoEnabled())
                Trace.info("onAbort");
            buttonLogin.Enabled = true;
        }
        public void onManagerInitialized(Manager manager, Config config)
        {
            endpointManager = (EndpointManager)manager;
            if (Trace.isInfoEnabled())
                Trace.info("onManagerInitialized");
        }
        public void onManagerUninitialized(Manager manager)
        {
            if (Trace.isInfoEnabled())
                Trace.info("onManagerUninitialized");
            endpointManager = null;
        }
        public void onTransportAdded(Transport transport)
        {
            if (Trace.isInfoEnabled())
                Trace.info("onTransportAdded");

            buttonLogin.Enabled = false;
            buttonLogout.Enabled = true;
            buttonRename.Enabled = true;

            chat.chatclient.chatviews.CommonInfo.getInstance().registerListener("halls", e => updateRooms((List<chat.chatviews.RoomChatHallInfo>)e.value));
            chat.chatclient.chatviews.UserInfo.getInstance().registerListener("lasterror", e => onRecvErrorCode((int)e.value));
            chat.chatclient.chatviews.UserInfo.getInstance().registerListener("name", e =>
            {
                var name = ((chat.chatclient.chatviews.UserInfo.__name)e.value).nickname;
                if (name.Length == 0)
                    onSetUserNickName();
                else
                    this.Text = "chat room [" + name + "]";
            });
            chat.chatclient.chatviews.UserInfo.getInstance().registerListener("recvedmessage", e => onRecvedMessage((chat.chatviews.ChatMessage)e.value));
            chat.chatclient.chatviews.UserInfo.getInstance().registerListener("sendedmessage", e => onSendedMessage((chat.chatviews.ChatMessage)e.value));
        }
        public void onTransportRemoved(Transport transport)
        {
            if (Trace.isInfoEnabled())
                Trace.info("onTransportRemoved");
            buttonLogin.Enabled = true;
            buttonLogout.Enabled = false;
            buttonJoin.Enabled = false;
            buttonRename.Enabled = false;
            treeViewRooms.Nodes.Clear();
        }

        class TreeNodeTagObject
        {
            private long _id;
            private bool _isRoom;

            public TreeNodeTagObject(long id, bool isRoom)
            {
                _id = id;
                _isRoom = isRoom;
            }
            public long Id { get { return _id; } }
            public bool isRoom { get { return _isRoom; } }
        }

        private void updateRooms(List<chat.chatviews.RoomChatHallInfo> halls)
        {
            treeViewRooms.Nodes.Clear();

            foreach (var hall in halls)
            {
                var hallnode = new TreeNode(hall.name);
                hallnode.Tag = new TreeNodeTagObject(hall.hallid, false);
                foreach (var room in hall.rooms)
                {
                    var roomnode = new TreeNode(room.name);
                    roomnode.Tag = new TreeNodeTagObject(room.roomid, true);
                    hallnode.Nodes.Add(roomnode);
                }
                treeViewRooms.Nodes.Add(hallnode);
            }
            treeViewRooms.ExpandAll();
        }

        private void treeViewRooms_AfterSelect(object sender, TreeViewEventArgs e)
        {
            var obj = (TreeNodeTagObject)e.Node.Tag;

            bool enabled = false;
            chat.chatclient.chatviews.UserInfo.getInstance().visitName(v => enabled = v.nickname.Length > 0);
            buttonJoin.Enabled = enabled && obj.isRoom && null == currentChatRoomForm;
        }

        private void sendUserInfoMessage(string msg)
        {
            endpointManager.getViewContext(providerId, ViewContextKind.Static).sendMessage(chat.chatclient.chatviews.UserInfo.getInstance(), msg);
        }

        private void onSetUserNickName()
        {
            Rename dialog = new Rename();
            if (DialogResult.OK == dialog.ShowDialog())
                sendUserInfoMessage("nickname=" + dialog.getName());
        }

        private void buttonRename_Click(object sender, EventArgs e)
        {
            onSetUserNickName();
        }

        private void onRecvErrorCode(int code)
        {
            string codemsg;
            switch (code)
            {
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
            MessageBox.Show("error :  code = " + code
                    + " msg = " + codemsg);
        }

        private void buttonJoin_Click(object sender, EventArgs e)
        {
            var obj = (TreeNodeTagObject)treeViewRooms.SelectedNode.Tag;
            if (obj.isRoom)
                sendUserInfoMessage("join=" + obj.Id);
            buttonJoin.Enabled = false;
        }

        private ChatRoom currentChatRoomForm = null;

        public ChatRoom showChatRoom(chat.chatclient.chatviews.ChatRoom view)
        {
            currentChatRoomForm = new ChatRoom(view);
            currentChatRoomForm.Show();
            return currentChatRoomForm;
        }

        private void checkButtonJoinEnabled()
        {
            bool enabled = false;
            var node = treeViewRooms.SelectedNode;
            if (null != node)
            {
                var obj = (TreeNodeTagObject)treeViewRooms.SelectedNode.Tag;
                if (null != obj)
                    enabled = obj.isRoom;
            }
            buttonJoin.Enabled = enabled;
        }

        public void closeChatRoom()
        {
            currentChatRoomForm = null;
            checkButtonJoinEnabled();
        }

        private void onRecvedMessage(chat.chatviews.ChatMessage msg)
        {
            if (endpointManager.getSessionID() == msg.user)
                currentChatRoomForm.showMessage(msg.msg);
            else
                currentChatRoomForm.showPrivateMessage(msg.user, msg.msg, true);
        }

        private void onSendedMessage(chat.chatviews.ChatMessage msg)
        {
            currentChatRoomForm.showPrivateMessage(msg.user, msg.msg, false);
        }

        private void buttonLogout_Click(object sender, EventArgs e)
        {
            endpointManager.close();
        }

        private void MainForm_FormClosing(object sender, FormClosingEventArgs e)
        {
            bool closedone = false;
            Endpoint.closeEngine(() => closedone = true);
            while (!closedone)
            {
                Utils.uiThreadSchedule();
                Thread.Sleep(1);
            }
        }

    }
}
