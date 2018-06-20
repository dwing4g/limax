//
//  TargetSelectorView.m
//  ios.chatroom
//
//  Created by xuhui on 15/5/11.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import "TargetSelectorView.h"

@interface MemberDataSource : NSObject<UITableViewDataSource>

@end

struct MemberData
{
    NSString* nickname;
    int64_t     sessionid;
};

@implementation MemberDataSource {
    std::vector<MemberData> datas;
}

- (void)setData:(const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>&)names {
    datas.clear();
    
    MemberData data;
    data.nickname = @"[all]";
    data.sessionid = -1L;
    datas.push_back( data);
    
    const auto mysessionid = limax::Endpoint::getDefaultEndpointManager()->getSessionId();

    for( const auto& it : names) {
        if( mysessionid == it.first)
            continue;
        MemberData data;
        data.nickname = [NSString stringWithUTF8String:it.second.nickname.c_str()];
        data.sessionid = it.first;
        datas.push_back( data);
    }
}

- (const MemberData&)getData:(size_t)index {
    return datas.at( index);
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return datas.size();
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    auto *cell=[[UITableViewCell alloc]initWithStyle:UITableViewCellStyleDefault reuseIdentifier:nil];
    const auto& data = datas.at( indexPath.row);
    cell.textLabel.text = data.nickname;
    return cell;
}

@end

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


@implementation TargetSelectorView {
    MemberDataSource* datasource;
    TargetSelectorView_Selected_Block_t callback_block;
}

- (IBAction)cancleClick:(UIButton*)btn {
    [self removeFromSuperview];
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
    const auto& data = [datasource getData:indexPath.row];
    callback_block( data.sessionid, data.nickname);
    [self removeFromSuperview];
}

-(void)InitDatas:(const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>&)ms cb:(TargetSelectorView_Selected_Block_t) block {
    callback_block = block;
    datasource = [[MemberDataSource alloc] init];
    [datasource setData:ms];
    self.members.dataSource = datasource;
    self.members.delegate = self;
}

+(void)showView:(UIView*)parent map:(const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>&)ms cb:(TargetSelectorView_Selected_Block_t) block {
    auto* views = [[NSBundle mainBundle] loadNibNamed:@"TargetSelectorView" owner:nil options:nil];
    auto* view = (TargetSelectorView*)[views lastObject];
    [view InitDatas:ms cb:block];
    [parent addSubview:view];
}

@end
