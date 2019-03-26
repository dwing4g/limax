using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using limax.codec;
using limax.defines;
using limax.util;

namespace limax.net
{
    public interface Transport
    {
        IPEndPoint getPeerAddress();
        IPEndPoint getLocalAddress();
        Object getSessionObject();
        void setSessionObject(object obj);
        Manager getManager();
        Exception getCloseReason();
        void check(int type, int size);
        void sendTypedData(int type, Octets data);
    }
    public interface StateTransport : Transport
    {
        void setOutputSecurityCodec(byte[] key, bool compress);
        void setInputSecurityCodec(byte[] key, bool compress);
        void setState(State state);
        void resetAlarm(long milliseconds);
    }
    public interface Listener
    {
        void onManagerInitialized(Manager manager, Config config);
        void onManagerUninitialized(Manager manager);
        void onTransportAdded(Transport transport);
        void onTransportRemoved(Transport transport);
    }
    public interface ClientListener : Listener
    {
        void onAbort(Transport transport);
    }
    public interface Config { }
    public interface ManagerConfig : Config
    {
        string getName();
        int getInputBufferSize();
        int getOutputBufferSize();
        bool isCheckOutputBuffer();
        byte[] getOutputSecurityBytes();
        byte[] getInputSecurityBytes();
        bool isOutputCompress();
        bool isInputCompress();
        State getDefaultState();
        Dispatcher getDispatcher();
    }
    public interface ClientManagerConfig : ManagerConfig
    {
        IPEndPoint getPeerAddress();
        bool isAutoReconnect();
        long getConnectTimeout();
    }
    public abstract class Protocol : Marshal
    {
        private Transport transport;
        internal void setTransport(Transport transport)
        {
            this.transport = transport;
        }
        public abstract void process();
        internal void dispatch()
        {
            ((SupportDispatch)getManager()).dispatch(() =>
            {
                try { process(); }
                catch (Exception e)
                {
                    if (Trace.isErrorEnabled())
                        Trace.error("", e);
                    getManager().close(transport);
                }
            });
        }
        public Transport getTransport()
        {
            return transport;
        }
        public Manager getManager()
        {
            return transport.getManager();
        }
        public abstract OctetsStream marshal(OctetsStream os);
        public abstract OctetsStream unmarshal(OctetsStream os);
        public abstract int getType();
        public void send(Transport transport)
        {
            try
            {
                Octets data = new OctetsStream().marshal(this);
                transport.check(getType(), data.size());
                transport.sendTypedData(getType(), data);
            }
            catch (Exception e)
            {
                throw new CodecException(e);
            }
        }
    }
    public class State
    {
        private sealed class Stub
        {
            internal int size { get; set; }
            internal Type type { get; set; }
            public Stub(Type _type, int _size)
            {
                size = _size;
                type = _type;
            }
            public bool check(int _size)
            {
                return size > 0 ? _size <= size : true;
            }
            public Protocol newInstance()
            {
                return (Protocol)Activator.CreateInstance(type);
            }
        }
        private readonly IDictionary<int, Stub> stubs = new Dictionary<int, Stub>();
        public void register(int type, int size, Type classtype)
        {
            stubs.Add(type, new Stub(classtype, size));
        }
        public void addStub(Type classtype, int type, int maxsize)
        {
            stubs.Add(type, new Stub(classtype, maxsize));
        }
        public void merge(State s)
        {
            foreach (var i in s.stubs)
            {
                stubs.Remove(i.Key);
                stubs.Add(i.Key, i.Value);
            }
        }
        public IDictionary<int, int> getSizePolicy()
        {
            IDictionary<int, int> map = new Dictionary<int, int>();
            foreach (var i in stubs)
                map.Add(i.Key, i.Value.size);
            return map;
        }
        public bool check(int type, int size)
        {
            Stub stub;
            return stubs.TryGetValue(type, out stub) ? stub.check(size) : false;
        }
        internal ICollection<Protocol> decode(OctetsStream os, Transport transport)
        {
            ICollection<Protocol> protocols = new List<Protocol>();
            Manager manager = transport.getManager();
            while (os.remain() > 0)
            {
                os.begin();
                try
                {
                    int type = os.unmarshal_int();
                    int size = os.unmarshal_size();
                    Stub stub;
                    if (stubs.TryGetValue(type, out stub))
                    {
                        stub.check(size);
                        if (size > os.remain())
                        {
                            os.rollback();
                            break; // not enough
                        }
                        int startpos = os.position();
                        Protocol p = stub.newInstance();
                        try { p.unmarshal(os); }
                        catch (MarshalException e)
                        {
                            throw new CodecException("State.decode (" + type + ", " + size + ")", e);
                        }
                        p.setTransport(transport);
                        protocols.Add(p);
                        if ((os.position() - startpos) != size)
                            throw new CodecException("State.decode(" + type + ", " + size + ")=" + (os.position() - startpos));
                    }
                    else
                    {
                        throw new CodecException("unknown protocol (" + type + ", " + size + ")");
                    }
                    os.commit();
                }
                catch (MarshalException)
                {
                    os.rollback();
                    break;
                }
            }
            return protocols;
        }
    }
    public interface Manager : Closeable
    {
        void close(Transport transport);
        Listener getListener();
        Manager getWrapperManager();
    }
    public interface ClientManager : Manager
    {
        Transport getTransport();
    }
    public abstract class AbstractManager : Manager
    {
        public abstract ManagerConfig getConfig();
        internal abstract void removeProtocolTransport(StateTransportImpl transport);
        internal abstract void addProtocolTransport(StateTransportImpl transport);
        public abstract void close();
        public abstract void close(Transport transport);
        public abstract Listener getListener();
        internal Manager getOutmostWrapperManager()
        {
            Manager manager = this;
            while (manager.getWrapperManager() != null)
                manager = manager.getWrapperManager();
            return manager;
        }
        public abstract Manager getWrapperManager();
    }
    public interface SupportDispatch
    {
        void dispatch(Action runnable);
    }
    internal class StateTransportImpl : StateTransport
    {
        private readonly object locker = new object();
        internal readonly ManagerConfig config;
        private volatile Alarm alarm;
        private readonly AbstractManager manager;
        private volatile State state;
        private volatile NetworkStream ns;
        private volatile IPEndPoint local;
        private volatile IPEndPoint peer;
        private readonly Action cleanup;
        private volatile object sessionobj;
        private volatile Exception closeReason;
        private volatile Codec input = NullCodec.getInstance();
        private volatile Codec output = NullCodec.getInstance();
        private int underlyingclosed = 0;
        private volatile byte[] inputbuffer = new byte[131072];
        public StateTransportImpl(AbstractManager manager, Action cleanup)
        {
            this.manager = manager;
            this.config = manager.getConfig();
            this.state = config.getDefaultState();
            this.cleanup = cleanup;
        }
        public void setupAlarm(Alarm alarm) { this.alarm = alarm; }
        public void startup(NetworkStream ns, IPEndPoint local, IPEndPoint peer)
        {
            resetAlarm(0);
            this.ns = ns;
            this.local = local;
            this.peer = peer;
            ManagerConfig config = manager.getConfig();
            setInputSecurityCodec(config.getInputSecurityBytes(), config.isInputCompress());
            setOutputSecurityCodec(config.getOutputSecurityBytes(), config.isOutputCompress());
            manager.addProtocolTransport(this);
        }
        public Manager getManager()
        {
            return manager.getOutmostWrapperManager();
        }
        public void check(int type, int size)
        {
            if (!state.check(type, size))
                throw new Exception("checkSize type=" + type + " size=" + size);
        }
        internal void ready()
        {
            try
            {
                ns.BeginRead(inputbuffer, 0, inputbuffer.Length, ar =>
                {
                    try
                    {
                        int size = ns.EndRead(ar);
                        if (Trace.isDebugEnabled())
                            Trace.debug(manager + " " + this + " recvData size = " + size);
                        if (size > 0)
                        {
                            try
                            {
                                input.update(inputbuffer, 0, size);
                                input.flush();
                                ready();
                            }
                            catch (Exception e)
                            {
                                if (Trace.isErrorEnabled())
                                    Trace.error(manager + " " + this + " process Exception", e);
                                close();
                            }
                        }
                        else
                            close(new Exception("the channel has reached end-of-stream"));
                    }
                    catch (Exception e) { close(e); }
                }, null);
            }
            catch (Exception e) { close(e); }
        }
        public void shutdown(Exception closeReason)
        {
            lock (locker)
            {
                this.closeReason = closeReason;
            }
            if (this.local != null)
                manager.removeProtocolTransport(this);
            else
                ((ClientManagerImpl)manager).connectAbort(this);
        }
        public void setOutputSecurityCodec(byte[] key, bool compress)
        {
            if (Trace.isInfoEnabled())
                Trace.info(manager + " " + this + " setOutputSecurityCodec key = " + (key == null ? "" : Helper.toHexString(key)) + " compress = " + compress);
            Codec codec = new BufferedSink(new NetTaskCodecSink(this));
            if (null != key)
                codec = new Encrypt(codec, key);
            if (compress)
                codec = new RFC2118Encode(codec);
            output.Dispose();
            output = codec;
        }
        public void setInputSecurityCodec(byte[] key, bool compress)
        {
            if (Trace.isInfoEnabled())
                Trace.info(manager + " " + this + " setInputSecurityCodec key = " + (key == null ? "" : Helper.toHexString(key)) + " compress = " + compress);
            Codec codec = new CodecSink(this);
            if (compress || null != key)
                codec = new BufferedSink(codec);
            if (compress)
                codec = new RFC2118Decode(codec);
            if (null != key)
                codec = new Decrypt(codec, key);
            input.Dispose();
            input = codec;
        }
        private void _close(Exception closeReason)
        {
            if (Interlocked.Exchange(ref underlyingclosed, 1) == 1)
                return;
            alarm.Dispose();
            if (ns != null)
                ns.Close();
            cleanup();
            shutdown(closeReason);
            input.Dispose();
            output.Dispose();
        }
        internal void close(Exception closeReason) { _close(closeReason); }
        internal void close() { close(new Exception("channel closed manually")); }
        public void sendTypedData(int type, Octets data) { sendData(new OctetsStream().marshal(type).marshal(data)); }
        private void sendData(Octets data)
        {
            if (Trace.isDebugEnabled())
                Trace.debug(manager + " " + this + " sendData size = " + data.size());
            lock (locker)
            {
                output.update(data.array(), 0, data.size());
                output.flush();
            }
        }
        private class NetTaskCodecSink : Codec
        {
            private readonly StateTransportImpl t;
            private readonly bool check;
            private readonly int cfgsize;
            private int sendsize;
            public NetTaskCodecSink(StateTransportImpl t)
            {
                this.t = t;
                if (this.check = t.config.isCheckOutputBuffer())
                    this.cfgsize = t.config.getOutputBufferSize();
            }
            public void update(byte c)
            {
                byte[] onebyte = new byte[1];
                onebyte[0] = c;
                update(onebyte, 0, 1);
            }
            public void update(byte[] data, int off, int len)
            {
                if (check && Interlocked.Add(ref sendsize, len) > cfgsize)
                {
                    if (Trace.isWarnEnabled())
                        Trace.warn(t.getManager() + " " + t + " send buffer is full! sendbuffersize " + sendsize + " " + cfgsize);
                    t.close();
                }
                else
                {
                    try
                    {
                        byte[] tmp = new byte[len];
                        Buffer.BlockCopy(data, off, tmp, 0, len);
                        t.ns.BeginWrite(tmp, 0, len, ar =>
                        {
                            if (check)
                                Interlocked.Add(ref sendsize, -len);
                            try { t.ns.EndWrite(ar); }
                            catch (Exception e) { t.close(e); }
                        }, null);
                    }
                    catch (Exception e) { t.close(e); }
                }
            }
            public void flush() { }
            public void Dispose() { }
        }
        private class CodecSink : Codec
        {
            private readonly OctetsStream os = new OctetsStream(8192);
            private readonly StateTransportImpl t;
            public CodecSink(StateTransportImpl t)
            {
                this.t = t;
            }
            public void update(byte c)
            {
                os.push_byte(c);
                dispatch(t.state.decode(os, t));
            }
            public void update(byte[] data, int off, int len)
            {
                os.insert(os.size(), data, off, len);
                dispatch(t.state.decode(os, t));
            }
            public void flush()
            {
                if (os.remain() > 0)
                    dispatch(t.state.decode(os, t));
            }
            public void Dispose() { }
            private void dispatch(ICollection<Protocol> protocols)
            {
                foreach (Protocol protocol in protocols)
                    protocol.dispatch();
            }
        }
        public void setState(State state)
        {
            this.state = state;
        }
        public void resetAlarm(long milliseconds)
        {
            alarm.reset(milliseconds);
        }
        public IPEndPoint getPeerAddress()
        {
            return peer;
        }
        public IPEndPoint getLocalAddress()
        {
            return local;
        }
        public object getSessionObject()
        {
            return sessionobj;
        }
        public void setSessionObject(object obj)
        {
            sessionobj = obj;
        }
        public Exception getCloseReason()
        {
            return closeReason;
        }
    }
    internal class ClientManagerImpl : AbstractManager, ClientManager, SupportDispatch
    {
        private enum State { INIT, CONNECTING, EXCHANGE, CLOSE };
        private readonly ClientManagerConfig config;
        private readonly ClientListener listener;
        private readonly Manager wrapper;
        private readonly Dispatcher dispatcher;
        private volatile StateTransportImpl transport = null;
        private const int SHRINKTIME_MIN = 1;
        private const int SHRINKTIME_MAX = 60 * 3;
        private int shrinktime = SHRINKTIME_MIN;
        private readonly object locker = new object();
        private bool autoReconnect;
        private State state;
        private Timer future;
        public override ManagerConfig getConfig() { return config; }
        public override Listener getListener() { return listener; }
        public Transport getTransport() { return transport; }
        private void doConnect()
        {
            StateTransportImpl transport = null;
            try
            {
                TcpClient c = new TcpClient();
                c.ReceiveBufferSize = config.getInputBufferSize();
                c.SendBufferSize = config.getOutputBufferSize();
                transport = new StateTransportImpl(this, () => c.Close());
                transport.setupAlarm(new Alarm(() => transport.close(new Exception("connect timeout"))));
                transport.resetAlarm(config.getConnectTimeout());
                lock (locker)
                {
                    c.BeginConnect(config.getPeerAddress().Address, config.getPeerAddress().Port, ar =>
                    {
                        try
                        {
                            c.EndConnect(ar);
                            transport.startup(c.GetStream(), (IPEndPoint)c.Client.LocalEndPoint, (IPEndPoint)c.Client.RemoteEndPoint);
                        }
                        catch (Exception e)
                        {
                            if (Trace.isErrorEnabled())
                                Trace.error("ClientManagerImpl.doConnect", e);
                            transport.close(e);
                        }
                    }, null);
                    state = State.CONNECTING;
                }
            }
            catch (Exception t)
            {
                if (Trace.isErrorEnabled())
                    Trace.error(this + " doConnect", t);
                _close();
            }
        }
        public ClientManagerImpl(ClientManagerConfig config, ClientListener listener, Manager wrapper)
        {
            this.config = config;
            this.listener = listener;
            this.wrapper = wrapper;
            this.dispatcher = config.getDispatcher();
            Exception e = dispatcher.run(() => this.listener.onManagerInitialized(this, config));
            if (e != null)
                throw e;
            this.autoReconnect = config.isAutoReconnect();
            state = State.INIT;
            doConnect();
        }
        public virtual void dispatch(Action r)
        {
            dispatcher.execute(r);
        }
        private bool scheduleReconnect()
        {
            if (!autoReconnect)
                return false;
            state = State.INIT;
            future = new Timer(_ =>
            {
                lock (locker)
                {
                    future.Dispose();
                    future = null;
                    if (state == State.INIT)
                        doConnect();
                }
            });
            future.Change(shrinktime, Timeout.Infinite);
            shrinktime *= 2;
            if (shrinktime > SHRINKTIME_MAX)
                shrinktime = SHRINKTIME_MAX;
            return true;
        }
        internal void connectAbort(StateTransport transport)
        {
            lock (locker)
            {
                if (state != State.CONNECTING)
                    return;
                Exception t = dispatcher.run(() => listener.onAbort(transport));
                if (t == null)
                {
                    if (!scheduleReconnect())
                        _close();
                }
                else
                {
                    if (Trace.isErrorEnabled())
                        Trace.error(this + " connectAbort", t);
                    _close();
                }
            }
        }
        internal override void addProtocolTransport(StateTransportImpl transport)
        {
            lock (locker)
            {
                if (state != State.CONNECTING)
                {
                    transport.close();
                    return;
                }
                Exception t = dispatcher.run(() => listener.onTransportAdded(transport));
                if (t == null)
                {
                    this.transport = transport;
                    transport.ready();
                    state = State.EXCHANGE;
                    shrinktime = SHRINKTIME_MIN;
                }
                else
                {
                    if (Trace.isErrorEnabled())
                        Trace.error(this + " addProtocolTransport = " + transport, t);
                    transport.close();
                    _close();
                }
            }
        }
        internal override void removeProtocolTransport(StateTransportImpl transport)
        {
            lock (locker)
            {
                if (state != State.EXCHANGE)
                    return;
                if (this.transport != null)
                {
                    transport.close();
                    this.transport = null;
                }
                Exception t = dispatcher.run(() => listener.onTransportRemoved(transport));
                if (t == null)
                {
                    if (!scheduleReconnect())
                        _close();
                }
                else
                {
                    if (Trace.isErrorEnabled())
                        Trace.error(this + " removeProtocolTransport = " + transport, t);
                    _close();
                }
                Monitor.Pulse(locker);
            }
        }
        private void _close()
        {
            state = State.CLOSE;
            Engine.remove(this);
        }
        public override void close()
        {
            if (Engine.remove(this))
                return;
            lock (locker)
            {
                autoReconnect = false;
                switch (state)
                {
                    case State.EXCHANGE:
                        StateTransportImpl _transport = transport;
                        transport = null;
                        _transport.close();
                        while (state != State.CLOSE)
                            Monitor.Wait(locker);
                        break;
                    case State.CONNECTING:
                        Exception e = dispatcher.run(() => listener.onAbort(null));
                        if (e != null && Trace.isErrorEnabled())
                            Trace.error(this + " connectAbort", e);
                        break;
                    case State.INIT:
                        if (future != null)
                            future.Dispose();
                        break;
                }
                dispatcher.await();
                dispatcher.run(() => listener.onManagerUninitialized(this));
            }
        }
        public override void close(Transport transport)
        {
            if (this.transport == transport)
                close();
        }
        public override Manager getWrapperManager()
        {
            return wrapper;
        }
    }
    public static class Engine
    {
        private static HashExecutor applicationExecutor;
        private static HashExecutor protocolExecutor;
        private static Dispatcher engineExecutor = new Dispatcher(new Executor(r => ThreadPool.QueueUserWorkItem(_ => r())));
        private readonly static Dictionary<Closeable, bool> closeables = new Dictionary<Closeable, bool>();
        private static bool closed = true;
        public static void open(int netProcessors, int protocolSchedulers, int applicationExecutors)
        {
            lock (closeables)
            {
                if (!closed)
                    throw new Exception("engine is not closed!");
                applicationExecutor = new HashExecutor(applicationExecutors);
                protocolExecutor = new HashExecutor(protocolSchedulers);
                closed = false;
            }
        }
        public static void close()
        {
            lock (closeables)
            {
                if (closed)
                    throw new Exception("engine is not running!");
                List<Closeable> all = new List<Closeable>();
                foreach (Closeable c in closeables.Keys)
                    if (c is Manager)
                    {
                        if (((Manager)c).getWrapperManager() == null)
                            all.Add(c);
                    }
                    else
                        all.Add(c);
                foreach (Closeable c in all)
                    c.close();
                while (closeables.Count != 0)
                    Monitor.Wait(closeables);
            }
            engineExecutor.await();
            lock (closeables) { closed = true; }
        }
        public static void add(Closeable c)
        {
            lock (closeables)
            {
                closeables.Add(c, true);
            }
        }
        public static Manager add(ClientManagerConfig config, ClientListener listener, Manager wrapper)
        {
            lock (closeables)
            {
                if (closed)
                    throw new Exception("engine is not running!");
                Manager manager = new ClientManagerImpl(config, listener, wrapper);
                closeables.Add(manager, true);
                if (wrapper != null && !closeables.ContainsKey(wrapper))
                    closeables.Add(wrapper, true);
                return manager;
            }
        }
        public static bool remove(Closeable c)
        {
            lock (closeables)
            {
                bool b;
                if (!closeables.TryGetValue(c, out b))
                    return true;
                if (!b)
                {
                    closeables.Remove(c);
                    Monitor.PulseAll(closeables);
                    return false;
                }
                engineExecutor.execute(() =>
                {
                    lock (closeables)
                    {
                        bool _b;
                        if (!closeables.TryGetValue(c, out _b) || !_b)
                            return;
                        closeables.Remove(c);
                        closeables.Add(c, false);
                    }
                    c.close();
                });
                while (closeables.ContainsKey(c))
                    Monitor.Wait(closeables);
                return true;
            }
        }
        public static bool contains(Manager manager)
        {
            lock (closeables)
            {
                return closeables.ContainsKey(manager);
            }
        }
        public static HashExecutor getProtocolScheduler()
        {
            return protocolExecutor;
        }
        public static HashExecutor getProtocolExecutor()
        {
            return protocolExecutor;
        }
        public static HashExecutor getApplicationExecutor()
        {
            return applicationExecutor;
        }
    }
}