//
//  ChatUIView.m
//  ios.chatroom
//
//  Created by xuhui on 15/5/9.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import "ChatRoomView.h"
#include <sstream>
#import "TargetSelectorView.h"

@implementation ChatRoomView {
    ViewController* vcontroller;
    chat::chatclient::chatviews::ChatRoom* roomview;
    limax::Resource resource;
    int64_t sendtarget;
}

- (void)initInstance:(ViewController*)instance {
    vcontroller = instance;
    resource = limax::Resource::createRoot();
    sendtarget = -1L;
}

- (void)releaseInstance {
    resource.close();
}

- (UIView*)getUIView {
    return self;
}

- (void)onChatRoomAttach:(int64_t)sessionid {
    bool done = false;
    roomview->visitNames([self, sessionid, &done](const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>& map)
    {
        auto it = map.find( sessionid);
        if( it != map.end())
        {
            NSString* name = [NSString stringWithUTF8String:it->second.nickname.c_str()];
            [self outputMessage:[NSString stringWithFormat:@"[\"%@\" enter room]", name]];
            done = true;
        }
    });
    
    if( !done)
        limax::runOnUiThread([self, sessionid](){ [self onChatRoomAttach:sessionid]; });
}

- (void)onChatRoomDetach:(int64_t)sessionid rs:(int)reason {
    
    roomview->visitNames([self, sessionid, reason](const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>& map)
    {
        NSString* as = reason >= 0 ? @"leave room" : @"disconnected";
        auto it = map.find( sessionid);
        if( it != map.end())
        {
            NSString* name = [NSString stringWithUTF8String:it->second.nickname.c_str()];
            [self outputMessage:[NSString stringWithFormat:@"[\"%@\" %@]", name, as]];
       }
    });
}

- (NSString*)getMemberName:(int64_t)sid {
    std::string name;
    roomview->visitNames([&name,sid](const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>& map)
    {
        auto it = map.find( sid);
        if( it != map.end())
            name = it->second.nickname;
    });
    return [NSString stringWithUTF8String:name.c_str()];
}

- (void)setChatView:(chat::chatclient::chatviews::ChatRoom*)v {
    roomview = v;
    
    [self showRoomName];
    
    auto closerunnable = roomview->registerListener( "lastmessage", [self](const limax::ViewChangedEvent& e)
    {
        if (limax::ViewChangedType::Replace == e.getType() || limax::ViewChangedType::Touch == e.getType())
        {
            const auto& cmsg = *(chat::chatviews::ChatMessage*)e.getValue();
            auto name = [self getMemberName:cmsg.user];
            auto msg = [NSString stringWithUTF8String:cmsg.msg.c_str()];
            [self outputMessage:[NSString stringWithFormat:@"%@ to all : %@", name, msg]];
        }
    });
    limax::Resource::create(resource, closerunnable);
    
    auto userinfo = chat::chatclient::chatviews::UserInfo::getInstance();
    closerunnable = userinfo->registerListener( "recvedmessage", [self](const limax::ViewChangedEvent& e)
    {
        const auto& cmsg = *(chat::chatviews::ChatMessage*)e.getValue();
        if( cmsg.user == limax::Endpoint::getDefaultEndpointManager()->getSessionId())
            [self outputMessage:[NSString stringWithUTF8String:cmsg.msg.c_str()]];
        else
            [self showPrivateMessage:[self getMemberName:cmsg.user] msg:[NSString stringWithUTF8String:cmsg.msg.c_str()] recved:true];
    });
    limax::Resource::create(resource, closerunnable);
    
    closerunnable = userinfo->registerListener( "sendedmessage", [self](const limax::ViewChangedEvent& e)
    {
        const auto& cmsg = *(chat::chatviews::ChatMessage*)e.getValue();
        [self showPrivateMessage:[self getMemberName:cmsg.user] msg:[NSString stringWithUTF8String:cmsg.msg.c_str()] recved:false];
    });
    limax::Resource::create(resource, closerunnable);
}

- (void)showPrivateMessage:(NSString*)name msg:(NSString*)m recved:(bool)b {
    NSString* format = b ? @"%@ to you : %@" : @"you to %@ : %@";
    [self outputMessage:[NSString stringWithFormat:format, name, m]];
}

- (void)outputMessage:(NSString*)line {
    NSString* text = [NSString stringWithFormat:@"%@%@\n", self.tvmessages.text, line];
    self.tvmessages.text = text;
}

- (void)showRoomName {
    bool done = false;
    roomview->visitInfo([&done, self](const class chat::chatviews::ViewChatRoomInfo& info)
    {
        self.lbroomname.text = [NSString stringWithUTF8String:info.name.c_str()];
        done = true;
    });
    if( !done)
        limax::runOnUiThread([self](){ [self showRoomName]; });
}

- (IBAction)leaveClick:(UIButton*)btn {
    roomview->sendMessage("leave=");
}

- (IBAction)targetClick:(UIButton*)btn {
    roomview->visitNames([self](const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>& names)
    {
        [TargetSelectorView showView:self map:names cb:^(int64_t sessionid, NSString * name) {
            [self.btntarget setTitle:name forState:UIControlStateNormal];
            self->sendtarget = sessionid;
        }];
    });
}

- (IBAction)sendClick:(UIButton*)btn {
    NSString* msg = self.tfInputMessage.text;
    self.tfInputMessage.text = @"";
    if( msg.length > 0)
    {
        std::ostringstream ss;
        ss << "message=" << sendtarget << "," << [msg cStringUsingEncoding:NSUTF8StringEncoding];
        roomview->sendMessage( ss.str());
    }
}

@end


