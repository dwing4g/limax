using System;
using System.Collections.Generic;
using limax.codec;
using limax.util;
namespace chat.chatclient.chatviews
{
    public sealed partial class ChatRoom
    {
        private chatroom.ChatRoom frame;

        override protected void onClose()
        {
            frame.onViewClose();
        }
        override protected void onAttach(long sessionid)
        {
            frame.onMemberAttach(sessionid);
        }
        override protected void onDetach(long sessionid, byte reason)
        {
            frame.onMemberDetach(sessionid, reason);
        }
        override protected void onOpen(ICollection<long> sessionids)
        {
            frame = chatroom.MainForm.getInstance().showChatRoom(this);
        }
    }
}
