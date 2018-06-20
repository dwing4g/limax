//
//  LoginView.h
//  ios.chatroom
//
//  Created by xuhui on 15/5/9.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import <UIKit/UIKit.h>
#import "ViewController.h"

@interface LoginView : UIView<UIPickerViewDataSource, UIPickerViewDelegate, ViewDataInterface>
{
}

@property(nonatomic,retain) IBOutlet UITextField* username;
@property(nonatomic,retain) IBOutlet UITextField* token;
@property(nonatomic,retain) IBOutlet UITextField* platflag;
@property(nonatomic,retain) IBOutlet UIPickerView* servers;

@end
