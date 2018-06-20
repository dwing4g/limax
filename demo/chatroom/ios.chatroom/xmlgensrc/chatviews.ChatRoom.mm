
#include "chatviews.ChatRoom.h"
#import "../ios.chatroom/ChatRoomView.h"

namespace chat {
    namespace chatclient {
        namespace chatviews {
            
            struct ChatRoom::ContextData
            {
                ChatRoomView* uiview;
            };
            
            ChatRoom::ChatRoom(std::shared_ptr<limax::ViewContext> vc)
                : _ChatRoom( vc), data( new ContextData)
            {
                data->uiview = nil;
            }
            ChatRoom::~ChatRoom()
            {
                delete data;
            }

            void ChatRoom::onOpen(const std::vector<int64_t>& sessionids)
            {
                auto manager = getViewContext()->getEndpointManager();
                auto getvc = (GetViewControllerable*)manager->getTransport()->getSessionObject();
                auto vc = getvc->getViewController();
                auto view = (ChatRoomView*)[vc showChatRoomView];
                data->uiview = view;
                [view setChatView:this];
            }
            void ChatRoom::onAttach(int64_t sessionid)
            {
                [data->uiview onChatRoomAttach:sessionid];
            }
            void ChatRoom::onDetach(int64_t sessionid, int reason)
            {
                [data->uiview onChatRoomDetach:sessionid rs:reason];
            }
            void ChatRoom::onClose()
            {
                auto manager = getViewContext()->getEndpointManager();
                auto getvc = (GetViewControllerable*)manager->getTransport()->getSessionObject();
                auto vc = getvc->getViewController();
                if( [vc isLoginNow])
                    [vc showMainFrameView];
                data->uiview = nil;
            }
            
        }
    }
}

