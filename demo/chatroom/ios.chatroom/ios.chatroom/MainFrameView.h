//
//  MainFrameView.h
//  ios.chatroom
//
//  Created by xuhui on 15/5/9.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "ViewController.h"

@interface MainFrameView : UIView<ViewDataInterface, UIAlertViewDelegate>

@property(nonatomic,retain) IBOutlet UILabel* lbnickname;
@property(nonatomic,retain) IBOutlet UITableView* roomsview;

@end
