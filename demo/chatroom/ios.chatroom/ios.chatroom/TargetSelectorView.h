//
//  TargetSelectorView.h
//  ios.chatroom
//
//  Created by xuhui on 15/5/11.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import <UIKit/UIKit.h>
#include "../xmlgeninc/xmlgen.h"

typedef void (^TargetSelectorView_Selected_Block_t)( int64_t, NSString*);

@interface TargetSelectorView : UIView<UITableViewDelegate>

@property(nonatomic,retain) IBOutlet UITableView* members;

+(void)showView:(UIView*)parent map:(const limax::hashmap<int64_t,class chat::chatclient::chatviews::UserInfo::name>&) ms cb:(TargetSelectorView_Selected_Block_t) block;

@end
