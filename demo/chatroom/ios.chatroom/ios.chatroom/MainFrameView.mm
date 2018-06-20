//
//  MainFrameView.m
//  ios.chatroom
//
//  Created by xuhui on 15/5/9.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import "MainFrameView.h"
#include <sstream>

@interface HallsDataSource : NSObject<UITableViewDataSource>

@end

struct RoomData
{
    std::string showname;
    int64_t     roomid;
};

@implementation HallsDataSource {
    std::vector<RoomData> datas;
}

- (void)setData:(const std::vector<class chat::chatviews::RoomChatHallInfo>&)halls {
    datas.clear();
    for( const auto& hall : halls) {
        for( const auto& room : hall.rooms)
        {
            RoomData data;
            data.showname = hall.name + " - " + room.name;
            data.roomid = room.roomid;
            datas.push_back( data);
        }
    }
}

- (const RoomData&)getData:(size_t)index {
    return datas.at( index);
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return datas.size();
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    auto *cell=[[UITableViewCell alloc]initWithStyle:UITableViewCellStyleDefault reuseIdentifier:nil];
    const auto& data = datas.at( indexPath.row);
    cell.textLabel.text = [NSString stringWithUTF8String:data.showname.c_str()];
    return cell;
}

@end

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@implementation MainFrameView {
    ViewController* vcontroller;
    HallsDataSource* datasource;
    limax::Resource resource;
}

- (void)initInstance:(ViewController*)instance {
    vcontroller = instance;

    datasource = [[HallsDataSource alloc] init];
    self.roomsview.dataSource = datasource;

    resource = limax::Resource::createRoot();
    
    auto closerunnable = chat::chatclient::chatviews::CommonInfo::getInstance()->registerListener( "halls", [self](const limax::ViewChangedEvent& e)
    {
        [self updateRooms];
    });
    [self updateRooms];
    limax::Resource::create( resource, closerunnable);
    
    auto view = chat::chatclient::chatviews::UserInfo::getInstance();
    closerunnable = view->registerListener( "name", [self](const limax::ViewChangedEvent& e)
    {
        auto name = ((class chat::chatclient::chatviews::UserInfo::name*)e.getValue())->nickname;
        if( name.empty())
            [self doRename];
        else
            self.lbnickname.text = [NSString stringWithUTF8String:name.c_str()];
    });
    limax::Resource::create( resource, closerunnable);
    view->visitName([self](const class chat::chatclient::chatviews::UserInfo::name& name)
    {
        self.lbnickname.text = [NSString stringWithUTF8String:name.nickname.c_str()];
    });
    
    closerunnable = view->registerListener( "lasterror", [self](const limax::ViewChangedEvent& e)
    {
        auto ec = *(int*)e.getValue();
        [self showLastError:ec];
    });
    limax::Resource::create( resource, closerunnable);
}

- (void)updateRooms {
    auto ds = datasource;
    chat::chatclient::chatviews::CommonInfo::getInstance()->visitHalls( [ds](const std::vector<class chat::chatviews::RoomChatHallInfo>& halls)
    {
        [ds setData:halls];
    });
    [self.roomsview reloadData];
}

- (void)showLastError:(int)ec {
    std::string info;
    switch ( ec) {
        case chat::chatviews::ErrorCodes::EC_BAD_ROOM_ID:
            info = "bad room id";
            break;
        case chat::chatviews::ErrorCodes::EC_NAME_EXISTING:
            info = "name already existing";
            break;
        case chat::chatviews::ErrorCodes::EC_NAME_UNMODIFIED:
            info = "name unmodified";
            break;
        default: {
            std::stringstream ss;
            ss << "[unknow error code = " << ec << "]";
            break;
        }
    }
    auto *view = [[UIAlertView alloc]initWithTitle:@"chatroom error" message:[NSString stringWithUTF8String:info.c_str()] delegate:nil cancelButtonTitle:@"OK" otherButtonTitles:nil, nil];
    [view show];
}

- (void)doRename {
    auto alert = [[UIAlertView alloc] initWithTitle:@"rename" message:nil delegate:self cancelButtonTitle:@"cancel" otherButtonTitles:@"modify",nil];
    [alert setAlertViewStyle:UIAlertViewStylePlainTextInput];
    auto * text = [[UITextField alloc] init];
    [alert addSubview:text];
    [alert show];
}

- (void)alertView:(UIAlertView *)alertView clickedButtonAtIndex:(NSInteger)buttonIndex {
    bool cancel = alertView.cancelButtonIndex == buttonIndex;
    if( !cancel)
    {
        auto *text=[alertView textFieldAtIndex:0];
        if( text.text.length > 0)
        {
            std::string nickname = [text.text cStringUsingEncoding:NSUTF8StringEncoding];
            std::stringstream ss;
            ss << "nickname=" << nickname;
            chat::chatclient::chatviews::UserInfo::getInstance()->sendMessage( ss.str());
        }
        else
        {
            cancel = true;
        }
    }
    
    if(cancel)
    {
        bool hasname = false;
        chat::chatclient::chatviews::UserInfo::getInstance()->visitName([&hasname](const class chat::chatclient::chatviews::UserInfo::name& v) { hasname = !v.nickname.empty(); });
        if( hasname)
            return;
        dispatch_async( dispatch_get_main_queue(), ^{
            [self doRename];
        });
    }
}

- (void)releaseInstance {
    resource.close();
}

- (UIView*)getUIView {
    return self;
}

- (IBAction)renameClick:(UIButton*)btn {
    [self doRename];
}

- (IBAction)joinClick:(UIButton*)btn {
    auto index = [self.roomsview indexPathForSelectedRow];
    if( nil == index)
        return;
    const auto& data = [datasource getData:index.row];
    
    auto view = chat::chatclient::chatviews::UserInfo::getInstance();
    bool hasname = false;
    view->visitName([&hasname](const class chat::chatclient::chatviews::UserInfo::name v) { hasname = !v.nickname.empty(); });
    if( !hasname)
        return;
    
    std::ostringstream ss;
    ss << "join=" << data.roomid;
    view->sendMessage( ss.str());
}

- (IBAction)logoutClick:(UIButton*)btn {
    [vcontroller closeLogin];
}


@end
