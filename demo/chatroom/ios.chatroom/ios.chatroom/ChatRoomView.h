//
//  ChatUIView.h
//  ios.chatroom
//
//  Created by xuhui on 15/5/9.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "ViewController.h"

@interface ChatRoomView : UIView<ViewDataInterface>

@property(nonatomic,retain) IBOutlet UILabel* lbroomname;
@property(nonatomic,retain) IBOutlet UIButton* btntarget;
@property(nonatomic,retain) IBOutlet UITextField* tfInputMessage;
@property(nonatomic,retain) IBOutlet UITextView* tvmessages;

- (void)setChatView:(chat::chatclient::chatviews::ChatRoom*)v;
- (void)onChatRoomAttach:(int64_t)sessionid;
- (void)onChatRoomDetach:(int64_t)sessionid rs:(int)reason;

@end
