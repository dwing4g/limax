using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Sockets;
using System.Numerics;
using System.Reflection;
using System.Threading;
using System.Text;
using limax.codec;
using limax.defines;
using limax.endpoint.auanyviews;
using limax.endpoint.providerendpoint;
using limax.endpoint.script;
using limax.endpoint.switcherendpoint;
using limax.endpoint.variant;
using limax.net;
using limax.providerendpoint;
using limax.util;

namespace limax.endpoint
{
    public static class __ProtocolProcessManager
    {
        private static EndpointManagerImpl i(Protocol p)
        {
            return (EndpointManagerImpl)p.getManager();
        }
        public static void process(SHandShake p)
        {
            i(p).process(p);
        }
        public static void process(SessionKick p)
        {
            i(p).process(p);
        }
        public static void process(ProviderLogin p)
        {
            i(p).process(p);
        }
        public static void process(OnlineAnnounce p)
        {
            i(p).process(p);
        }
        public static void process(PingAndKeepAlive p)
        {
            i(p).process(p);
        }
        public static void process(SyncViewToClients p)
        {
            i(p).process(p);
        }
        public static void process(Tunnel p)
        {
            i(p).process(p);
        }
        public static void onResultViewOpen(ServiceResult view)
        {
            AuanyService.onResultViewOpen(view);
        }
    }
    internal abstract class AbstractViewContext : ViewContext
    {
        internal abstract void onSyncViewToClients(SyncViewToClients protocol);
        internal abstract void clear();
        public abstract ViewContextKind getKind();
        public abstract int getProviderId();
        public abstract View getSessionOrGlobalView(short classindex);
        public abstract TemporaryView findTemporaryView(short classindex, int instanceindex);
        public abstract EndpointManager getEndpointManager();
        public void sendMessage(View view, string msg)
        {
            SendControlToServer p = new SendControlToServer();
            p.providerid = getProviderId();
            p.classindex = view.getClassIndex();
            p.instanceindex = view is TemporaryView ? ((TemporaryView)view).getInstanceIndex() : 0;
            p.controlindex = 255;
            p.stringdata = msg;
            p.send(getEndpointManager().getTransport());
        }
    }
    public sealed class AuanyService
    {
        public const int providerId = 1;
        public delegate void Result(int errorSource, int errorCode, string result);
        private static int snGenerator = 0;
        private readonly static IDictionary<int, AuanyService> map = new Dictionary<int, AuanyService>();
        private readonly int sn;
        private readonly Executor executor;
        private readonly Result onresult;
        private readonly Timer timer;
        private static AuanyService removeService(int sn)
        {
            AuanyService service;
            lock (map)
            {
                if (map.TryGetValue(sn, out service))
                {
                    map.Remove(sn);
                    service.timer.Dispose();
                }
            }
            return service;
        }
        private AuanyService(Result onresult, long timeout, Executor executor)
        {
            this.sn = Interlocked.Increment(ref snGenerator);
            this.executor = executor;
            this.onresult = onresult;
            timer = new Timer(_ =>
            {
                AuanyService service = removeService(sn);
                if (service != null)
                    service.onError(ErrorCodes.ENDPOINT_AUANY_SERVICE_CLIENT_TIMEOUT);
            });
            lock (map)
            {
                timer.Change(timeout, Timeout.Infinite);
                map.Add(this.sn, this);
            }
        }
        private void onError(int errorCode)
        {
            onResult(ErrorSource.ENDPOINT, errorCode, null);
        }
        private void onResult(int errorSource, int errorCode, string credential)
        {
            executor(() => onresult(errorSource, errorCode, credential));
        }
        internal static void onResultViewOpen(ServiceResult view)
        {
            view.registerListener(e =>
            {
                limax.auanyviews.Result r = (limax.auanyviews.Result)e.value;
                AuanyService service = removeService(r.sn);
                if (service != null)
                    service.onResult(r.errorSource, r.errorCode, r.result);
            });
        }
        internal static void cleanup()
        {
            lock (map)
            {
                foreach (var service in map.Values)
                {
                    service.timer.Dispose();
                    service.onError(ErrorCodes.ENDPOINT_AUANY_SERVICE_ENGINE_CLOSE);
                }
                map.Clear();
            }
        }
        private class CredentialContext
        {
            internal class Listener : EndpointListener
            {
                private readonly CredentialContext ctx;
                private readonly Action action;
                internal Listener(CredentialContext ctx, Action action) { this.ctx = ctx; this.action = action; }
                public void onAbort(Transport transport) { lock (ctx) { Monitor.Pulse(ctx); } }
                public void onManagerInitialized(Manager manager, Config config) { ctx.manager = manager as EndpointManager; }
                public void onManagerUninitialized(Manager manager) { }
                public void onTransportAdded(Transport transport) { action(); }
                public void onTransportRemoved(Transport transport) { }
                public void onSocketConnected() { }
                public void onKeyExchangeDone() { }
                public void onKeepAlived(int ms) { }
                public void onErrorOccured(int source, int code, Exception exception)
                {
                    lock (ctx)
                    {
                        ctx.errorSource = source;
                        ctx.errorCode = code;
                        Monitor.Pulse(ctx);
                    }
                }
            };
            private int errorSource = ErrorSource.ENDPOINT;
            private int errorCode = ErrorCodes.ENDPOINT_AUANY_SERVICE_CLIENT_TIMEOUT;
            private string credential;
            private EndpointManager manager;
            private readonly string httpHost;
            private readonly int httpPort;
            private readonly int appid;
            private readonly long timeout;
            private class CredentialResult
            {
                private readonly CredentialContext ctx;
                internal CredentialResult(CredentialContext ctx) { this.ctx = ctx; }
                internal Result get()
                {
                    return (errorSource, errorCode, credential) =>
                        {
                            lock (ctx)
                            {
                                ctx.errorSource = errorSource;
                                ctx.errorCode = errorCode;
                                ctx.credential = credential;
                                Monitor.Pulse(ctx);
                            }
                        };
                }
            }
            private readonly CredentialResult result;
            internal CredentialContext(string httpHost, int httpPort, int appid, long timeout)
            {
                this.httpHost = httpHost;
                this.httpPort = httpPort;
                this.appid = appid;
                this.timeout = timeout;
                this.result = new CredentialResult(this);
            }
            private void execute(Func<string, Action> action, Result r)
            {
                try
                {
                    long starttime = DateTime.Now.Ticks;
                    JSONDecoder decoder = new JSONDecoder();
                    new HttpClient("http://" + httpHost + ":" + httpPort + "/invite?native=" + appid, timeout / 2, 4096, null, false).transfer(new CharSink(decoder));
                    JSON json = decoder.get();
                    JSON switcher = json.get("switcher");
                    string code = json.get("code").ToString();
                    EndpointConfig config = Endpoint.createEndpointConfigBuilder(switcher.get("host").ToString(), switcher.get("port").intValue(), LoginConfig.plainLogin(code, "", "invite")).build();
                    long remain = timeout - (DateTime.Now.Ticks - starttime) / TimeSpan.TicksPerMillisecond;
                    if (remain > 0)
                    {
                        lock (this)
                        {
                            Endpoint.start(config, new Listener(this, action(code)));
                            Monitor.Wait(this, (int)remain);
                        }
                        manager.close();
                    }
                }
                catch (Exception) { }
                r(errorSource, errorCode, credential);
            }
            public void derive(string authcode, Result r)
            {
                execute(code => () => AuanyService.derive(code, authcode, timeout, result.get(), manager), r);
            }
            public void bind(string authcode, LoginConfig loginConfig, Result r)
            {
                execute(code => () => AuanyService.bind(code, authcode, loginConfig, timeout, result.get(), manager), r);
            }
            public void temporary(string credential, string authcode, string authcode2, long milliseconds, byte usage, string subid, Result r)
            {
                execute(codec => () => AuanyService.temporary(credential, authcode, authcode2, milliseconds, usage, subid, timeout, result.get(), manager), r);
            }
            public void temporary(LoginConfig loginConfig, string authcode, long milliseconds, byte usage, string subid, Result r)
            {
                execute(codec => () => AuanyService.temporary(loginConfig, appid, authcode, milliseconds, usage, subid, timeout, result.get(), manager), r);
            }
            public void transfer(LoginConfig loginConfig, string authcode, string temp, string authtemp, Result r)
            {
                execute(code => () => AuanyService.transfer(loginConfig, authcode, temp, authtemp, timeout, result.get(), manager), r);
            }
        }
        public static void derive(string httpHost, int httpPort, int appid, string authcode, long timeout, Result onresult)
        {
            new CredentialContext(httpHost, httpPort, appid, timeout).derive(authcode, onresult);
        }
        public static void derive(string credential, string authcode, long timeout, Result onresult, EndpointManager manager)
        {
            Service.getInstance(manager).Derive(new AuanyService(onresult, timeout, ((EndpointManagerImpl)manager).dispatch).sn, credential, authcode);
        }
        public static void derive(string credential, string authcode, long timeout, Result onresult)
        {
            derive(credential, authcode, timeout, onresult, Endpoint.getDefaultEndpointManager());
        }
        private static Octets toNonce(string authcode)
        {
            return Octets.wrap(SHA256.digest(Encoding.UTF8.GetBytes(authcode)));
        }
        public static void bind(string httpHost, int httpPort, int appid, string authcode, LoginConfig loginConfig, long timeout, Result onresult)
        {
            new CredentialContext(httpHost, httpPort, appid, timeout).bind(authcode, loginConfig, onresult);
        }
        public static void bind(string credential, string authcode, LoginConfig loginConfig, long timeout, Result onresult, EndpointManager manager)
        {
            Service.getInstance(manager).Bind(new AuanyService(onresult, timeout, ((EndpointManagerImpl)manager).dispatch).sn, credential, authcode, loginConfig.getUsername(), loginConfig.getToken(toNonce(authcode)), loginConfig.getPlatflagRaw());
        }
        public static void bind(string credential, string authcode, LoginConfig loginConfig, long timeout, Result onresult)
        {
            bind(credential, authcode, loginConfig, timeout, onresult, Endpoint.getDefaultEndpointManager());
        }
        public static void temporary(string httpHost, int httpPort, int appid, string credential, string authcode, string authcode2, long milliseconds, byte usage, string subid, long timeout, Result onresult)
        {
            new CredentialContext(httpHost, httpPort, appid, timeout).temporary(credential, authcode, authcode2, milliseconds, usage, subid, onresult);
        }
        public static void temporary(string credential, string authcode, string authcode2, long milliseconds, byte usage, string subid, long timeout, Result onresult, EndpointManager manager)
        {
            Service.getInstance(manager).TemporaryFromCredential(new AuanyService(onresult, timeout, ((EndpointManagerImpl)manager).dispatch).sn, credential, authcode, authcode2, milliseconds, usage, subid);
        }
        public static void temporary(string credential, string authcode, string authcode2, long milliseconds, byte usage, string subid, long timeout, Result onresult)
        {
            temporary(credential, authcode, authcode2, milliseconds, usage, subid, timeout, onresult, Endpoint.getDefaultEndpointManager());
        }
        public static void temporary(string httpHost, int httpPort, int appid, LoginConfig loginConfig, string authcode, long milliseconds, byte usage, string subid, long timeout, Result onresult)
        {
            new CredentialContext(httpHost, httpPort, appid, timeout).temporary(loginConfig, authcode, milliseconds, usage, subid, onresult);
        }
        public static void temporary(LoginConfig loginConfig, int appid, string authcode, long milliseconds, byte usage, string subid, long timeout, Result onresult, EndpointManager manager)
        {
            Service.getInstance(manager).TemporaryFromLogin(new AuanyService(onresult, timeout, ((EndpointManagerImpl)manager).dispatch).sn, loginConfig.getUsername(), loginConfig.getToken(toNonce(authcode)), loginConfig.getPlatflagRaw(), appid, authcode, milliseconds, usage, subid);
        }
        public static void temporary(LoginConfig loginConfig, int appid, string authcode, long milliseconds, byte usage, string subid, long timeout, Result onresult)
        {
            temporary(loginConfig, appid, authcode, milliseconds, usage, subid, timeout, onresult, Endpoint.getDefaultEndpointManager());
        }
        public static void transfer(string httpHost, int httpPort, int appid, LoginConfig loginConfig, string authcode, string temp, string authtemp, long timeout, Result onresult)
        {
            new CredentialContext(httpHost, httpPort, appid, timeout).transfer(loginConfig, authcode, temp, authtemp, onresult);
        }
        private static void transfer(LoginConfig loginConfig, string authcode, string temp, string authtemp, long timeout, Result onresult, EndpointManager manager)
        {
            Service.getInstance(manager).Transfer(new AuanyService(onresult, timeout, ((EndpointManagerImpl)manager).dispatch).sn, loginConfig.getUsername(), loginConfig.getToken(toNonce(authcode)), loginConfig.getPlatflagRaw(), authcode, temp, authtemp);
        }
        public static void pay(int gateway, int payid, int product, int price, int quantity, string receipt, long timeout, Result onresult, EndpointManager manager)
        {
            Service.getInstance(manager).Pay(new AuanyService(onresult, timeout, ((EndpointManagerImpl)manager).dispatch).sn, gateway, payid, product, price, quantity, receipt);
        }
        public static void pay(int gateway, int payid, int product, int price, int quantity, string receipt, long timeout, Result onresult)
        {
            pay(gateway, payid, product, price, quantity, receipt, timeout, onresult, Endpoint.getDefaultEndpointManager());
        }
    }
    public static class Endpoint
    {
        private static ISet<int> protocolpvids = new HashSet<int>();
        public static void openEngine(int applicationthreadcount)
        {
            Engine.open(1, 1, applicationthreadcount);
        }
        public static void openEngine()
        {
            openEngine(4);
        }
        public static void closeEngine(Action done)
        {
            AuanyService.cleanup();
            new Thread(_ =>
            {
                try { Engine.close(); }
                finally
                {
                    if (done != null)
                        done();
                }
            }).Start();
        }
        public static EndpointConfigBuilder createEndpointConfigBuilder(string serverIp, int serverPort, LoginConfig loginConfig)
        {
            return new EndpointConfigBuilderImpl(serverIp, serverPort, loginConfig, false);
        }
        public static EndpointConfigBuilder createEndpointConfigBuilder(ServiceInfo service, LoginConfig loginConfig)
        {
            ServiceInfo.SwitcherConfig switcher = service.randomSwitcherConfig();
            return createEndpointConfigBuilder(switcher.host, switcher.port, loginConfig);
        }
        public static EndpointConfigBuilder createPingOnlyConfigBuilder(string serverIp, int serverPort)
        {
            return new EndpointConfigBuilderImpl(serverIp, serverPort, null, true);
        }
        private static void mapPvidsAppendValue(IDictionary<int, byte> pvids, int s, int nv)
        {
            byte v;
            if (pvids.TryGetValue(s, out v))
            {
                v = (byte)(v | nv);
                pvids.Remove(s);
            }
            else
                v = (byte)nv;
            pvids.Add(s, v);
        }
        private static ICollection<int> makeProtocolProviderIds(EndpointConfig config)
        {
            ISet<int> pvids = new HashSet<int>();
            foreach (int type in config.getEndpointState().getSizePolicy().Keys)
            {
                int pvid = (int)((uint)type >> 8);
                if (pvid > 0)
                    pvids.Add(pvid);
            }
            return pvids;
        }
        private static ICollection<int> makeScriptProviderIds(EndpointConfig config)
        {
            if (config.getScriptEngineHandle() != null)
                return config.getScriptEngineHandle().getProviders();
            return new List<int>();
        }
        private static IDictionary<int, byte> makeProviderMap(EndpointConfig config)
        {
            var pvids = new Dictionary<int, byte>();
            foreach (int s in makeProtocolProviderIds(config))
                mapPvidsAppendValue(pvids, s, SessionType.ST_PROTOCOL);
            foreach (int s in config.getStaticViewClasses().Keys)
                mapPvidsAppendValue(pvids, s, SessionType.ST_STATIC);
            foreach (int s in config.getVariantProviderIds())
                mapPvidsAppendValue(pvids, s, SessionType.ST_VARIANT);
            foreach (int s in makeScriptProviderIds(config))
                mapPvidsAppendValue(pvids, s, SessionType.ST_SCRIPT);
            if (config.auanyService())
                mapPvidsAppendValue(pvids, AuanyService.providerId, SessionType.ST_STATIC);
            return pvids;
        }
        public static void start(EndpointConfig config, EndpointListener listener)
        {
            new Thread(() =>
            {
                try
                {
                    if (null == Engine.getProtocolScheduler())
                        throw new Exception("endpoint need call openEngine");
                    IDictionary<int, byte> pvids = makeProviderMap(config);
                    if (!config.isPingServerOnly() && pvids.Count == 0)
                        throw new Exception("endpoint no available provider");
                    new EndpointManagerImpl(config, listener, pvids);
                }
                catch (Exception e)
                {
                    config.getClientManagerConfig().getDispatcher().execute(() => listener.onErrorOccured(ErrorSource.ENDPOINT, 0, e));
                }
            }).Start();
        }
        public static Closeable start(string host, int port, LoginConfig loginConfig, ScriptEngineHandle handle, Executor executor)
        {
            return new WebSocketConnector(host, port, loginConfig, handle, executor);
        }
        public static List<ServiceInfo> loadServiceInfos(string httpHost, int httpPort, int appid, string additionalQuery, long timeout, int maxsize, string cacheDir, bool staleEnable)
        {
            StringBuilder sb = new StringBuilder().Append("http://").Append(httpHost).Append(':').Append(httpPort).Append("/app?native=").Append(appid);
            if (additionalQuery.Length > 0)
            {
                if (!additionalQuery.StartsWith("&"))
                    sb.Append("&");
                sb.Append(additionalQuery);
            }
            JSONDecoder decoder = new JSONDecoder();
            new HttpClient(sb.ToString(), timeout, maxsize, cacheDir, staleEnable).transfer(new CharSink(decoder));
            List<ServiceInfo> services = new List<ServiceInfo>();
            foreach (JSON json in decoder.get().get("services").ToArray())
                services.Add(new ServiceInfo(appid, json));
            return services;
        }
        public static List<ServiceInfo> loadServiceInfos(string httpHost, int httpPort, int appid, long timeout, int maxsize, string cacheDir, bool staleEnable)
        {
            return loadServiceInfos(httpHost, httpPort, appid, "", timeout, maxsize, cacheDir, staleEnable);
        }
        private static EndpointManagerImpl defaultEndpointManager;
        internal static void setDefaultEndpointManager(EndpointManagerImpl manager)
        {
            Interlocked.CompareExchange(ref defaultEndpointManager, manager, null);
        }
        internal static void clearDefaultEndpointManager(EndpointManagerImpl manager)
        {
            Interlocked.CompareExchange(ref defaultEndpointManager, null, manager);
        }
        public static EndpointManager getDefaultEndpointManager()
        {
            return defaultEndpointManager;
        }
    }
    public interface EndpointConfig : Config
    {
        int getDHGroup();
        ClientManagerConfig getClientManagerConfig();
        LoginConfig getLoginConfig();
        bool isPingServerOnly();
        bool auanyService();
        bool keepAlive();
        State getEndpointState();
        IDictionary<int, IDictionary<short, Type>> getStaticViewClasses();
        ICollection<int> getVariantProviderIds();
        ScriptEngineHandle getScriptEngineHandle();
    }
    public interface EndpointConfigBuilder
    {
        EndpointConfigBuilder inputBufferSize(int inputBufferSize);
        EndpointConfigBuilder outputBufferSize(int outputBufferSize);
        EndpointConfigBuilder executor(Executor executor);
        EndpointConfigBuilder auanyService(bool used);
        EndpointConfigBuilder keepAlive(bool used);
        EndpointConfigBuilder endpointState(params State[] states);
        EndpointConfigBuilder staticViewClasses(params View.StaticManager[] managers);
        EndpointConfigBuilder variantProviderIds(params int[] pvids);
        EndpointConfigBuilder scriptEngineHandle(ScriptEngineHandle handle);
        EndpointConfig build();
    }
    internal class EndpointConfigBuilderImpl : EndpointConfigBuilder
    {
        internal readonly string serverIp;
        internal readonly int serverPort;
        internal readonly LoginConfig loginConfig;
        internal readonly bool pingServerOnly;
        internal volatile bool auanyServciceUsed = true;
        internal volatile bool keepAlivedUsed = true;
        internal volatile int _outputBufferSize = 8 * 1024;
        internal volatile int _inputBufferSize = 8 * 1024;
        internal volatile Executor _executor;
        internal volatile State _endpointState = new State();
        internal readonly IDictionary<int, IDictionary<short, Type>> _svclasses = new Dictionary<int, IDictionary<short, Type>>();
        internal ICollection<int> _variantpvids = new HashSet<int>();
        internal ScriptEngineHandle _scriptEngineHandle;
        private ClientManagerConfig clientConfig;
        public EndpointConfigBuilderImpl(string serverIp, int serverPort, LoginConfig loginConfig, bool pingServerOnly)
        {
            this.serverIp = serverIp;
            this.serverPort = serverPort;
            this.loginConfig = loginConfig;
            this.pingServerOnly = pingServerOnly;
            this._executor = Engine.getApplicationExecutor().getExecutor(this);
            this.clientConfig = new DefaultClientManagerConfig(this);
            this._endpointState.merge(limax.endpoint.states.Endpoint.EndpointClient);
        }
        public EndpointConfigBuilder inputBufferSize(int inputBufferSize)
        {
            this._inputBufferSize = inputBufferSize;
            return this;
        }
        public EndpointConfigBuilder outputBufferSize(int outputBufferSize)
        {
            this._outputBufferSize = outputBufferSize;
            return this;
        }
        public EndpointConfigBuilder endpointState(params State[] states)
        {
            _endpointState = new State();
            _endpointState.merge(limax.endpoint.states.Endpoint.EndpointClient);
            foreach (State state in states)
                _endpointState.merge(state);
            return this;
        }
        public EndpointConfigBuilder variantProviderIds(params int[] pvids)
        {
            _variantpvids.Clear();
            foreach (int id in pvids)
                _variantpvids.Add(id);
            return this;
        }
        public EndpointConfigBuilder staticViewClasses(params View.StaticManager[] managers)
        {
            _svclasses.Clear();
            foreach (View.StaticManager m in managers)
                _svclasses.Add(m.getProviderId(), m.getClasses());
            return this;
        }
        public EndpointConfigBuilder scriptEngineHandle(ScriptEngineHandle handle)
        {
            _scriptEngineHandle = handle;
            return this;
        }
        public EndpointConfigBuilder executor(Executor executor)
        {
            this._executor = executor;
            return this;
        }
        public EndpointConfigBuilder auanyService(bool used)
        {
            this.auanyServciceUsed = used;
            return this;
        }
        public EndpointConfigBuilder keepAlive(bool used)
        {
            this.keepAlivedUsed = used;
            return this;
        }
        private class DefaultClientManagerConfig : ClientManagerConfig
        {
            private readonly EndpointConfigBuilderImpl impl;
            internal DefaultClientManagerConfig(EndpointConfigBuilderImpl impl)
            {
                this.impl = impl;
            }
            public string getName()
            {
                return "Endpoint";
            }
            public int getInputBufferSize()
            {
                return impl._inputBufferSize;
            }
            public int getOutputBufferSize()
            {
                return impl._outputBufferSize;
            }
            public bool isCheckOutputBuffer()
            {
                return true;
            }
            public byte[] getOutputSecurityBytes()
            {
                return null;
            }
            public byte[] getInputSecurityBytes()
            {
                return null;
            }
            public bool isOutputCompress()
            {
                return false;
            }
            public bool isInputCompress()
            {
                return false;
            }
            public State getDefaultState()
            {
                return limax.endpoint.states.Endpoint.getDefaultState();
            }
            public Dispatcher getDispatcher()
            {
                return new Dispatcher(impl._executor);
            }
            public IPEndPoint getPeerAddress()
            {
                return new IPEndPoint(IPAddress.Parse(impl.serverIp), impl.serverPort);
            }
            public bool isAutoReconnect()
            {
                return false;
            }
            public long getConnectTimeout()
            {
                return 5000;
            }
        };
        private class DefaultEndpointConfig : EndpointConfig
        {
            private readonly EndpointConfigBuilderImpl impl;
            internal DefaultEndpointConfig(EndpointConfigBuilderImpl impl)
            {
                this.impl = impl;
            }
            public int getDHGroup()
            {
                return 2;
            }
            public ClientManagerConfig getClientManagerConfig()
            {
                return impl.clientConfig;
            }
            public LoginConfig getLoginConfig()
            {
                return impl.loginConfig;
            }
            public bool isPingServerOnly()
            {
                return impl.pingServerOnly;
            }
            public bool auanyService()
            {
                return impl.auanyServciceUsed;
            }
            public bool keepAlive()
            {
                return impl.keepAlivedUsed;
            }
            public Dispatcher getDispatcher()
            {
                return new Dispatcher(impl._executor);
            }
            public State getEndpointState()
            {
                return impl._endpointState;
            }
            public IDictionary<int, IDictionary<short, Type>> getStaticViewClasses()
            {
                return impl._svclasses;
            }
            public ICollection<int> getVariantProviderIds()
            {
                return impl._variantpvids;
            }
            public ScriptEngineHandle getScriptEngineHandle()
            {
                return impl._scriptEngineHandle;
            }
        }
        public EndpointConfig build()
        {
            return new DefaultEndpointConfig(this);
        }
    }
    public interface EndpointListener : ClientListener
    {
        void onSocketConnected();
        void onKeyExchangeDone();
        void onKeepAlived(int ms);
        void onErrorOccured(int source, int code, Exception exception);
    }
    public interface EndpointManager : ClientManager
    {
        long getSessionID();
        long getAccountFlags();
        ViewContext getViewContext(int pvid, ViewContextKind kind);
    }
    internal class EndpointManagerImpl : EndpointManager, ClientListener, SupportDispatch
    {
        private class ViewContextMap
        {
            private readonly IDictionary<int, ICollection<AbstractViewContext>> map = new Dictionary<int, ICollection<AbstractViewContext>>();
            internal void put(int pvid, AbstractViewContext vc)
            {
                ICollection<AbstractViewContext> c;
                if (!map.TryGetValue(pvid, out c))
                    map.Add(pvid, c = new List<AbstractViewContext>());
                c.Add(vc);
            }
            internal void onSyncViewToClients(SyncViewToClients protocol)
            {
                ICollection<AbstractViewContext> c;
                if (map.TryGetValue(protocol.providerid, out c))
                    foreach (AbstractViewContext vc in c)
                        vc.onSyncViewToClients(protocol);
            }
            internal void clear()
            {
                foreach (ICollection<AbstractViewContext> c in map.Values)
                    foreach (AbstractViewContext vc in c)
                        try { vc.clear(); }
                        catch (Exception) { }
                map.Clear();
            }
            internal ViewContext getViewContext(int pvid, ViewContextKind kind)
            {
                ICollection<AbstractViewContext> c;
                if (map.TryGetValue(pvid, out c))
                    foreach (AbstractViewContext vc in c)
                        if (kind == vc.getKind())
                            return vc;
                return null;
            }
        }
        internal ClientManager manager;
        private readonly EndpointConfig config;
        private readonly EndpointListener listener;
        private readonly IDictionary<int, byte> pvids;
        private readonly ScriptExchange scriptExchange;
        private readonly ViewContextMap viewContextMap = new ViewContextMap();
        enum LoginStatus
        {
            LOGINING, LOGINED_NOTIFY, LOGINED_DONE,
        }
        private volatile LoginStatus loginstatus = LoginStatus.LOGINING;
        private readonly KeepAlive keepalive;
        private BigInteger dhRandom;
        private volatile Transport transportsaved = null;
        private long sessionid = -1;
        private long accountflags = 0;
        public EndpointManagerImpl(EndpointConfig config, EndpointListener listener, IDictionary<int, byte> pvids)
        {
            this.config = config;
            this.listener = listener;
            this.pvids = pvids;
            this.manager = (ClientManager)Engine.add(config.getClientManagerConfig(), this, this);
            this.keepalive = config.keepAlive() ? KeepAliveImpl.create(this) : KeepAliveHollow.instance;
            this.scriptExchange = config.getScriptEngineHandle() != null ? new ScriptExchange(
                    this, config.getScriptEngineHandle()) : null;
            if (listener is TunnelSupport)
                (listener as TunnelSupport).registerTunnelSender((providerid, label, data) => new Tunnel(providerid, 0, label, data).send(manager.getTransport()));
        }
        public Transport getTransport()
        {
            return manager.getTransport();
        }
        public void close()
        {
            if (Engine.remove(this))
                return;
            manager.close();
        }
        public void close(Transport transport)
        {
            manager.close(transport);
        }
        public Listener getListener()
        {
            return listener;
        }
        private interface KeepAlive : IDisposable
        {
            void startPingAndKeepAlive(Transport transport);
            void process(PingAndKeepAlive p);
            bool isTimeout();
        }
        private sealed class KeepAliveHollow : KeepAlive
        {
            private KeepAliveHollow() {}
            public void startPingAndKeepAlive(Transport transport) {}
            public void process(PingAndKeepAlive p) {}
            public bool isTimeout() { return false; }
            public void Dispose() {}

            internal static KeepAlive instance = new KeepAliveHollow();
        }
        private sealed class KeepAliveImpl : KeepAlive
        {
            private const int PING_TIMEOUT = 5;
            private const int KEEP_ALIVE_DELAY = 50;
            private readonly EndpointManagerImpl impl;
            private readonly Timer timer;
            private volatile bool wait_response;
            private volatile bool timeout = false;

            internal static KeepAlive create(EndpointManagerImpl impl)
            {
                return new KeepAliveImpl(impl);
            }
            private KeepAliveImpl(EndpointManagerImpl impl)
            {
                this.impl = impl;
                this.timer = new Timer(_ =>
                {
                    if (wait_response)
                    {
                        timeout = true;
                        Transport t = impl.getTransport();
                        if (t != null)
                            impl.close(t);
                    }
                    else
                    {
                        try
                        {
                            startPingAndKeepAlive(impl.getTransport());
                        }
                        catch (Exception e)
                        {
                            if (Trace.isErrorEnabled())
                                Trace.error("startPingAndKeepAlive", e);
                        }
                    }
                });
            }
            public void startPingAndKeepAlive(Transport transport)
            {
                new PingAndKeepAlive(DateTime.Now.Ticks / 10000).send(transport);
                wait_response = true;
                timer.Change(PING_TIMEOUT * 1000, Timeout.Infinite);
            }
            public void process(PingAndKeepAlive p)
            {
                if (timeout)
                    return;
                impl.listener.onKeepAlived((int)(DateTime.Now.Ticks / 10000 - p.timestamp));
                if (impl.config.isPingServerOnly())
                    return;
                wait_response = false;
                timer.Change(KEEP_ALIVE_DELAY * 1000, Timeout.Infinite);
            }
            public void Dispose() { timer.Dispose(); }
            public bool isTimeout() { return timeout; }
        }
        public void dispatch(Action runnable)
        {
            ((SupportDispatch)manager).dispatch(runnable);
        }
        public Manager getWrapperManager()
        {
            return null;
        }
        public void onManagerInitialized(Manager manager, Config config)
        {
            listener.onManagerInitialized(this, this.config);
        }
        public void onManagerUninitialized(Manager manager)
        {
            if (LoginStatus.LOGINED_DONE == loginstatus)
            {
                try
                {
                    keepalive.Dispose();
                    listener.onTransportRemoved(transportsaved);
                }
                catch (Exception e)
                {
                    if (Trace.isErrorEnabled())
                        Trace.error("listener.onTransportRemoved", e);
                }
            }
            if (LoginStatus.LOGINED_DONE == loginstatus || LoginStatus.LOGINED_NOTIFY == loginstatus)
            {
                Endpoint.clearDefaultEndpointManager(this);
                viewContextMap.clear();
                if (scriptExchange != null)
                    scriptExchange.onUnload();
            }
            listener.onManagerUninitialized(this);
        }
        public void onTransportAdded(Transport transport)
        {
            listener.onSocketConnected();
            if (config.isPingServerOnly())
            {
                keepalive.startPingAndKeepAlive(transport);
            }
            else
            {
                dhRandom = Helper.makeDHRandom();
                byte[] dh_data = Helper.generateDHResponse(config.getDHGroup(), dhRandom).ToByteArray();
                Array.Reverse(dh_data);
                new CHandShake((byte)config.getDHGroup(), Octets.wrap(dh_data)).send(transport);
            }
        }
        public void onTransportRemoved(Transport transport)
        {
            if (keepalive.isTimeout())
                onErrorOccured(ErrorSource.ENDPOINT, ErrorCodes.ENDPOINT_PING_TIMEOUT);
            if (LoginStatus.LOGINING == loginstatus)
            {
                if (!config.isPingServerOnly())
                    onAbort(transport);
            }
            else
                transportsaved = transport;
        }
        private void onErrorOccured(int source, int code)
        {
            listener.onErrorOccured(source, code, null);
        }
        public void onAbort(Transport transport)
        {
            listener.onAbort(transport);
        }
        public long getSessionID()
        {
            return sessionid;
        }
        public long getAccountFlags()
        {
            return accountflags;
        }
        public override string ToString()
        {
            return GetType().Name;
        }
        internal void process(SHandShake p)
        {
            StateTransport transport = (StateTransport)p.getTransport();
            byte[] dh_data = p.dh_data.getBytes();
            Array.Reverse(dh_data);
            byte[] material = Helper.computeDHKey(config.getDHGroup(), new BigInteger(dh_data), dhRandom).ToByteArray();
            Array.Reverse(material);
            byte[] key = transport.getPeerAddress().Address.GetAddressBytes();
            int half = material.Length / 2;
            HmacMD5 mac = new HmacMD5(key, 0, key.Length);
            mac.update(material, 0, half);
            transport.setOutputSecurityCodec(mac.digest(), p.c2sneedcompress);
            mac = new HmacMD5(key, 0, key.Length);
            mac.update(material, half, material.Length - half);
            transport.setInputSecurityCodec(mac.digest(), p.s2cneedcompress);
            transport.setState(limax.endpoint.states.Endpoint.EndpointSessionLogin);
            listener.onKeyExchangeDone();
            SessionLoginByToken protocol = new SessionLoginByToken();
            protocol.username = config.getLoginConfig().getUsername();
            protocol.token = config.getLoginConfig().getToken(Octets.wrap(material));
            protocol.platflag = config.getLoginConfig().getPlatflag();
            ScriptEngineHandle seh = config.getScriptEngineHandle();
            if (seh != null)
            {
                string dictionaryKeys = string.Join(",", seh.getDictionaryCache().keys());
                if (dictionaryKeys.Length > 0)
                    protocol.platflag += ";" + dictionaryKeys;
            }
            foreach (var i in pvids)
                protocol.pvids.Add(i.Key, i.Value);
            key = transport.getLocalAddress().Address.GetAddressBytes();
            protocol.report_ip.replace(key);
            protocol.report_port = (short)transport.getLocalAddress().Port;
            protocol.send(transport);
            transport.setState(config.getEndpointState());
        }
        internal void process(SessionKick p)
        {
            onErrorOccured(ErrorSource.LIMAX, p.error);
            if (scriptExchange != null)
                scriptExchange.onClose(p.error);
        }
        internal void process(PingAndKeepAlive p)
        {
            keepalive.process(p);
        }
        internal void process(ProviderLogin p)
        {
            ProviderLoginData logindata = null;
            ProviderLoginDataManager pldm = config.getLoginConfig().getProviderLoginDataManager();
            if (pldm != null)
                logindata = pldm.get(p.data.pvid);
            if (logindata == null)
                logindata = p.data;
            new ProviderLogin(logindata).send(p.getTransport());
        }
        internal void process(OnlineAnnounce p)
        {
            Transport transport = p.getTransport();
            if (ErrorSource.LIMAX == p.errorSource && ErrorCodes.SUCCEED == p.errorCode)
            {
                sessionid = p.sessionid;
                accountflags = p.flags;
                createStaticViewContextImpls();
                parseVariantDefines(p.variantdefines);
                if (scriptExchange != null)
                    scriptExchange.onLoad(p.scriptdefines);
                loginstatus = LoginStatus.LOGINED_NOTIFY;
                Endpoint.setDefaultEndpointManager(this);
                if (p.lmkdata.size() > 0)
                {
                    LmkUpdater lmkUpdater = config.getLoginConfig().getLmkUpdater();
                    if (lmkUpdater != null)
                        lmkUpdater(p.lmkdata, () => new Tunnel(AuanyService.providerId, 0, -1, new Octets()).send(transport));
                }
                listener.onTransportAdded(transport);
                loginstatus = LoginStatus.LOGINED_DONE;
                keepalive.startPingAndKeepAlive(transport);
            }
            else
            {
                onErrorOccured(p.errorSource, p.errorCode);
                close();
            }
        }
        private void parseVariantDefines(IDictionary<int, VariantDefines> variantdefines)
        {
            foreach (var e in variantdefines)
                viewContextMap.put(e.Key, VariantViewContextImpl.createInstance(e.Key, e.Value, this));
        }
        private void createStaticViewContextImpls()
        {
            var vm = limax.endpoint.auanyviews.ViewManager.createInstance(AuanyService.providerId);
            viewContextMap.put(vm.getProviderId(), new StaticViewContextImpl(vm.getProviderId(), vm.getClasses(), this));
            foreach (var e in config.getStaticViewClasses())
                viewContextMap.put(e.Key, new StaticViewContextImpl(e.Key, e.Value, this));
        }
        internal void process(SyncViewToClients p)
        {
            viewContextMap.onSyncViewToClients(p);
            if (scriptExchange != null)
                scriptExchange.onSyncViewToClients(p);
        }
        internal void process(Tunnel p)
        {
            if (listener is TunnelSupport)
                (listener as TunnelSupport).onTunnel(p.providerid, p.label, p.data);
            if (scriptExchange != null)
                scriptExchange.onTunnel(p.providerid, p.label, p.data);
        }
        public ViewContext getViewContext(int pvid, ViewContextKind type)
        {
            return viewContextMap.getViewContext(pvid, type);
        }
    }
    public sealed class LmkBundle
    {
        private static readonly int MAGIC = 0x4c4d4b30;
        private readonly int magic;
        private readonly Octets chain;
        private readonly BigInteger n;
        private readonly BigInteger d;
        private readonly BigInteger p;
        private readonly BigInteger q;
        private readonly BigInteger exp1;
        private readonly BigInteger exp2;
        private readonly BigInteger coef;
        private readonly Octets passphrase;
        private static BigInteger toBigInteger(byte[] data)
        {
            Array.Reverse(data);
            return new BigInteger(data);
        }
        private static byte[] toByteArray(BigInteger data)
        {
            byte[] r = data.ToByteArray();
            Array.Reverse(r);
            return r;
        }
        private BigInteger encrypt(BigInteger message)
        {
            if (coef == 0)
                return BigInteger.ModPow(message, d, n);
            BigInteger c1 = BigInteger.ModPow(message % p, exp1, p);
            BigInteger c2 = BigInteger.ModPow(message % q, exp2, q);
            return (c1 < c2 ? (c1 - c2) * coef % p + p : (c1 - c2) * coef % p) * q + c2;
        }
        private LmkBundle(Octets lmkdata, Octets passphrase)
        {
            if ((this.magic = OctetsStream.wrap(lmkdata).unmarshal_int()) != MAGIC)
                throw new MarshalException();
            OctetsStream os = new OctetsStream();
            Codec codec = new Decrypt(new SinkOctets(os), passphrase.getBytes());
            codec.update(lmkdata.array(), 4, lmkdata.size() - 4);
            codec.flush();
            this.chain = os.unmarshal_Octets();
            if (os.unmarshal_bool())
            {
                n = toBigInteger(os.unmarshal_bytes());
                d = toBigInteger(os.unmarshal_bytes());
            }
            else
            {
                p = toBigInteger(os.unmarshal_bytes());
                q = toBigInteger(os.unmarshal_bytes());
                exp1 = toBigInteger(os.unmarshal_bytes());
                exp2 = toBigInteger(os.unmarshal_bytes());
                coef = toBigInteger(os.unmarshal_bytes());
            }
            this.passphrase = new Octets(passphrase);
        }
        public static LmkBundle createInstance(Octets lmkdata, Octets passphrase)
        {
            return new LmkBundle(lmkdata, passphrase);
        }
        public Octets save(Octets passphrase)
        {
            OctetsStream os = new OctetsStream();
            os.marshal(chain);
            if (coef == 0)
            {
                os.marshal(true);
                os.marshal(toByteArray(n));
                os.marshal(toByteArray(d));
            }
            else
            {
                os.marshal(false);
                os.marshal(toByteArray(p));
                os.marshal(toByteArray(q));
                os.marshal(toByteArray(exp1));
                os.marshal(toByteArray(exp2));
                os.marshal(toByteArray(coef));
            }
            byte[] data = os.getBytes();
            os.clear();
            os.marshal(magic);
            Codec codec = new Encrypt(new SinkOctets(os), passphrase.getBytes());
            codec.update(data, 0, data.Length);
            codec.flush();
            return os;
        }
        public string sign(Octets message)
        {
            byte[] data = SHA256.digest(message.getBytes());
            Array.Reverse(data);
            Array.Resize(ref data, data.Length + 1);
            data = encrypt(new BigInteger(data)).ToByteArray();
            Array.Reverse(data);
            OctetsStream os = new OctetsStream();
            os.marshal(data);
            os.marshal(passphrase);
            return "LMK0" + Encoding.ASCII.GetString(Base64Encode.transform(os.getBytes()));
        }
        public string x509()
        {
            return Encoding.ASCII.GetString(Base64Encode.transform(chain.getBytes()));
        }
    }
    public delegate void LmkUpdater(Octets lmkdata, Action done);
    public sealed class LoginConfig
    {
        private readonly string username;
        private readonly string token;
        private readonly string platflag;
        private readonly LmkBundle lmkBundle;
        private readonly string subid;
        private readonly ProviderLoginDataManager pldm;
        private volatile LmkUpdater lmkUpdater;
        private LoginConfig(String username, String token, String platflag, LmkBundle lmkBundle, String subid,
                ProviderLoginDataManager pldm)
        {
            this.username = username;
            this.token = token;
            this.platflag = platflag;
            this.lmkBundle = lmkBundle;
            this.subid = subid;
            this.pldm = pldm;
        }
        public static LoginConfig plainLogin(string username, string token, string platflag, string subid, ProviderLoginDataManager pldm)
        {
            return new LoginConfig(username, token, platflag, null, subid, pldm);
        }
        public static LoginConfig plainLogin(string username, string token, string platflag, string subid)
        {
            return new LoginConfig(username, token, platflag, null, subid, null);
        }
        public static LoginConfig plainLogin(string username, string token, string platflag, ProviderLoginDataManager pldm)
        {
            return new LoginConfig(username, token, platflag, null, "", pldm);
        }
        public static LoginConfig plainLogin(string username, string token, string platflag)
        {
            return new LoginConfig(username, token, platflag, null, "", null);
        }
        private static string decodeMainCredential(string credential)
        {
            int pos = credential.IndexOf(',');
            return pos == -1 ? credential : credential.Substring(0, pos);
        }
        public static LoginConfig credentialLogin(string credential, string authcode, string subid, ProviderLoginDataManager pldm)
        {
            return new LoginConfig(decodeMainCredential(credential), authcode, "credential", null, subid, pldm);
        }
        public static LoginConfig credentialLogin(string credential, string authcode, string subid)
        {
            return new LoginConfig(decodeMainCredential(credential), authcode, "credential", null, subid, null);
        }
        public static LoginConfig credentialLogin(string credential, string authcode, ProviderLoginDataManager pldm)
        {
            return new LoginConfig(decodeMainCredential(credential), authcode, "credential", null, "", pldm);
        }
        public static LoginConfig credentialLogin(string credential, string authcode)
        {
            return new LoginConfig(decodeMainCredential(credential), authcode, "credential", null, "", null);
        }
        public static LoginConfig lmkLogin(LmkBundle lmkBundle, string subid, ProviderLoginDataManager pldm)
        {
            return new LoginConfig(null, null, "lmk", lmkBundle, subid, pldm);
        }
        public static LoginConfig lmkLogin(LmkBundle lmkBundle, string subid)
        {
            return new LoginConfig(null, null, "lmk", lmkBundle, subid, null);
        }
        public static LoginConfig lmkLogin(LmkBundle lmkBundle, ProviderLoginDataManager pldm)
        {
            return new LoginConfig(null, null, "lmk", lmkBundle, "", pldm);
        }
        public static LoginConfig lmkLogin(LmkBundle lmkBundle)
        {
            return new LoginConfig(null, null, "lmk", lmkBundle, "", null);
        }
        internal string getUsername()
        {
            return lmkBundle == null ? username : lmkBundle.x509();
        }
        internal string getToken(Octets nonce)
        {
            return lmkBundle == null ? token : lmkBundle.sign(nonce);
        }
        internal string getPlatflagRaw()
        {
            return platflag;
        }
        internal string getPlatflag()
        {
            return subid.Length == 0 ? getPlatflagRaw() : getPlatflagRaw() + ":" + subid;
        }
        internal ProviderLoginDataManager getProviderLoginDataManager()
        {
            return pldm;
        }
        public void setLmkUpdater(LmkUpdater lmkUpdater)
        {
            this.lmkUpdater = lmkUpdater;
        }
        internal LmkUpdater getLmkUpdater()
        {
            return lmkUpdater;
        }
    }
    public sealed class ProviderLoginDataManager
    {
        private readonly IDictionary<int, ProviderLoginData> map = new Dictionary<int, ProviderLoginData>();
        private ProviderLoginDataManager()
        {
        }
        public void add(int pvid, Octets unsafedata)
        {
            map.Add(pvid, new ProviderLoginData(pvid, ProviderLoginData.tUserData, 0, unsafedata));
        }
        public void add(int pvid, int label, Octets data)
        {
            map.Add(pvid, new ProviderLoginData(pvid, ProviderLoginData.tTunnelData, label, data));
        }
        public void add(int pvid, int label, string data)
        {
            add(pvid, label, Octets.wrap(Base64Decode.transform(Encoding.ASCII.GetBytes(data))));
        }
        public ICollection<int> getProviderIds()
        {
            return map.Keys;
        }
        public bool isSafe(int pvid)
        {
            return map[pvid].type == ProviderLoginData.tTunnelData;
        }
        public int getLabel(int pvid)
        {
            return map[pvid].label;
        }
        public Octets getData(int pvid)
        {
            return map[pvid].data;
        }
        public static ProviderLoginDataManager createInstance()
        {
            return new ProviderLoginDataManager();
        }
        internal ProviderLoginData get(int pvid)
        {
            ProviderLoginData logindata;
            return map.TryGetValue(pvid, out logindata) ? logindata : null;
        }
    }
    internal class ScriptExchange : ScriptSender
    {
        private readonly EndpointManagerImpl netmanager;
        private readonly ScriptEngineHandle handle;
        private readonly ISet<int> providers;
        private object closeReason;
        public ScriptExchange(EndpointManagerImpl netmanager, ScriptEngineHandle handle)
        {
            this.netmanager = netmanager;
            this.handle = handle;
            this.providers = handle.getProviders();
        }
        private void process(int t, Object p)
        {
            if (handle.action(t, p) == 3)
                netmanager.close();
        }
        internal void onLoad(string welcome)
        {
            lock (this)
            {
                handle.registerScriptSender(this);
                process(1, welcome);
            }
        }
        internal void onSyncViewToClients(SyncViewToClients protocol)
        {
            lock (this)
            {
                if (protocol.stringdata.Length > 0
                        && providers.Contains(protocol.providerid))
                    process(1, protocol.stringdata);
            }
        }
        internal void onTunnel(int providerid, int label, Octets data)
        {
            string s = Encoding.ASCII.GetString(Base64Encode.transform(data.getBytes()));
            StringBuilder sb = new StringBuilder();
            sb.Append('S').Append(Helper.toString36(s.Length)).Append(':').Append(s);
            sb.Append('I').Append(Helper.toString36(providerid)).Append(':');
            sb.Append('I').Append(Helper.toString36(label)).Append(':');
            process(1, sb.ToString());
        }
        internal void onClose(object closeReason)
        {
            lock (this)
            {
                this.closeReason = closeReason;
            }
        }
        internal void onUnload()
        {
            try
            {
                lock (this)
                {
                    handle.action(2, closeReason);
                }
            }
            catch (Exception)
            {
            }
        }
        public Exception send(string s)
        {
            SendControlToServer protocol = new SendControlToServer();
            protocol.providerid = -1;
            protocol.stringdata = s;
            try
            {
                protocol.send(netmanager.getTransport());
            }
            catch (Exception e)
            {
                return e;
            }
            return null;
        }
    }
    public sealed class ServiceInfo
    {
        private readonly static Random random = new Random((int)System.DateTime.Now.Ticks);
        private readonly List<SwitcherConfig> switchers = new List<SwitcherConfig>();
        internal int appid;
        private readonly int[] pvids;
        private readonly int[] payids;
        private readonly JSON[] userjsons;
        private readonly bool running;
        private readonly string optional;
        internal struct SwitcherConfig
        {
            internal readonly string host;
            internal readonly int port;
            internal SwitcherConfig(string host, int port)
            {
                this.host = host;
                this.port = port;
            }
        }
        internal ServiceInfo(int appid, JSON json)
        {
            this.appid = appid;
            foreach (JSON switcher in json.get("switchers").ToArray())
                switchers.Add(new SwitcherConfig(switcher.get("host").ToString(), switcher.get("port").intValue()));
            JSON[] ja = json.get("pvids").ToArray();
            pvids = new int[ja.Length];
            for (int i = 0; i < ja.Length; i++)
                pvids[i] = ja[i].intValue();
            payids = new int[ja.Length];
            for (int i = 0; i < ja.Length; i++)
                payids[i] = ja[i].intValue();
            ja = json.get("userjsons").ToArray();
            userjsons = new JSON[ja.Length];
            for (int i = 0; i < ja.Length; i++)
                userjsons[i] = JSON.parse(ja[i].ToString());
            running = json.get("running").booleanValue();
            optional = json.get("optional").ToString();
        }
        internal SwitcherConfig randomSwitcherConfig()
        {
            return switchers[random.Next(switchers.Count)];
        }
        public int[] getPvids()
        {
            return pvids;
        }
        public int[] getPayids()
        {
            return payids;
        }
        public JSON[] getUserJSONs()
        {
            return userjsons;
        }
        public bool isRunning()
        {
            return running;
        }
        public string getOptional()
        {
            return optional;
        }
    }
    internal class StaticViewContextImpl : AbstractViewContext
    {

        private readonly ViewContextImpl impl;
        private readonly IDictionary<short, Type> viewClasses;

        private class CreateViewInstance : ViewContextImpl.createViewInstance
        {
            private readonly int pvid;
            private readonly StaticViewContextImpl impl;
            public CreateViewInstance(int pvid, StaticViewContextImpl impl)
            {
                this.pvid = pvid;
                this.impl = impl;
            }
            public int getProviderId()
            {
                return pvid;
            }
            public View createView(short clsindex)
            {
                Type cls;
                if (!impl.viewClasses.TryGetValue(clsindex, out cls))
                    return null;
                ConstructorInfo constructor = cls.GetConstructor(BindingFlags.NonPublic | BindingFlags.Instance, null, new Type[] { typeof(ViewContext) }, null);
                return (View)constructor.Invoke(new object[] { impl });
            }
        }

        internal StaticViewContextImpl(int pvid, IDictionary<short, Type> map, EndpointManagerImpl netmanager)
        {
            viewClasses = new Dictionary<short, Type>(map);
            impl = new ViewContextImpl(new CreateViewInstance(pvid, this), netmanager);
            foreach (var cls in map.Values)
                cls.GetConstructor(BindingFlags.NonPublic | BindingFlags.Instance, null, new Type[] { typeof(ViewContext) }, null).Invoke(new object[] { this });
        }
        public override View getSessionOrGlobalView(short classindex)
        {
            return impl.getSesseionOrGlobalView(classindex);
        }
        public override TemporaryView findTemporaryView(short classindex, int instanceindex)
        {
            return impl.findTemporaryView(classindex, instanceindex);
        }
        public override EndpointManager getEndpointManager()
        {
            return impl.getEndpointManager();
        }
        public override int getProviderId()
        {
            return impl.getProviderId();
        }
        public override ViewContextKind getKind()
        {
            return ViewContextKind.Static;
        }
        internal override void onSyncViewToClients(SyncViewToClients protocol)
        {
            impl.onSyncViewToClients(protocol);
        }
        internal override void clear()
        {
            impl.clear();
        }
    }
    public abstract class TemporaryView : View
    {
        internal volatile int instanceindex;
        protected TemporaryView(ViewContext vc) : base(vc) { }
        public int getInstanceIndex()
        {
            return instanceindex;
        }
        protected abstract void onOpen(ICollection<long> sessionids);
        protected abstract void onClose();
        protected abstract void onAttach(long sessionid);
        protected abstract void detach(long sessionid, byte reason);
        protected abstract void onDetach(long sessionid, byte reason);
        internal override void doClose()
        {
            base.doClose();
            onClose();
        }
        internal void doOnOpen(ICollection<long> sessionids)
        {
            onOpen(sessionids);
        }
        internal void doOnClose()
        {
            onClose();
        }
        internal void doOnAttach(long sessionid)
        {
            onAttach(sessionid);
        }
        internal void doDetach(long sessionid, byte reason)
        {
            detach(sessionid, reason);
        }

        public override string ToString()
        {
            return "[class = " + GetType().Name + " ProviderId = " + getViewContext().getProviderId() + " classindex = " + getClassIndex() + " instanceindex = " + instanceindex + "]";
        }
    }
    public delegate void TunnelSender(int providerid, int label, Octets data);
    public interface TunnelSupport
    {
        void onTunnel(int provider, int label, Octets data);
        void registerTunnelSender(TunnelSender sender);
    }
    internal class VariantViewContextImpl : AbstractViewContext, SupportManageVariant
    {
        private class HollowTempViewHandler : TemporaryViewHandler
        {
            public void onOpen(VariantView view, ICollection<long> sessionids)
            {
            }
            public void onClose(VariantView view)
            {
            }
            public void onAttach(VariantView view, long sessionid)
            {
            }
            public void onDetach(VariantView view, long sessionid, int reason)
            {
            }
        }
        private readonly static TemporaryViewHandler hollowTempViewHandler = new HollowTempViewHandler();

        private readonly ViewContextImpl impl;
        private readonly IDictionary<short, object> viewdefines;
        private readonly ISet<string> tempviewnames;
        private readonly IDictionary<string, short> nametoidmap = new Dictionary<string, short>();
        private readonly IDictionary<string, TemporaryViewHandler> tempviewhandler = new Dictionary<string, TemporaryViewHandler>();
        private readonly VariantManager variantmanager;
        private readonly MethodInfo viewCreator;
        private class CreateViewInstance : ViewContextImpl.createViewInstance
        {
            private readonly int pvid;
            private readonly VariantViewContextImpl impl;
            internal CreateViewInstance(int pvid, VariantViewContextImpl impl)
            {
                this.pvid = pvid;
                this.impl = impl;
            }
            public int getProviderId()
            {
                return pvid;
            }
            public View createView(short classindex)
            {
                return impl.createViewInstance(classindex, impl);
            }

        }

        private VariantViewContextImpl(int pvid, IDictionary<short, object> viewdefines, ISet<string> tempviewnames, EndpointManagerImpl netmanager)
        {
            this.impl = new ViewContextImpl(new CreateViewInstance(pvid, this), netmanager);
            this.viewdefines = viewdefines;
            this.tempviewnames = tempviewnames;
            foreach (var e in viewdefines)
                nametoidmap.Add(e.Value.ToString(), e.Key);
            viewCreator = typeof(VariantManager).GetMethod("createDynamicViewInstance", BindingFlags.NonPublic | BindingFlags.Static, null, new Type[] { typeof(int), typeof(object), typeof(TemporaryViewHandler), typeof(ViewContext) }, null);
            ConstructorInfo constructor = typeof(VariantManager).GetConstructor(BindingFlags.NonPublic | BindingFlags.Instance, null, new Type[] { typeof(SupportManageVariant), typeof(ViewContext) }, null);
            variantmanager = (VariantManager)constructor.Invoke(new object[] { this, this });
        }

        internal View createViewInstance(short index, ViewContext vc)
        {
            object vd;
            if (!viewdefines.TryGetValue(index, out vd))
                return null;
            return (View)viewCreator.Invoke(null, new object[] { impl.getProviderId(), vd, getTemporaryViewHandler(vd.ToString(), true), vc });
        }
        public short getViewClassIndex(string name)
        {
            short id;
            if (nametoidmap.TryGetValue(name, out id)) return id;
            throw new Exception("getViewClassIndex id not found[] name " + name);
        }
        public void setTemporaryViewHandler(string name, TemporaryViewHandler handler)
        {
            lock (this)
            {
                if (!tempviewnames.Contains(name))
                    throw new Exception("temporary view named \"" + name + "\" not exist");
                tempviewhandler[name] = handler;
            }
        }
        public TemporaryViewHandler getTemporaryViewHandler(string name, bool returnDeafault)
        {
            lock (this)
            {
                TemporaryViewHandler handler;
                if (tempviewhandler.TryGetValue(name, out handler))
                    return handler;
                return returnDeafault ? hollowTempViewHandler : null;
            }
        }
        public VariantManager getVariantManager()
        {
            return variantmanager;
        }
        public override ViewContextKind getKind()
        {
            return ViewContextKind.Variant;
        }
        public override View getSessionOrGlobalView(short classindex)
        {
            return impl.getSesseionOrGlobalView(classindex);
        }
        public override TemporaryView findTemporaryView(short classindex, int instanceindex)
        {
            return impl.findTemporaryView(classindex, instanceindex);
        }
        public override EndpointManager getEndpointManager()
        {
            return impl.getEndpointManager();
        }
        public override int getProviderId()
        {
            return impl.getProviderId();
        }
        internal override void onSyncViewToClients(SyncViewToClients protocol)
        {
            impl.onSyncViewToClients(protocol);
        }
        internal override void clear()
        {
            impl.clear();
        }
        internal static VariantViewContextImpl createInstance(int pvid, VariantDefines defines, EndpointManagerImpl netmanager)
        {
            IDictionary<short, object> viewdefines = new Dictionary<short, object>();
            ISet<string> tempviewnames = new HashSet<string>();
            MethodInfo method = typeof(VariantManager).GetMethod("parseViewDefines", BindingFlags.NonPublic | BindingFlags.Static, null, new Type[] { typeof(VariantDefines), typeof(IDictionary<short, object>), typeof(ISet<string>) }, null);
            method.Invoke(null, new object[] { defines, viewdefines, tempviewnames });
            return new VariantViewContextImpl(pvid, viewdefines, tempviewnames, netmanager);
        }
    }
    public abstract class View
    {
        private readonly AbstractViewContext viewContext;
        protected View(ViewContext vc)
        {
            viewContext = (AbstractViewContext)vc;
        }
        public ViewContext getViewContext()
        {
            return viewContext;
        }
        public void sendMessage(string msg)
        {
            viewContext.sendMessage(this, msg);
        }
        public abstract short getClassIndex();
        protected abstract void onData(long sessionid, byte index, byte field, Octets data, Octets dataremoved);
        internal void doOnData(long sessionid, byte index, byte field, Octets data, Octets dataremoved)
        {
            onData(sessionid, index, field, data, dataremoved);
        }
        public override string ToString()
        {
            return "[class = " + GetType().Name + " ProviderId = " + viewContext.getProviderId() + " classindex = " + getClassIndex() + "]";
        }
        abstract protected ISet<string> getFieldNames();
        private readonly IDictionary<string, ViewChangedListener> listeners = new Dictionary<string, ViewChangedListener>();
        public Action registerListener(ViewChangedListener listener)
        {
            lock (this)
            {
                foreach (string name in getFieldNames())
                {
                    if (listeners.ContainsKey(name))
                        listeners[name] += listener;
                    else
                        listeners[name] = listener;
                }
                return () =>
                {
                    lock (this)
                    {
                        foreach (string name in getFieldNames())
                            listeners[name] -= listener;
                    }
                };
            }
        }
        public Action registerListener(string fieldname, ViewChangedListener listener)
        {
            lock (this)
            {
                if (!getFieldNames().Contains(fieldname))
                    return () => { };
                if (listeners.ContainsKey(fieldname))
                    listeners[fieldname] += listener;
                else
                    listeners[fieldname] = listener;
                return () =>
                {
                    lock (this)
                    {
                        listeners[fieldname] -= listener;
                    }
                };
            }
        }
        protected void onViewChanged(long sessionid, string fieldname, object value, ViewChangedType type)
        {
            ViewChangedListener l;
            if (listeners.TryGetValue(fieldname, out l))
                l(new ViewChangedEvent(this, sessionid, fieldname, value, type));
        }
        internal virtual void doClose()
        {
            lock (this)
            {
                listeners.Clear();
            }
        }
        public abstract class Control : Marshal
        {
            private readonly View view;
            protected Control(View view)
            {
                this.view = view;
            }
            public abstract short getViewClassIndex();
            private int getViewInstanceIndex()
            {
                var tv = view as TemporaryView;
                return tv == null ? 0 : tv.getInstanceIndex();
            }
            public abstract byte getControlIndex();
            public void send()
            {
                SendControlToServer protocol = new SendControlToServer();
                protocol.providerid = view.getViewContext().getProviderId();
                protocol.classindex = this.getViewClassIndex();
                protocol.instanceindex = this.getViewInstanceIndex();
                protocol.controlindex = this.getControlIndex();
                protocol.controlparameter = new OctetsStream().marshal(this);
                protocol.send(view.getViewContext().getEndpointManager().getTransport());
            }
            public abstract OctetsStream marshal(OctetsStream os);
            public abstract OctetsStream unmarshal(OctetsStream os);
        }
        public interface StaticManager
        {
            int getProviderId();
            IDictionary<short, Type> getClasses();
        }
    }
    public struct ViewChangedEvent
    {
        readonly View _view;
        readonly long _sessionid;
        readonly string _fieldname;
        readonly object _value;
        readonly ViewChangedType _type;
        internal ViewChangedEvent(View view, long sessionid, string fieldname, object value, ViewChangedType type)
        {
            _view = view;
            _sessionid = sessionid;
            _fieldname = fieldname;
            _value = value;
            _type = type;
        }
        public View view { get { return _view; } }
        public long sessionid { get { return _sessionid; } }
        public string fieldname { get { return _fieldname; } }
        public object value { get { return _value; } }
        public ViewChangedType type { get { return _type; } }
        public override string ToString()
        {
            return _view + " " + _sessionid + " " + _fieldname + " " + _value + " " + type;
        }
    }
    public delegate void ViewChangedListener(ViewChangedEvent e);
    public enum ViewChangedType
    {
        NEW, REPLACE, TOUCH, DELETE
    }
    public enum ViewContextKind
    {
        Static, Variant, Script
    }
    public interface ViewContext
    {
        View getSessionOrGlobalView(short classindex);
        TemporaryView findTemporaryView(short classindex, int instanceindex);
        EndpointManager getEndpointManager();
        int getProviderId();
        ViewContextKind getKind();
        void sendMessage(View view, string msg);
    }
    internal class ViewContextImpl
    {
        internal interface createViewInstance
        {
            int getProviderId();
            View createView(short classindex);
        }
        private static long getTemporaryViewKey(short classindex, int instanceindex)
        {
            return ((classindex & 0xFFFFL) << 32) | (instanceindex & 0xFFFFFFFFL);
        }
        internal readonly EndpointManagerImpl netmanager;
        private readonly createViewInstance createview;
        private readonly IDictionary<short, View> viewmap = new Dictionary<short, View>();
        private readonly IDictionary<long, TemporaryView> tempviewmap = new Dictionary<long, TemporaryView>();
        internal ViewContextImpl(createViewInstance createview,
                EndpointManagerImpl netmanager)
        {
            this.netmanager = netmanager;
            this.createview = createview;
        }
        internal int getProviderId()
        {
            return createview.getProviderId();
        }
        internal void clear()
        {
            lock (this)
            {
                foreach (var v in tempviewmap.Values)
                    v.doClose();
                foreach (var v in viewmap.Values)
                    v.doClose();
                viewmap.Clear();
                tempviewmap.Clear();
            }
        }
        internal TemporaryView findTemporaryView(short classindex, int instanceindex)
        {
            lock (this)
            {
                TemporaryView view;
                return tempviewmap.TryGetValue(getTemporaryViewKey(classindex, instanceindex), out view) ? view : null;
            }
        }
        internal void onSyncViewToClients(SyncViewToClients p)
        {
            switch (p.synctype)
            {
                case SyncViewToClients.DT_VIEW_DATA:
                    {
                        View view = getViewInstance(p.classindex);
                        lock (view)
                        {
                            foreach (ViewVariableData var in p.vardatas)
                                view.doOnData(netmanager.getSessionID(), var.index, var.field, var.data, var.dataremoved);
                        }
                        break;
                    }
                case SyncViewToClients.DT_TEMPORARY_INIT_DATA:
                    {
                        TemporaryView view = getTemporaryView(p.classindex, p.instanceindex);
                        lock (view)
                        {
                            ICollection<long> sessionids = new HashSet<long>();
                            foreach (ViewMemberData item in p.members)
                                sessionids.Add(item.sessionid);
                            view.doOnOpen(sessionids);
                            foreach (ViewVariableData var in p.vardatas)
                                view.doOnData(netmanager.getSessionID(), var.index, var.field, var.data, var.dataremoved);
                            foreach (ViewMemberData var in p.members)
                                if ((var.vardata.index & 0x80) == 0)
                                    view.doOnData(var.sessionid, var.vardata.index, var.vardata.field, var.vardata.data, var.vardata.dataremoved);
                        }
                        break;
                    }
                case SyncViewToClients.DT_TEMPORARY_DATA:
                    {
                        TemporaryView view = findTemporaryView(p.classindex, p.instanceindex);
                        if (view != null)
                        {
                            lock (view)
                            {
                                foreach (ViewVariableData var in p.vardatas)
                                    view.doOnData(netmanager.getSessionID(), var.index, var.field, var.data, var.dataremoved);
                                foreach (ViewMemberData var in p.members)
                                    view.doOnData(var.sessionid, var.vardata.index, var.vardata.field, var.vardata.data, var.vardata.dataremoved);
                            }
                        }
                        break;
                    }
                case SyncViewToClients.DT_TEMPORARY_ATTACH:
                    {
                        TemporaryView view = findTemporaryView(p.classindex, p.instanceindex);
                        if (view != null && p.members.Count >= 1)
                        {
                            lock (view)
                            {
                                view.doOnAttach(p.members[0].sessionid);
                                foreach (ViewMemberData var in p.members)
                                    if ((var.vardata.index & 0x80) == 0)
                                        view.doOnData(var.sessionid, var.vardata.index, var.vardata.field, var.vardata.data, var.vardata.dataremoved);
                            }
                        }
                        break;
                    }
                case SyncViewToClients.DT_TEMPORARY_DETACH:
                    {
                        TemporaryView view = findTemporaryView(p.classindex, p.instanceindex);
                        if (view != null && p.members.Count == 1)
                        {
                            ViewMemberData e = p.members[0];
                            lock (view)
                            {
                                view.doDetach(e.sessionid, e.vardata.index);
                            }
                        }
                        break;
                    }
                case SyncViewToClients.DT_TEMPORARY_CLOSE:
                    {
                        TemporaryView view = closeTemporaryView(p.classindex, p.instanceindex);
                        if (view != null)
                            lock (view)
                            {
                                view.doClose();
                            }
                        break;
                    }
            }
        }
        private View getViewInstance(short classindex)
        {
            lock (this)
            {
                View view;
                if (viewmap.TryGetValue(classindex, out view))
                    return view;
                view = createview.createView(classindex);
                if (null == view)
                    throw new Exception("unknown view class pvid = " + createview.getProviderId() + " classindex = " + classindex);
                return view;
            }
        }
        private TemporaryView getTemporaryView(short classindex, int instanceindex)
        {
            lock (this)
            {
                long key = getTemporaryViewKey(classindex, instanceindex);
                TemporaryView view;
                if (!tempviewmap.TryGetValue(key, out view))
                {
                    view = (TemporaryView)createview.createView(classindex);
                    if (null == view)
                        throw new Exception("unknown temp view class pvid = " + createview.getProviderId() + " classindex = " + classindex + " instanceindex = " + instanceindex);
                    view.instanceindex = instanceindex;
                    tempviewmap.Add(key, view);
                }
                return view;
            }
        }
        private TemporaryView closeTemporaryView(short classindex, int instanceindex)
        {
            lock (this)
            {
                long key = getTemporaryViewKey(classindex, instanceindex);
                TemporaryView view;
                if (tempviewmap.TryGetValue(key, out view))
                {
                    tempviewmap.Remove(key);
                    return view;
                }
                return null;
            }
        }
        internal View getSesseionOrGlobalView(short classindex)
        {
            lock (this)
            {
                View view;
                if (viewmap.TryGetValue(classindex, out view))
                    return view;
                view = createview.createView(classindex);
                if (null != view)
                    viewmap.Add(view.getClassIndex(), view);
                return view;
            }
        }
        internal EndpointManager getEndpointManager()
        {
            return netmanager;
        }
    }
    public delegate void ViewVisitor<T>(T value);
    class WebSocketConnector : ScriptSender, LmkDataReceiver, Closeable
    {
        private enum ReadyState
        {
            CONNECTING, OPEN, CLOSING, CLOSED
        }
        private enum CloseStatus
        {
            CONNECTION_FAIL, ACTIVE_CLOSE, PASSIVE_CLOSE
        }
        private const int DHGroup = 2;
        private readonly LoginConfig loginConfig;
        private readonly ScriptEngineHandle handle;
        private readonly Executor executor;
        private readonly OctetsStream os = new OctetsStream();
        private readonly Queue<string> messages = new Queue<string>();
        private Timer timer;
        private int reportError;
        private Exception firstException;
        private readonly TcpClient tcpclient;
        private ReadyState readyState = ReadyState.CONNECTING;
        private readonly byte[] inbuffer = new byte[131072];
        private int stage;
        private StringBuilder sbhead = new StringBuilder();
        private byte[] key;
        private BigInteger dhRandom;
        private Codec isec;
        private Codec osec;
        private Octets ibuf = new Octets();
        private Octets obuf = new Octets();
        void rawSend(byte[] data)
        {
            try
            {
                NetworkStream ns = tcpclient.GetStream();
                ns.BeginWrite(data, 0, data.Length, ar =>
                {
                    try
                    {
                        ns.EndWrite(ar);
                    }
                    catch (Exception e)
                    {
                        close(e);
                    }
                }, null);
            }
            catch (Exception e)
            {
                close(e);
            }
        }
        void ready()
        {
            try
            {
                NetworkStream ns = tcpclient.GetStream();
                ns.BeginRead(inbuffer, 0, inbuffer.Length, ar =>
                {
                    try
                    {
                        int size = ns.EndRead(ar);
                        if (size > 0)
                        {
                            netin(inbuffer, size);
                            flush();
                            ready();
                        }
                        else
                            close(false);
                    }
                    catch (Exception e)
                    {
                        close(e);
                    }
                }, null);
            }
            catch (Exception e)
            {
                close(e);
            }
        }
        private void process(int t, object p)
        {
            try
            {
                switch (handle.action(t, p))
                {
                    case 2:
                        timer = new Timer(_ => send(" "), null, 50000, 50000);
                        break;
                    case 3:
                        close(false);
                        break;
                }
            }
            catch (Exception e)
            {
                close(e);
            }
        }
        void onopen()
        {
        }
        void onmessage(string message)
        {
            if (Char.IsDigit(message[0]))
                reportError = Int32.Parse(message);
            else
                executor(() => process(1, message));
        }
        void onclose(CloseStatus cs)
        {
            executor(() =>
            {
                if (timer != null)
                    timer.Dispose();
                process(2, cs.ToString() + " " + (firstException == null ? "" : firstException + " ") + reportError);
            });
        }
        public void onLmkData(string lmkdata, Action done)
        {
            if (lmkdata.Length > 0)
            {
                LmkUpdater lmkUpdater = loginConfig.getLmkUpdater();
                if (lmkUpdater != null)
                    lmkUpdater(Octets.wrap(Base64Decode.transform(Encoding.ASCII.GetBytes(lmkdata))), done);
            }
        }
        void netin(byte[] data, int _size)
        {
            lock (this)
            {
                switch (readyState)
                {
                    case ReadyState.CONNECTING:
                        for (int i = 0; i < _size; i++)
                        {
                            byte b = data[i];
                            sbhead.Append((char)b);
                            switch (stage)
                            {
                                case 0:
                                    stage = b == '\r' ? 1 : 0;
                                    break;
                                case 1:
                                    stage = b == '\n' ? 2 : 0;
                                    break;
                                case 2:
                                    stage = b == '\r' ? 3 : 0;
                                    break;
                                case 3:
                                    if (b == '\n')
                                    {
                                        string head = sbhead.ToString();
                                        string security = null;
                                        for (int spos = 0, epos; security == null && (epos = head.IndexOf('\n', spos)) != -1; spos = epos + 1)
                                        {
                                            int mpos = head.LastIndexOf(':', epos);
                                            if (mpos > spos && string.Compare(head.Substring(spos, mpos - spos).Trim(), "x-limax-security", true) == 0)
                                                security = head.Substring(mpos + 1, epos - mpos - 1).Trim();
                                        }
                                        if (security == null)
                                        {
                                            close(false);
                                            return;
                                        }
                                        byte[] dh_data = Base64Decode.transform(Encoding.UTF8.GetBytes(security));
                                        Array.Reverse(dh_data);
                                        byte[] material = Helper.computeDHKey(DHGroup, new BigInteger(dh_data), dhRandom).ToByteArray();
                                        Array.Reverse(material);
                                        int half = material.Length / 2;
                                        HmacMD5 mac = new HmacMD5(key, 0, key.Length);
                                        mac.update(material, 0, half);
                                        osec = new RFC2118Encode(new Encrypt(new SinkOctets(obuf), mac.digest()));
                                        mac = new HmacMD5(key, 0, key.Length);
                                        mac.update(material, half, material.Length - half);
                                        isec = new Decrypt(new RFC2118Decode(new SinkOctets(ibuf)), mac.digest());
                                        string pvids = String.Join(",", handle.getProviders());
                                        string keys = String.Join(",", handle.getDictionaryCache().keys());
                                        if (keys.Length > 0)
                                            keys = ";" + keys;
                                        string query = "/?username=" + loginConfig.getUsername() + "&token=" + loginConfig.getToken(Octets.wrap(material)) + "&platflag=" + loginConfig.getPlatflag() + keys + "&pvids=" + pvids;
                                        send(Encoding.UTF8.GetBytes(query));
                                        readyState = ReadyState.OPEN;
                                        onopen();
                                        return;
                                    }
                                    else
                                        stage = 0;
                                    break;
                            }
                        }
                        break;
                    case ReadyState.OPEN:
                        if (true)
                        {
                            isec.update(data, 0, _size);
                            isec.flush();
                            os.insert(os.size(), ibuf.getBytes());
                            ibuf.clear();
                            while (true)
                            {
                                os.begin();
                                try
                                {
                                    int opcode = os.unmarshal_byte() & 0xff;
                                    int len = os.unmarshal_byte() & 0x7f;
                                    switch (len)
                                    {
                                        case 126:
                                            len = os.unmarshal_short() & 0xffff;
                                            break;
                                        case 127:
                                            len = (int)os.unmarshal_long();
                                            break;
                                    }
                                    if (os.remain() >= len)
                                    {
                                        int pos = os.position();
                                        if (opcode == 0x81)
                                            messages.Enqueue(Encoding.UTF8.GetString(os.array(), pos, len));
                                        os.position(pos + len);
                                        os.commit();
                                    }
                                    else
                                    {
                                        os.rollback();
                                        break;
                                    }
                                }
                                catch (MarshalException)
                                {
                                    os.rollback();
                                    break;
                                }
                            }
                        }
                        break;
                }
            }
        }
        void flush()
        {
            foreach (var message in messages)
                onmessage(message);
            messages.Clear();
        }
        void send(byte[] data)
        {
            int len = data.Length;
            osec.update((byte)0x81);
            if (len < 126)
                osec.update((byte)(len | 0x80));
            else if (len < 65536)
            {
                osec.update((byte)254);
                osec.update((byte)(len >> 8));
                osec.update((byte)(len));
            }
            else
            {
                osec.update((byte)255);
                osec.update((byte)(len >> 56));
                osec.update((byte)(len >> 48));
                osec.update((byte)(len >> 40));
                osec.update((byte)(len >> 32));
                osec.update((byte)(len >> 24));
                osec.update((byte)(len >> 16));
                osec.update((byte)(len >> 8));
                osec.update((byte)(len));
            }
            osec.update((byte)0);
            osec.update((byte)0);
            osec.update((byte)0);
            osec.update((byte)0);
            osec.update(data, 0, data.Length);
            osec.flush();
            rawSend((new Octets(obuf)).getBytes());
            obuf.clear();
        }
        public Exception send(string s)
        {
            lock (this)
            {
                if (readyState != ReadyState.OPEN)
                    return null;
                try
                {
                    send(Encoding.UTF8.GetBytes(s));
                }
                catch (Exception e)
                {
                    return e;
                }
                return null;
            }
        }
        void setup(IPEndPoint local, IPEndPoint peer)
        {
            lock (this)
            {
                dhRandom = Helper.makeDHRandom();
                byte[] response = Helper.generateDHResponse(DHGroup, dhRandom).ToByteArray();
                Array.Reverse(response);
                StringBuilder sb = new StringBuilder("GET / HTTP/1.1\r\nConnection: Upgrade\r\nUpgrade: WebSocket\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: AQIDBAUGBwgJCgsMDQ4PEC==\r\nOrigin: null\r\n");
                sb.Append("X-Limax-Security: ").Append(DHGroup).Append(';').Append(Encoding.UTF8.GetString(Base64Encode.transform(response))).Append("\r\n\r\n");
                rawSend(Encoding.UTF8.GetBytes(sb.ToString()));
                key = peer.Address.GetAddressBytes();
                ready();
            }
        }
        void close(Exception e)
        {
            lock (this)
            {
                if (firstException == null)
                {
                    firstException = e;
                    close(false);
                }
            }
        }
        void close(bool active)
        {
            ReadyState readyState;
            lock (this)
            {
                readyState = this.readyState;
                if (readyState == ReadyState.CLOSED)
                    return;
                this.readyState = ReadyState.CLOSED;
            }
            if (active)
            {
                onclose(CloseStatus.ACTIVE_CLOSE);
            }
            else
            {
                switch (readyState)
                {
                    case ReadyState.OPEN:
                        onclose(CloseStatus.PASSIVE_CLOSE);
                        break;
                    case ReadyState.CONNECTING:
                        onclose(CloseStatus.CONNECTION_FAIL);
                        break;
                }
            }
            tcpclient.Close();
        }
        public void close()
        {
            if (Engine.remove(this))
                return;
            close(true);
        }
        public WebSocketConnector(string host, int port, LoginConfig loginConfig, ScriptEngineHandle handle, Executor executor)
        {
            this.loginConfig = loginConfig;
            this.handle = handle;
            this.executor = executor;
            this.stage = 0;
            handle.registerScriptSender(this);
            handle.registerLmkDataReceiver(this);
            if (loginConfig.getProviderLoginDataManager() != null)
                handle.registerProviderLoginDataManager(loginConfig.getProviderLoginDataManager());
            tcpclient = new TcpClient();
            tcpclient.BeginConnect(host, port, ar =>
            {
                try
                {
                    tcpclient.EndConnect(ar);
                    setup((IPEndPoint)tcpclient.Client.LocalEndPoint, (IPEndPoint)tcpclient.Client.RemoteEndPoint);
                }
                catch (Exception e)
                {
                    close(e);
                }
            }, this);
            Engine.add(this);
        }
    }
}
