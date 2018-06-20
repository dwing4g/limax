//
//  ViewController.m
//  ios.chatroom
//
//  Created by xuhui on 15/5/8.
//  Copyright (c) 2015年 com.limaxproject. All rights reserved.
//

#import "ViewController.h"

class ChatClientListener : public limax::EndpointListener, public GetViewControllerable
{
    ViewController* vcontroller;
    limax::EndpointManager* volatile manager = nullptr;
public:
    ChatClientListener() {}
    virtual ~ChatClientListener() {}
public:
    void setViewController( ViewController* vc)
    {
        vcontroller = vc;
    }
    void closeManager()
    {
        if( manager)
            manager->close();
    }
    bool isHasManager()
    {
        return nullptr != manager;
    }
public:
    virtual void onManagerInitialized(limax::EndpointManager* mng, limax::EndpointConfig*) override;
    virtual void onTransportAdded(limax::Transport*) override;
    virtual void onTransportRemoved(limax::Transport*) override;
    virtual void onAbort(limax::Transport*) override;
    virtual void onManagerUninitialized(limax::EndpointManager*) override;
    virtual void onSocketConnected() override;
    virtual void onKeyExchangeDone() override;
    virtual void onKeepAlived(int ping) override;
    virtual void onErrorOccured(int errorsource, int errorvalue, const std::string& info) override;
    virtual void destroy() override;
    
    virtual ViewController* getViewController() override
    {
        return vcontroller;
    }
};

@interface ViewController ()

@end

@implementation ViewController
{
    id<ViewDataInterface> currentViewData;
    NSThread*   idleThread;
    NSCondition*    idleLocker;
    ChatClientListener listener;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    [self showLoginView];
    
    listener.setViewController( self);
    limax::Endpoint::openEngine();
    
    idleLocker = [[NSCondition alloc] init];
    idleThread = [[NSThread alloc] initWithTarget:self selector:@selector(idleThreadRunning:) object:nil];
    [idleThread start];
}

-(void)idleThreadRunning:(NSObject*)_ {
    while(true) {
        [idleLocker lock];
        dispatch_async(dispatch_get_main_queue(), ^{
            limax::uiThreadSchedule();
            [idleLocker lock];
            [idleLocker signal];
            [idleLocker unlock];
        });
        [idleLocker wait];
        [idleLocker unlock];
        usleep( 100 * 1000);
    }
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

-(UIView*)showViewByName:(NSString*)viewname {
    auto* views = [[NSBundle mainBundle] loadNibNamed:viewname owner:nil options:nil];
    UIView* view = [views lastObject];
    id<ViewDataInterface> viewdata = (id<ViewDataInterface>)view;
    if (nil != currentViewData)
    {
        [currentViewData releaseInstance];
        [[currentViewData getUIView] removeFromSuperview];
    }
    currentViewData = viewdata;
    [currentViewData initInstance:self];
    [self.view addSubview:view];
    return view;
}

- (UIView*)showLoginView {
    return [self showViewByName:@"LoginView"];
}

- (UIView*)showMainFrameView {
    return [self showViewByName:@"MainFrameView"];
}

- (UIView*)showChatRoomView {
    return [self showViewByName:@"ChatRoomView"];
}

- (UIView*)getCurrentView {
    return [self->currentViewData getUIView];
}

- (void)startLogin:(NSString*)username tk:(NSString*)token pf:(NSString*)platflag svr:(NSString*)server sp:(int)port {
    auto config = limax::Endpoint::createEndpointConfigBuilder([server cStringUsingEncoding:NSUTF8StringEncoding], port,limax::LoginConfig::plainLogin([username cStringUsingEncoding:NSUTF8StringEncoding], [token cStringUsingEncoding:NSUTF8StringEncoding], [platflag cStringUsingEncoding:NSUTF8StringEncoding]))->staticViewCreatorManagers( { chat::getChatviewsViewCreatorManager(100) })->executor( limax::runOnUiThread)->build();
    limax::Endpoint::start( config, &listener);
}

- (void)closeLogin {
    listener.closeManager();
}

- (bool)isLoginNow {
    return listener.isHasManager();
}

@end

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////

void ChatClientListener::onManagerInitialized(limax::EndpointManager* mng, limax::EndpointConfig*)
{
    manager = mng;
}

void ChatClientListener::onTransportAdded(limax::Transport* transport)
{
    transport->setSessionObject((GetViewControllerable*)this);
    [vcontroller showMainFrameView];
}

void ChatClientListener::onTransportRemoved(limax::Transport*)
{
    [vcontroller showLoginView];
}

void ChatClientListener::onAbort(limax::Transport*)
{
    auto *view = [[UIAlertView alloc]initWithTitle:@"chatroom" message:@"connect abort" delegate:nil cancelButtonTitle:@"OK" otherButtonTitles:nil, nil];
    [view show];
}

void ChatClientListener::onManagerUninitialized(limax::EndpointManager*)
{
    manager = nullptr;
}

void ChatClientListener::onSocketConnected() {}

void ChatClientListener::onKeyExchangeDone() {}

void ChatClientListener::onKeepAlived(int ping) {}

void ChatClientListener::onErrorOccured(int errorsource, int errorvalue, const std::string& info)
{
    auto* i = [NSString stringWithUTF8String:info.c_str()];
    auto* msg = [NSString stringWithFormat:@"onerror source = %i error = %i info = %@", errorsource, errorvalue, i];
    auto *view = [[UIAlertView alloc]initWithTitle:@"chatroom" message:msg delegate:nil cancelButtonTitle:@"OK" otherButtonTitles:nil, nil];
    [view show];
}

void ChatClientListener::destroy() {}

