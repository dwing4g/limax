#pragma once

#include "../xmlgeninc/views/chatviews.ChatRoom.h"

namespace chat { 
namespace chatclient { 
namespace chatviews { 

	class ChatRoom : public _ChatRoom
	{
        struct ContextData;
        ContextData* data;
	public:
        ChatRoom(std::shared_ptr<limax::ViewContext>);
        virtual ~ChatRoom();
	protected:
		virtual ChatRoom* _toChatRoom() override { return this; }
		virtual void onOpen(const std::vector<int64_t>& sessionids) override;
		virtual void onAttach(int64_t sessionid) override;
		virtual void onDetach(int64_t sessionid, int reason) override;
		virtual void onClose() override;
	};

} 
} 
} 

