//
//  ViewController.h
//  ios.chatroom
//
//  Created by xuhui on 15/5/8.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import <UIKit/UIKit.h>
#include "../xmlgeninc/xmlgen.h"

@interface ViewController : UIViewController

- (UIView*)showLoginView;
- (UIView*)showMainFrameView;
- (UIView*)showChatRoomView;
- (UIView*)getCurrentView;

- (void)startLogin:(NSString*)username tk:(NSString*)token pf:(NSString*)platflag svr:(NSString*)server sp:(int)port;
- (void)closeLogin;
- (bool)isLoginNow;

@end

@protocol ViewDataInterface<NSObject>

- (void)initInstance:(ViewController*)instance;
- (void)releaseInstance;
- (UIView*)getUIView;

@end

struct GetViewControllerable
{
    GetViewControllerable() {}
    virtual ~GetViewControllerable() {}
    virtual ViewController* getViewController() = 0;
};
