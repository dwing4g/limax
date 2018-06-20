using chat.chatviews;
using limax.endpoint;
using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;

namespace chatroom
{
    public partial class ChatRoom : Form
    {
        private chat.chatclient.chatviews.ChatRoom view;

        private class ItemData
        {
            public readonly string name;
            public readonly long sid;

            public ItemData(string name, long sid)
            {
                this.name = name;
                this.sid = sid;
            }

            public override string ToString()
            {
                return name;
            }
        }

        public ChatRoom(chat.chatclient.chatviews.ChatRoom view)
        {
            InitializeComponent();
            this.view = view;

            listBoxMembers.Items.Add(new ItemData("[all]", -1));
            listBoxMembers.SelectedIndex = 0;
            view.visitInfo(i => Text = i.name);
            view.visitNames(ns => makeNames(ns));
            view.registerListener("names", e => updateName(e.sessionid, ((chat.chatclient.chatviews.UserInfo.__name)e.value).nickname));
            view.registerListener("lastmessage", e =>
            {
                if (ViewChangedType.REPLACE == e.type || ViewChangedType.TOUCH == e.type)
                {
                    var msg = e.value as ChatMessage;
                    showMessageToAll(msg.user, msg.msg);
                }
            });
        }

        private void ChatRoom_FormClosed(object sender, FormClosedEventArgs e)
        {
            sendChatRoomMessage("leave=");
        }

        private void makeNames(IDictionary<long, chat.chatclient.chatviews.UserInfo.__name> v)
        {
            long mysessionid = MainForm.getEndpointManager().getSessionID();
            foreach (var e in v)
            {
                if (mysessionid != e.Key)
                    listBoxMembers.Items.Add(new ItemData(e.Value.nickname, e.Key));
            }
        }

        private void updateName(long sessionid, string name)
        {
            long mysessionid = MainForm.getEndpointManager().getSessionID();
            if (sessionid == mysessionid)
                return;
            bool currentSelect = false;
            {
                ItemData sid = (ItemData)listBoxMembers.SelectedItem;
                if (null != sid)
                    currentSelect = sid.sid == sessionid;
            }
            var oldname = removeFromMembers(sessionid);
            int index = listBoxMembers.Items.Add(new ItemData(name, sessionid));
            if (currentSelect)
                listBoxMembers.SelectedIndex = index;
            if (null != oldname)
                showMessage("[user '" + oldname + "' change name to '" + name + "']");
        }

        private string removeFromMembers(long sessionid)
        {
            foreach (var e in listBoxMembers.Items)
            {
                var i = e as ItemData;
                if (i.sid == sessionid)
                {
                    string oldname = i.name;
                    listBoxMembers.Items.Remove(e);
                    return oldname;
                }
            }
            return null;
        }

        private void showMessageToAll(long sessionid, string msg)
        {
            StringBuilder sb = new StringBuilder();
            view.visitNames(m =>
            {
                chat.chatclient.chatviews.UserInfo.__name name;
                if (m.TryGetValue(sessionid, out name))
                    sb.Append(name.nickname);
            });
            sb.Append(" to all : ").Append(msg);
            showMessage(sb.ToString());
        }

        public void showPrivateMessage(long sessionid, string msg, bool recved)
        {
            StringBuilder sb = new StringBuilder();
            if (!recved)
                sb.Append("you to ");
            view.visitNames(m =>
            {
                chat.chatclient.chatviews.UserInfo.__name name;
                if (m.TryGetValue(sessionid, out name))
                    sb.Append(name.nickname);
            });
            if (recved)
                sb.Append(" to you");
            sb.Append(" : ").Append(msg);
            showMessage(sb.ToString());
        }

        public void onViewClose()
        {
            MainForm.getInstance().closeChatRoom();
            view = null;
            Close();
        }

        public void onMemberAttach(long sessionid)
        {
            bool done = false;
            view.visitNames(v =>
            {
                chat.chatclient.chatviews.UserInfo.__name name;
                if (v.TryGetValue(sessionid, out name))
                {
                    done = true;
                    showMessage("[user \"" + name.nickname + "\" enter room]");
                }
            });
            if (!done)
                MainForm.getInstance().getExecutor()(() => onMemberAttach(sessionid));
        }

        public void onMemberDetach(long sessionid, int code)
        {
            string msg = code >= 0 ? "leave room" : "disconnected";
            view.visitNames(map =>
            {
                chat.chatclient.chatviews.UserInfo.__name name;
                if (map.TryGetValue(sessionid, out name))
                    showMessage("[user \"" + name.nickname + "\" " + msg + "]");
            });
            removeFromMembers(sessionid);
            int index = listBoxMembers.SelectedIndex;
            if (-1 != index)
                return;
            for (int i = 0; i < listBoxMembers.Items.Count; i++)
            {
                if (-1 == ((ItemData)listBoxMembers.Items[i]).sid)
                {
                    listBoxMembers.SelectedIndex = i;
                    break;
                }
            }
        }

        public void showMessage(string msg)
        {
            textBoxMessages.AppendText(msg);
            textBoxMessages.AppendText("\r\n");
        }

        private void sendChatRoomMessage(string msg)
        {
            if (null != view)
                view.sendMessage(msg);
        }

        private void buttonSend_Click(object sender, EventArgs e)
        {
            var msg = textBoxInput.Text.Trim();
            if (msg.Length > 0)
            {
                var to = listBoxMembers.SelectedItem as ItemData;
                sendChatRoomMessage("message=" + to.sid + "," + msg);
            }
            textBoxInput.Text = "";
        }

        private void listBoxMembers_SelectedIndexChanged(object sender, EventArgs e)
        {
            var to = listBoxMembers.SelectedItem as ItemData;
            if (null == to)
                labelTo.Text = "";
            else
                labelTo.Text = to.name;

        }
    }
}
