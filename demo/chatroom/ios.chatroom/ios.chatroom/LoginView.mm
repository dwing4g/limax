//
//  LoginView.m
//  ios.chatroom
//
//  Created by xuhui on 15/5/9.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import "LoginView.h"

@interface ServerData : NSObject
{
@public
    NSString* serverip;
    int serverport;
}

-(NSString*)viewString;
+(ServerData*)newData:(NSString*)ip p:(int)port;

@end

@implementation ServerData

+ (ServerData*)newData:(NSString*)ip p:(int)port {
    auto* data = [[ServerData alloc] init];
    data->serverip = ip;
    data->serverport = port;
    return data;
}

- (NSString*)viewString {
    return [NSString stringWithFormat:@"%@:%i", serverip , serverport];
}

@end

@implementation LoginView
{
    NSArray* serverDatas;
    
    ViewController* vcontroller;
}

- (NSInteger)numberOfComponentsInPickerView:(UIPickerView *)pickerView {
    return 1;
}

- (NSInteger)pickerView:(UIPickerView *)pickerView numberOfRowsInComponent:(NSInteger)component {
   return [serverDatas count];
}

- (NSString *)pickerView:(UIPickerView *)pickerView titleForRow:(NSInteger)row forComponent:(NSInteger)component {
    return [[serverDatas objectAtIndex:row] viewString];
}

- (void)initPickView {
    serverDatas = @[
                    [ServerData newData:@"127.0.0.1" p: 10000],
                    [ServerData newData:@"10.0.0.191" p: 10000],
                    [ServerData newData:@"192.168.1.99" p: 10000]
                    ];
    
    self.servers.dataSource = self;
    self.servers.delegate = self;
}

- (void)initInstance:(ViewController*)instance {
    [self initPickView];
    vcontroller = instance;
}

- (void)releaseInstance {
    
}

- (UIView*)getUIView {
    return  self;
}

- (IBAction)loginClick:(UIButton*)btn {
    auto* username = [self.username text];
    auto* token = [self.token text];
    auto* platflag = [self.platflag text];
    
    auto index = [self.servers selectedRowInComponent:0];
    ServerData* data = [serverDatas objectAtIndex:index];
    
    [ vcontroller startLogin:username tk:token pf:platflag svr:data->serverip sp:data->serverport];
}

@end
