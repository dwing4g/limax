using System;
using System.Collections;
using System.Collections.Generic;
using System.Collections.Concurrent;
using System.Globalization;
using System.IO;
using System.Net;
using System.Numerics;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using limax.codec;

namespace limax.util
{
    #region Utils
    static public class Utils
    {
        public static bool equals<T>(T a, T b)
        {
            if (object.ReferenceEquals(a, b))
                return true;
            return a.Equals(b);
        }
        public static bool equals_Collection<T>(ICollection<T> a, ICollection<T> b)
        {
            if (object.ReferenceEquals(a, b))
                return true;
            if (a.Count != b.Count)
                return false;
            using (var eb = b.GetEnumerator())
            {
                foreach (var e in a)
                {
                    eb.MoveNext();
                    if (!equals(e, eb.Current))
                        return false;
                }
            }
            return true;
        }
        public static bool equals<T>(LinkedList<T> a, LinkedList<T> b)
        {
            return equals_Collection(a, b);
        }
        public static bool equals<T>(List<T> a, List<T> b)
        {
            return equals_Collection(a, b);
        }
        public static bool equals<T>(HashSet<T> a, HashSet<T> b)
        {
            if (object.ReferenceEquals(a, b))
                return true;
            return a.SetEquals(b);
        }
        public static bool equals<TKey, TValue>(Dictionary<TKey, TValue> a, Dictionary<TKey, TValue> b)
        {
            return equals_map(a, b);
        }
        public static bool equals<TKey, TValue>(SortedDictionary<TKey, TValue> a, SortedDictionary<TKey, TValue> b)
        {
            return equals_map(a, b);
        }
        public static bool equals_map<TKey, TValue>(IDictionary<TKey, TValue> a, IDictionary<TKey, TValue> b)
        {
            if (object.ReferenceEquals(a, b))
                return true;
            if (a.Count != b.Count)
                return false;
            foreach (var v in a)
            {
                TValue vb;
                if (!b.TryGetValue(v.Key, out vb))
                    return false;
                if (!equals(v.Value, vb))
                    return false;
            }
            return true;
        }
        public static int hashCode_Collection<T>(ICollection<T> c)
        {
            int hashCode = 1;
            foreach (var e in c)
                hashCode = 31 * hashCode + (e == null ? 0 : e.GetHashCode());
            return hashCode;
        }
        public static int hashCode<T>(List<T> c)
        {
            return hashCode_Collection(c);
        }
        public static int hashCode<T>(LinkedList<T> c)
        {
            return hashCode_Collection(c);
        }
        public static int hashCode<T>(HashSet<T> c)
        {
            return hashCode_Collection(c);
        }
        public static int hashCode_Dictionary<TKey, TValue>(IDictionary<TKey, TValue> c)
        {
            int hashCode = 1;
            foreach (var e in c)
            {
                var k = e.Key;
                var v = e.Value;
                hashCode = 31 * hashCode + (k == null ? 0 : k.GetHashCode());
                hashCode = 31 * hashCode + (v == null ? 0 : v.GetHashCode());
            }
            return hashCode;
        }
        public static int hashCode<TKey, TValue>(IDictionary<TKey, TValue> c)
        {
            return hashCode_Dictionary(c);
        }
        public static int hashCode<TKey, TValue>(SortedDictionary<TKey, TValue> c)
        {
            return hashCode_Dictionary(c);
        }
        private static readonly object actions_mutex = new object();
        private static volatile List<Action> actions_queue = new List<Action>();
        public static void runOnUiThread(Action a)
        {
            var x = actions_queue;
            lock (actions_mutex)
            {
                actions_queue.Add(a);
            }
        }
        public static void uiThreadSchedule()
        {
            List<Action> actions;
            lock (actions_mutex)
            {
                actions = actions_queue;
                actions_queue = new List<Action>();
            }
            actions.ForEach(_ => _());
        }
    }
    #endregion
    #region BitSet tools
    public struct BitSet
    {
        private readonly BitArray bs;
        public BitSet(int length)
        {
            bs = new BitArray(length);
        }
        public void clear(int index)
        {
            bs.Set(index, false);
        }
        public void set(int index)
        {
            bs.Set(index, true);
        }
        public bool get(int index)
        {
            return bs.Get(index);
        }
        internal bool isEmpty()
        {
            foreach (bool b in bs)
                if (b)
                    return false;
            return true;
        }
    }
    public struct MapBitSet<K>
    {
        private readonly int length;
        private readonly IDictionary<K, BitSet> map;
        public MapBitSet(int length)
        {
            this.length = length;
            this.map = new Dictionary<K, BitSet>();
        }
        public void clear(K key, int index)
        {
            BitSet bs;
            if (map.TryGetValue(key, out bs))
            {
                bs.clear(index);
                if (bs.isEmpty())
                    map.Remove(key);
            }
        }
        public void set(K key, int index)
        {
            BitSet bs;
            if (!map.TryGetValue(key, out bs))
                map.Add(key, bs = new BitSet(length));
            bs.set(index);
        }
        public bool get(K key, int index)
        {
            BitSet bs;
            return map.TryGetValue(key, out bs) ? bs.get(index) : false;
        }
        public void remove(K key)
        {
            map.Remove(key);
        }
    }
    #endregion
    #region Thread tools
    public delegate void Executor(Action a);
    public interface Closeable
    {
        void close();
    }
    public sealed class Dispatcher
    {
        private int running;
        private readonly Executor executor;
        public Dispatcher(Executor executor)
        {
            this.executor = executor;
        }
        public void execute(Action r)
        {
            Interlocked.Increment(ref running);
            executor(() =>
            {
                try
                {
                    r();
                }
                finally
                {
                    if (Interlocked.Decrement(ref running) == 0)
                        lock (this)
                        {
                            Monitor.PulseAll(this);
                        }
                }
            });
        }
        public void await()
        {
            lock (this)
            {
                while (Interlocked.CompareExchange(ref running, 0, 0) != 0)
                    Monitor.Wait(this);
            }
        }
        private class WrappedAction
        {
            private readonly Action r;
            internal Exception exception = null;
            internal Boolean done = false;
            internal WrappedAction(Action r) { this.r = r; }
            internal void run()
            {
                lock (this)
                {
                    try { r(); }
                    catch (Exception e) { exception = e; }
                    done = true;
                    Monitor.Pulse(this);
                }
            }
        }
        public Exception run(Action r)
        {
            WrappedAction wrapper = new WrappedAction(r);
            lock (wrapper)
            {
                executor(() => wrapper.run());
                while (!wrapper.done)
                    Monitor.Wait(wrapper);
            }
            return wrapper.exception;
        }
    }
    public sealed class SingleThreadExecutor
    {
        private readonly Thread thread;
        private readonly int tid;
        private readonly BlockingCollection<Action> q = new BlockingCollection<Action>();
        private volatile bool terminated = false;
        public SingleThreadExecutor(string name)
        {
            thread = new Thread(() =>
            {
                for (Action r; true;  )
                {
                    try { r = q.Take(); }
                    catch (ThreadInterruptedException) { break; }
                    try { r(); }
                    catch (Exception) { }
                }
            });
            tid = thread.ManagedThreadId;
            thread.Name = name;
            thread.Start();
        }
        public void execute(Action r)
        {
            if (r == null)
                throw new Exception("Action r == null");
            if (terminated)
                throw new Exception("SingleThreadExecutor <" + thread.Name + "> terminated");
            q.Add(r);
        }
        public void wait(Action r)
        {
            if (tid == Thread.CurrentThread.ManagedThreadId)
            {
                r();
                return;
            }
            object mutex = new object();
            Exception ex = null;
            lock (mutex)
            {
                execute(() =>
                {
                    lock (mutex)
                    {
                        try { r(); }
                        catch (Exception e) { ex = e; }
                        finally { Monitor.Pulse(mutex); }
                    }
                });
                Monitor.Wait(mutex);
            }
            if (ex != null)
                throw ex;
        }
        public void shutdown()
        {
            terminated = true;
            thread.Interrupt();
            thread.Join();
            q.Dispose();
        }
    }
    public sealed class HashExecutor
    {
        private readonly Executor[] pool;
        public HashExecutor(int concurrencyLevel)
        {
            int capacity = 1;
            while (capacity < concurrencyLevel)
                capacity <<= 1;
            this.pool = new Executor[capacity];
            for (int i = 0; i < capacity; i++)
            {
                SerialExecutor executor = new SerialExecutor();
                this.pool[i] = new Executor(_ => executor.execute(_));
            }
        }
        public void execute(Action r)
        {
            ThreadPool.QueueUserWorkItem(_ => r());
        }
        private static int hash(int _h)
        {
            uint h = (uint)_h;
            h ^= (h >> 20) ^ (h >> 12);
            return (int)(h ^ (h >> 7) ^ (h >> 4));
        }
        public Executor getExecutor(object key)
        {
            return pool[hash(key == null ? 0 : key.GetHashCode()) & (pool.Length - 1)];
        }
        public void execute(object key, Action r)
        {
            getExecutor(key)(r);
        }
        private class SerialExecutor
        {
            private readonly Queue<WaitCallback> tasks = new Queue<WaitCallback>();
            private WaitCallback active;
            public void execute(Action r)
            {
                lock (this)
                {
                    tasks.Enqueue(_ =>
                    {
                        try { r(); }
                        finally { scheduleNext(); }
                    });
                    if (active == null)
                        scheduleNext();
                }
            }
            private void scheduleNext()
            {
                lock (this)
                {
                    if (tasks.Count > 0)
                        ThreadPool.QueueUserWorkItem(active = tasks.Dequeue());
                    else
                        active = null;
                }
            }
        }
    }
    public sealed class Alarm : IDisposable
    {
        private readonly Timer timer;
        public Alarm(Action r) { timer = new Timer(_ => r()); }
        public void reset(long milliseconds) { timer.Change(milliseconds > 0 ? milliseconds : Timeout.Infinite, Timeout.Infinite); }
        public void Dispose() { timer.Dispose(); }
    }
    #endregion
    #region Resource
    public sealed class Resource
    {
        private readonly Resource parent;
        private readonly ISet<Resource> children = new HashSet<Resource>();
        private Action cleanup;
        private Resource()
        {
            this.parent = null;
            this.cleanup = () => { };
        }
        private Resource(Resource parent, Action cleanup)
        {
            this.parent = parent;
            this.cleanup = cleanup;
            lock (parent)
            {
                if (parent.cleanup == null)
                    throw new InvalidOperationException();
                parent.children.Add(this);
            }
        }
        private void _close()
        {
            lock (this)
            {
                if (cleanup == null)
                    return;
                foreach (var c in children)
                    c._close();
                children.Clear();
                cleanup();
                cleanup = null;
            }
        }
        public void close()
        {
            if (parent != null)
                lock (parent)
                {
                    if (parent.children.Remove(this))
                        _close();
                }
            else
                _close();
        }
        public static Resource createRoot() { return new Resource(); }
        public static Resource create(Resource parent, Action cleanup)
        {
            if (parent == null || cleanup == null)
                throw new ArgumentNullException();
            return new Resource(parent, cleanup);
        }
    }
    #endregion
    #region Helper
    public static class Helper
    {
        private static readonly char[] table36 = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z' };
        public static string toString36(int n)
        {
            string s;
            string r = "";
            if (n < 0)
            {
                n = -n;
                s = "-";
            }
            else
            {
                s = "";
            }
            while (n > 35)
            {
                r = table36[n % 36] + r;
                n /= 36;
            }
            return s + table36[n] + r;
        }

        private static readonly string HEX_CHARS = "0123456789abcdef";
        public static string toHexString(byte[] b)
        {
            return toHexString(b, 0, b.Length);
        }
        public static string toHexString(byte[] b, int off, int len)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = off; i < (off + len); i++)
            {
                sb.Append(HEX_CHARS[(b[i] >> 4) & 0x0F]);
                sb.Append(HEX_CHARS[b[i] & 0x0F]);
            }
            return sb.ToString();
        }
        public static string toFileNameString(string s)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0, l = s.Length; i < l; i++)
            {
                char c = s[i];
                if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
                    sb.Append(c);
                else if (((c >> 8) & 0xff) != 0)
                {
                    sb.Append('_');
                    sb.Append(HEX_CHARS[c >> 12 & 0xf]);
                    sb.Append(HEX_CHARS[c >> 8 & 0xf]);
                    sb.Append(HEX_CHARS[c >> 4 & 0xf]);
                    sb.Append(HEX_CHARS[c & 0xf]);
                }
                else
                {
                    sb.Append('-');
                    sb.Append(HEX_CHARS[c >> 4 & 0xf]);
                    sb.Append(HEX_CHARS[c & 0xf]);
                }
            }
            return sb.ToString();
        }
        public static string fromFileNameString(string s)
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0, l = s.Length; i < l; )
            {
                char c = s[i++];
                if (c == '_')
                {
                    c = (char)Int32.Parse(s.Substring(i, 4), System.Globalization.NumberStyles.HexNumber);
                    i += 4;
                }
                else if (c == '-')
                {
                    c = (char)Int32.Parse(s.Substring(i, 2), System.Globalization.NumberStyles.HexNumber);
                    i += 2;
                }
                sb.Append(c);
            }
            return sb.ToString();
        }
        private static readonly BigInteger dh_g = new BigInteger(2);
        private static readonly BigInteger[] dh_group = new BigInteger[] {
			BigInteger.Zero,
			BigInteger.Parse(
					"0FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A63A3620FFFFFFFFFFFFFFFF",
					NumberStyles.AllowHexSpecifier),// dh_group1, rfc2049 768
			BigInteger.Parse(
					"0FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381FFFFFFFFFFFFFFFF",
					NumberStyles.AllowHexSpecifier),// dh_group2, rfc2049 1024
			BigInteger.Zero,
			BigInteger.Zero,
			BigInteger.Parse(
					"0FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF",
					NumberStyles.AllowHexSpecifier),// dh_group5 rfc3526 1536
			BigInteger.Zero,
			BigInteger.Zero,
			BigInteger.Zero,
			BigInteger.Zero,
			BigInteger.Zero,
			BigInteger.Zero,
			BigInteger.Zero,
			BigInteger.Zero,
			BigInteger.Parse(
					"0FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF",
					NumberStyles.AllowHexSpecifier),// dh_group14, rfc3526 2048
			BigInteger.Parse(
					"0FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200CBBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF",
					NumberStyles.AllowHexSpecifier),// dh_group15,rfc3526 3072
			BigInteger.Parse(
					"0FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200CBBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A92108011A723C12A787E6D788719A10BDBA5B2699C327186AF4E23C1A946834B6150BDA2583E9CA2AD44CE8DBBBC2DB04DE8EF92E8EFC141FBECAA6287C59474E6BC05D99B2964FA090C3A2233BA186515BE7ED1F612970CEE2D7AFB81BDD762170481CD0069127D5B05AA993B4EA988D8FDDC186FFB7DC90A6C08F4DF435C934063199FFFFFFFFFFFFFFFF",
					NumberStyles.AllowHexSpecifier),// dh_group16,rfc3526 4096
			BigInteger.Parse(
					"0FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200CBBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A92108011A723C12A787E6D788719A10BDBA5B2699C327186AF4E23C1A946834B6150BDA2583E9CA2AD44CE8DBBBC2DB04DE8EF92E8EFC141FBECAA6287C59474E6BC05D99B2964FA090C3A2233BA186515BE7ED1F612970CEE2D7AFB81BDD762170481CD0069127D5B05AA993B4EA988D8FDDC186FFB7DC90A6C08F4DF435C93402849236C3FAB4D27C7026C1D4DCB2602646DEC9751E763DBA37BDF8FF9406AD9E530EE5DB382F413001AEB06A53ED9027D831179727B0865A8918DA3EDBEBCF9B14ED44CE6CBACED4BB1BDB7F1447E6CC254B332051512BD7AF426FB8F401378CD2BF5983CA01C64B92ECF032EA15D1721D03F482D7CE6E74FEF6D55E702F46980C82B5A84031900B1C9E59E7C97FBEC7E8F323A97A7E36CC88BE0F1D45B7FF585AC54BD407B22B4154AACC8F6D7EBF48E1D814CC5ED20F8037E0A79715EEF29BE32806A1D58BB7C5DA76F550AA3D8A1FBFF0EB19CCB1A313D55CDA56C9EC2EF29632387FE8D76E3C0468043E8F663F4860EE12BF2D5B0B7474D6E694F91E6DCC4024FFFFFFFFFFFFFFFF",
					NumberStyles.AllowHexSpecifier),// dh_group17,rfc3526 6144
			BigInteger.Parse(
					"0FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA051015728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200CBBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A92108011A723C12A787E6D788719A10BDBA5B2699C327186AF4E23C1A946834B6150BDA2583E9CA2AD44CE8DBBBC2DB04DE8EF92E8EFC141FBECAA6287C59474E6BC05D99B2964FA090C3A2233BA186515BE7ED1F612970CEE2D7AFB81BDD762170481CD0069127D5B05AA993B4EA988D8FDDC186FFB7DC90A6C08F4DF435C93402849236C3FAB4D27C7026C1D4DCB2602646DEC9751E763DBA37BDF8FF9406AD9E530EE5DB382F413001AEB06A53ED9027D831179727B0865A8918DA3EDBEBCF9B14ED44CE6CBACED4BB1BDB7F1447E6CC254B332051512BD7AF426FB8F401378CD2BF5983CA01C64B92ECF032EA15D1721D03F482D7CE6E74FEF6D55E702F46980C82B5A84031900B1C9E59E7C97FBEC7E8F323A97A7E36CC88BE0F1D45B7FF585AC54BD407B22B4154AACC8F6D7EBF48E1D814CC5ED20F8037E0A79715EEF29BE32806A1D58BB7C5DA76F550AA3D8A1FBFF0EB19CCB1A313D55CDA56C9EC2EF29632387FE8D76E3C0468043E8F663F4860EE12BF2D5B0B7474D6E694F91E6DBE115974A3926F12FEE5E438777CB6A932DF8CD8BEC4D073B931BA3BC832B68D9DD300741FA7BF8AFC47ED2576F6936BA424663AAB639C5AE4F5683423B4742BF1C978238F16CBE39D652DE3FDB8BEFC848AD922222E04A4037C0713EB57A81A23F0C73473FC646CEA306B4BCBC8862F8385DDFA9D4B7FA2C087E879683303ED5BDD3A062B3CF5B3A278A66D2A13F83F44F82DDF310EE074AB6A364597E899A0255DC164F31CC50846851DF9AB48195DED7EA1B1D510BD7EE74D73FAF36BC31ECFA268359046F4EB879F924009438B481C6CD7889A002ED5EE382BC9190DA6FC026E479558E4475677E9AA9E3050E2765694DFC81F56E880B96E7160C980DD98EDD3DFFFFFFFFFFFFFFFFF",
					NumberStyles.AllowHexSpecifier) // dh_group18,rfc3526 8192
        };
        public static byte[] makeRandValues(int bytes)
        {
            byte[] v = new byte[bytes];
            using (RandomNumberGenerator random = RandomNumberGenerator.Create())
                random.GetNonZeroBytes(v);
            return v;
        }
        public static bool isDHGroupSupported(int group)
        {
            return group >= 0 && group < dh_group.Length && !dh_group[group].Equals(BigInteger.Zero);
        }
        public static BigInteger makeDHRandom()
        {
            byte[] r = makeRandValues(17);
            r[16] = 0;
            return new BigInteger(r);
        }
        public static BigInteger generateDHResponse(int group, BigInteger rand)
        {
            return BigInteger.ModPow(dh_g, rand, dh_group[group]);
        }
        public static BigInteger computeDHKey(int group, BigInteger response, BigInteger rand)
        {
            return BigInteger.ModPow(response, rand, dh_group[group]);
        }
        public static T[] copyOfRange<T>(T[] original, int from, int to)
        {
            T[] data = new T[to - from];
            Array.Copy(original, from, data, 0, to - from);
            return data;
        }
        public static T[] copyOf<T>(T[] original, int newLength)
        {
            return copyOfRange(original, 0, newLength);
        }
        private class EmptyCollection<E> : ICollection<E>
        {
            public int Count { get { return 0; } }
            public bool IsReadOnly { get { return true; } }
            public void Add(E item)
            {
                throw new Exception("unsupported operator!");
            }
            public void Clear()
            {
                throw new Exception("unsupported operator!");
            }
            public bool Contains(E item)
            {
                return false;
            }
            public void CopyTo(E[] array, int arrayIndex)
            {
            }
            public bool Remove(E item)
            {
                throw new Exception("unsupported operator!");
            }
            private sealed class EmptyEnumerator<T> : IEnumerator<T>
            {
                Object System.Collections.IEnumerator.Current { get { return null; } }
                T IEnumerator<T>.Current { get { return default(T); } }
                public void Dispose() { }
                public bool MoveNext() { return false; }
                public void Reset() { }
            }
            System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator()
            {
                return new EmptyEnumerator<E>();
            }
            IEnumerator<E> IEnumerable<E>.GetEnumerator()
            {
                return new EmptyEnumerator<E>();
            }
        }
        public static ICollection<E> emptyCollection<E>()
        {
            return new EmptyCollection<E>();
        }
        private sealed class EmptyDictionary<TKey, TValue> : EmptyCollection<KeyValuePair<TKey, TValue>>, IDictionary<TKey, TValue>
        {
            public ICollection<TKey> Keys
            {
                get
                {
                    return emptyCollection<TKey>();
                }
            }
            public ICollection<TValue> Values
            {
                get
                {
                    return emptyCollection<TValue>();
                }
            }
            public TValue this[TKey key]
            {
                get
                {
                    return default(TValue);
                }
                set
                {
                    throw new Exception("unsupported operator!");
                }
            }
            public void Add(TKey key, TValue value)
            {
                throw new Exception("unsupported operator!");
            }
            public bool ContainsKey(TKey key) { return false; }
            public bool Remove(TKey key) { return false; }
            public bool TryGetValue(TKey key, out TValue value)
            {
                value = default(TValue);
                return false;
            }
        }
        public static IDictionary<K, V> emptyDictionary<K, V>()
        {
            return new EmptyDictionary<K, V>();
        }
        private class ReadonlyCollection<E> : ICollection<E>
        {
            private readonly ICollection<E> collection;
            public ReadonlyCollection(ICollection<E> collection)
            {
                this.collection = collection;
            }
            public int Count { get { return collection.Count; } }
            public bool IsReadOnly { get { return true; } }
            public void Add(E item)
            {
                throw new Exception("unsupported operator!");
            }
            public void Clear()
            {
                throw new Exception("unsupported operator!");
            }
            public bool Contains(E item)
            {
                return collection.Contains(item);
            }
            public void CopyTo(E[] array, int arrayIndex)
            {
                collection.CopyTo(array, arrayIndex);
            }
            public bool Remove(E item)
            {
                throw new Exception("unsupported operator!");
            }
            private sealed class ReadonlyEnumerator<T> : IEnumerator<T>
            {
                private readonly IEnumerator<T> enumerator;
                public ReadonlyEnumerator(IEnumerator<T> enumerator)
                {
                    this.enumerator = enumerator;
                }
                Object System.Collections.IEnumerator.Current
                {
                    get
                    {
                        return enumerator.Current;
                    }
                }
                T IEnumerator<T>.Current
                {
                    get
                    {
                        return enumerator.Current;
                    }
                }
                public void Dispose()
                {
                    enumerator.Dispose();
                }
                public bool MoveNext()
                {
                    return enumerator.MoveNext();
                }
                public void Reset()
                {
                    enumerator.Reset();
                }
            }
            System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator()
            {
                return new ReadonlyEnumerator<E>(collection.GetEnumerator());
            }
            IEnumerator<E> IEnumerable<E>.GetEnumerator()
            {
                return new ReadonlyEnumerator<E>(collection.GetEnumerator());
            }
        }
        public static ICollection<E> readonlyCollection<E>(ICollection<E> c)
        {
            return new ReadonlyCollection<E>(c);
        }
    }
    #endregion
    #region Trace
    public static class Trace
    {
        public enum Level { Fatal, Error, Warn, Info, Debug };
        public delegate void Destination(string msg);
        private static Level level = Level.Warn;
        private static Destination printTo = _ => System.Diagnostics.Debug.Print(_);
        public static void open(Destination _printTo, Level _level)
        {
            printTo = _printTo;
            level = _level;
        }
        private static void output(string msg) { printTo(msg); }
        public static bool isDebugEnabled() { return level >= Level.Debug; }
        public static bool isInfoEnabled() { return level >= Level.Info; }
        public static bool isWarnEnabled() { return level >= Level.Warn; }
        public static bool isErrorEnabled() { return level >= Level.Error; }
        public static bool isFatalEnabled() { return level >= Level.Fatal; }
        public static void fatal(string msg)
        {
            if (isFatalEnabled())
                output(msg);
        }
        public static void fatal(string msg, Exception e)
        {
            if (isFatalEnabled())
                output(msg + "\r\n" + e.ToString());
        }
        public static void error(string msg)
        {
            if (isErrorEnabled())
                output(msg);
        }
        public static void error(string msg, Exception e)
        {
            if (isErrorEnabled())
                output(msg + "\r\n" + e.ToString());
        }
        public static void info(string msg)
        {
            if (isInfoEnabled())
                output(msg);
        }
        public static void info(string msg, Exception e)
        {
            if (isInfoEnabled())
                output(msg + "\r\n" + e.ToString());
        }
        public static void debug(string msg)
        {
            if (isDebugEnabled())
                output(msg);
        }
        public static void debug(string msg, Exception e)
        {
            if (isDebugEnabled())
                output(msg + "\r\n" + e.ToString());
        }
        public static void warn(string msg)
        {
            if (isWarnEnabled())
                output(msg);
        }
        public static void warn(string msg, Exception e)
        {
            if (isWarnEnabled())
                output(msg + "\r\n" + e.ToString());
        }
    }
    #endregion
    #region HttpClient
    public sealed class HttpClient
    {
        private sealed class HttpWebClient : WebClient
        {
            private readonly string etag;
            private HttpWebResponse response;
            internal HttpWebClient(string etag)
            {
                this.etag = etag;
            }
            protected override WebRequest GetWebRequest(Uri address)
            {
                HttpWebRequest request = base.GetWebRequest(address) as HttpWebRequest;
                if (etag != null)
                    request.Headers[HttpRequestHeader.IfNoneMatch] = etag;
                return request;
            }
            protected override WebResponse GetWebResponse(WebRequest request)
            {
                try
                {
                    response = (HttpWebResponse)base.GetWebResponse(request);
                }
                catch (WebException e)
                {
                    response = (HttpWebResponse)e.Response;
                }
                return response;
            }
            protected override WebResponse GetWebResponse(WebRequest request, IAsyncResult result)
            {
                try
                {
                    response = (HttpWebResponse)base.GetWebResponse(request, result);
                }
                catch (WebException e)
                {
                    response = (HttpWebResponse)e.Response;
                }
                return response;
            }
            public string getETag()
            {
                return response != null ? response.Headers[HttpResponseHeader.ETag] : null;
            }
            public HttpStatusCode getStatusCode()
            {
                return response != null ? response.StatusCode : HttpStatusCode.GatewayTimeout;
            }
            public string getContentType()
            {
                return response != null ? response.ContentType : "";
            }
        }
        private readonly static Regex pattern = new Regex(".*charset=(.*)", RegexOptions.IgnoreCase);
        private readonly Uri uri;
        private readonly long timeout;
        private readonly int maxsize;
        private readonly bool staleEnable;
        private readonly byte[] headBuffer = new byte[64];
        private readonly string path;
        private FileStream fs;
        private string etag;
        private Encoding charset;
        public HttpClient(String url, long timeout, int maxsize, string cacheDir, bool staleEnable)
        {
            this.uri = new Uri(url);
            this.timeout = timeout;
            this.maxsize = maxsize;
            this.staleEnable = staleEnable;
            string path = cacheDir == null ? null : Path.Combine(cacheDir, Helper.toFileNameString(uri.ToString()));
            if (path != null)
            {
                try { fs = File.Open(path, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None); }
                catch (Exception) { fs = null; }
                try
                {
                    fs.Read(headBuffer, 0, headBuffer.Length);
                    if (BitConverter.ToInt32(headBuffer, 0) != fs.Length)
                        throw new Exception();
                    string sign = Encoding.UTF8.GetString(headBuffer, 5, headBuffer[4]);
                    int pos = sign.IndexOf(' ');
                    charset = Encoding.GetEncoding(sign.Substring(0, pos));
                    etag = sign.Substring(pos + 1);
                }
                catch (Exception)
                {
                    try { fs.SetLength(0); }
                    catch (Exception)
                    {
                        fs.Close();
                        path = null;
                        fs = null;
                        etag = null;
                        charset = null;
                    }
                }
            }
            this.path = path;
        }
        public void transfer(CharSink sink)
        {
            try { _transfer(sink); }
            finally
            {
                if (fs != null)
                    fs.Close();
            }
        }
        private void _transfer(CharSink sink)
        {
            long starttime = DateTime.Now.Ticks;
            HttpWebClient conn = new HttpWebClient(etag);
            Stream istream = null;
            conn.OpenReadCompleted += (_, arg) =>
            {
                try { istream = arg.Result; }
                catch (Exception) { }
                lock (conn) { Monitor.Pulse(conn); }
            };
            lock (conn)
            {
                conn.OpenReadAsync(uri);
                if (!Monitor.Wait(conn, (int)timeout))
                    conn.CancelAsync();
            }
            HttpStatusCode status = conn.getStatusCode();
            if (status != HttpStatusCode.OK)
            {
                if (status != HttpStatusCode.NotModified && !staleEnable || fs == null || charset == null)
                    throw new WebException("HttpClient cache missing. responseCode = " + status);
                try
                {
                    sink.setCharset(charset);
                    new StreamSource(fs, sink).flush();
                }
                catch (Exception e) { throw new WebException("HttpClient transfer cache data.", e); }
                return;
            }
            Match matchers = pattern.Match(conn.getContentType());
            charset = Encoding.UTF8;
            if (matchers.Success)
                try { charset = Encoding.GetEncoding(matchers.Groups[1].Value); }
                catch (Exception) { }
            if (fs != null)
                try
                {
                    etag = conn.getETag();
                    Buffer.BlockCopy(BitConverter.GetBytes(0), 0, headBuffer, 0, 4);
                    byte[] sign = Encoding.UTF8.GetBytes(charset.WebName + " " + etag);
                    headBuffer[4] = (byte)sign.Length;
                    Buffer.BlockCopy(sign, 0, headBuffer, 5, sign.Length);
                    fs.Seek(0, SeekOrigin.Begin);
                    fs.Write(headBuffer, 0, 64);
                }
                catch (Exception)
                {
                    fs.Close();
                    File.Delete(path);
                    fs = null;
                }
            try
            {
                ExceptionJail ej = null;
                sink.setCharset(charset);
                Codec wc;
                if (fs == null)
                    wc = sink;
                else
                    wc = new CodecCollection(sink, new SinkStream(fs));
                new StreamSource(istream, new BoundCheck(maxsize, timeout - (DateTime.Now.Ticks - starttime) / TimeSpan.TicksPerMillisecond, wc)).flush();
                if (ej != null && ej.get() != null)
                    throw ej.get();
                if (fs != null)
                {
                    fs.Seek(0, SeekOrigin.Begin);
                    fs.Write(BitConverter.GetBytes((int)fs.Length), 0, 4);
                }
            }
            catch (Exception e)
            {
                if (fs != null)
                    fs.Close();
                File.Delete(path);
                fs = null;
                throw new WebException("HttpClient transfer net data", e);
            }
            finally
            {
                if (istream != null)
                    istream.Close();
            }
        }
    }
    #endregion
    #region ReflectionCache
    public sealed class ReflectionCache
    {
        public delegate object ToStringCast(object obj);
        public interface Invokable
        {
            object Invoke(object[] parameters);
            object GetTarget();
        }
        private static readonly Dictionary<Type, Class> cache = new Dictionary<Type, Class>();
        private readonly ToStringCast toString;
        public ReflectionCache(ToStringCast toString)
        {
            this.toString = toString;
        }
        private class CheckIConvertible
        {
            private readonly static Dictionary<Type, bool> stub = new Dictionary<Type, bool>();
            internal static bool check(Type type)
            {
                bool r;
                lock (stub)
                {
                    if (stub.TryGetValue(type, out r))
                        return r;
                }
                r = type.FindInterfaces((a, b) => a.Equals(b), typeof(IConvertible)).Length > 0;
                lock (stub)
                {
                    stub.Add(type, r);
                }
                return r;
            }
        }
        private int Compatible(object obj, Type type)
        {
            if (type.Equals(obj.GetType()))
                return 1;
            if (type.IsInstanceOfType(obj) || obj == null && !type.IsValueType)
                return 0;
            if (type.Equals(typeof(bool)) || type.Equals(typeof(string)))
                return -1;
            if (typeof(IConvertible).IsInstanceOfType(obj) && CheckIConvertible.check(type))
                return -2;
            return Int32.MinValue;
        }
        private int Evaluate(ParameterInfo[] pinfo, object[] parameters)
        {
            int evaluation = 0;
            foreach (ParameterInfo pi in pinfo)
            {
                int ev = Compatible(parameters[pi.Position], pi.ParameterType);
                if (ev == Int32.MinValue)
                    return Int32.MinValue;
                evaluation += ev;
            }
            return evaluation;
        }
        private object Cast(object obj, Type type)
        {
            if (type.IsInstanceOfType(obj) || obj == null && !type.IsValueType)
                return obj;
            switch (Type.GetTypeCode(type))
            {
                case TypeCode.Boolean:
                    if (obj == null)
                        return false;
                    switch (Type.GetTypeCode(obj.GetType()))
                    {
                        case TypeCode.DBNull:
                            return false;
                        case TypeCode.Byte:
                            break;
                        case TypeCode.SByte:
                            break;
                        case TypeCode.Int16:
                            break;
                        case TypeCode.UInt16:
                            break;
                        case TypeCode.Int32:
                            break;
                        case TypeCode.UInt32:
                            break;
                        case TypeCode.Int64:
                            break;
                        case TypeCode.UInt64:
                            break;
                        case TypeCode.Single:
                            break;
                        case TypeCode.Double:
                            break;
                        case TypeCode.Decimal:
                            break;
                        case TypeCode.Char:
                            return ((char)obj) != '\0';
                        case TypeCode.String:
                            return ((string)obj).Length != 0;
                        default:
                            return true;
                    }
                    break;
                case TypeCode.String:
                    return toString(obj);
            }
            return Convert.ChangeType(obj, type);
        }
        private object[] BindParameters(ParameterInfo[] pinfo, object[] parameters)
        {
            object[] args = new object[pinfo.Length];
            foreach (ParameterInfo pi in pinfo)
                args[pi.Position] = Cast(parameters[pi.Position], pi.ParameterType);
            return args;
        }
        private sealed class Class
        {
            private interface ValueOperation
            {
                Type TargetType();
                object GetValue(object obj);
                void SetValue(object obj, object value);
            }
            private sealed class Field : ValueOperation
            {
                private readonly FieldInfo info;
                internal Field(MemberInfo mbinfo)
                {
                    info = mbinfo as FieldInfo;
                }
                public Type TargetType()
                {
                    return info.FieldType;
                }
                public object GetValue(object obj)
                {
                    return info.GetValue(obj);
                }
                public void SetValue(object obj, object value)
                {
                    info.SetValue(obj, value);
                }
            }
            private sealed class Property : ValueOperation
            {
                private readonly PropertyInfo info;
                internal Property(MemberInfo mbinfo)
                {
                    info = mbinfo as PropertyInfo;
                }
                public Type TargetType()
                {
                    return info.PropertyType;
                }
                public object GetValue(object obj)
                {
                    return info.CanRead ? info.GetValue(obj, null) : DBNull.Value;
                }
                public void SetValue(object obj, object value)
                {
                    if (info.CanWrite)
                        info.SetValue(obj, value, null);
                }
            }
            private abstract class Callable
            {
                private readonly IList<ParameterInfo[]> pinfos = new List<ParameterInfo[]>();
                protected void Add(ParameterInfo[] pinfo)
                {
                    pinfos.Add(pinfo);
                }
                internal object Invoke(object obj, object[] parameters, object value, ReflectionCache refcache)
                {
                    List<int> candidate = new List<int>();
                    for (int i = 0; i < pinfos.Count; i++)
                        if (pinfos[i].Length == parameters.Length)
                            candidate.Add(i);
                    object[] args = null;
                    int index = -1;
                    switch (candidate.Count)
                    {
                        case 0:
                            break;
                        case 1:
                            try
                            {
                                args = refcache.BindParameters(pinfos[candidate[0]], parameters);
                                index = candidate[0];
                            }
                            catch (Exception) { }
                            break;
                        default:
                            {
                                int evmax = Int32.MinValue;
                                List<int> candidate2 = new List<int>();
                                foreach (int i in candidate)
                                {
                                    int ev = refcache.Evaluate(pinfos[i], parameters);
                                    if (ev == Int32.MinValue || ev < evmax)
                                        continue;
                                    if (evmax < ev)
                                    {
                                        evmax = ev;
                                        candidate2.Clear();
                                    }
                                    candidate2.Add(i);
                                }
                                foreach (int i in candidate2)
                                {
                                    try { args = refcache.BindParameters(pinfos[i], parameters); }
                                    catch (Exception) { continue; }
                                    if (index != -1)
                                        throw new AmbiguousMatchException();
                                    index = i;
                                }
                                break;
                            }
                    }
                    return index == -1 ? Missing() : Invoke(index, obj, args, value, refcache);
                }
                protected abstract object Invoke(int index, object obj, object[] parameters, object value, ReflectionCache refcache);
                protected virtual object Missing()
                {
                    return DBNull.Value;
                }
            }
            private sealed class Method : Callable
            {
                private readonly IList<MethodInfo> methods = new List<MethodInfo>();
                internal void Add(MemberInfo mbinfo)
                {
                    MethodInfo method = mbinfo as MethodInfo;
                    base.Add(method.GetParameters());
                    methods.Add(method);
                }
                protected sealed override object Invoke(int index, object obj, object[] parameters, object value, ReflectionCache refcache)
                {
                    object r = methods[index].Invoke(obj, parameters);
                    return methods[index].ReturnType.Equals(typeof(void)) ? DBNull.Value : r;
                }
                protected sealed override object Missing()
                {
                    throw new Exception("Non-Method matched");
                }
            }
            private sealed class Constructor : Callable
            {
                private readonly IList<ConstructorInfo> ctors = new List<ConstructorInfo>();
                internal void Add(MemberInfo mbinfo)
                {
                    ConstructorInfo ctor = mbinfo as ConstructorInfo;
                    base.Add(ctor.GetParameters());
                    ctors.Add(ctor);
                }
                protected sealed override object Invoke(int index, object obj, object[] parameters, object value, ReflectionCache refcache)
                {
                    return ctors[index].Invoke(parameters);
                }
                protected sealed override object Missing()
                {
                    throw new Exception("Non-Constructor matched");
                }
            }
            private sealed class IndexedPropertyGetter : Callable
            {
                private readonly IList<PropertyInfo> ppinfos = new List<PropertyInfo>();
                internal void Add(PropertyInfo ppinfo)
                {
                    base.Add(ppinfo.GetIndexParameters());
                    ppinfos.Add(ppinfo);
                }
                protected sealed override object Invoke(int index, object obj, object[] parameters, object value, ReflectionCache refcache)
                {
                    return ppinfos[index].GetValue(obj, parameters);
                }
            }
            private sealed class IndexedPropertySetter : Callable
            {
                private readonly IList<PropertyInfo> ppinfos = new List<PropertyInfo>();
                internal void Add(PropertyInfo ppinfo)
                {
                    base.Add(ppinfo.GetIndexParameters());
                    ppinfos.Add(ppinfo);
                }
                protected sealed override object Invoke(int index, object obj, object[] parameters, object value, ReflectionCache refcache)
                {
                    ppinfos[index].SetValue(obj, refcache.Cast(value, ppinfos[index].PropertyType), parameters);
                    return null;
                }
            }
            private readonly ReflectionCache refcache;
            private readonly IDictionary<object, object> cache = new Dictionary<object, object>();
            private readonly Constructor constructor = new Constructor();
            private readonly IndexedPropertyGetter indexedPropertyGetter = new IndexedPropertyGetter();
            private readonly IndexedPropertySetter indexedPropertySetter = new IndexedPropertySetter();
            internal Class(Type type, ReflectionCache refcache)
            {
                this.refcache = refcache;
                foreach (var mbinfo in type.GetMembers())
                {
                    switch (mbinfo.MemberType)
                    {
                        case MemberTypes.Field:
                            cache.Add(mbinfo.Name, new Field(mbinfo));
                            break;
                        case MemberTypes.Property:
                            if (mbinfo.Name.Equals("Item"))
                            {
                                PropertyInfo ppinfo = mbinfo as PropertyInfo;
                                if (ppinfo.CanRead)
                                    indexedPropertyGetter.Add(ppinfo);
                                if (ppinfo.CanWrite)
                                    indexedPropertySetter.Add(ppinfo);
                            }
                            else
                                cache.Add(mbinfo.Name, new Property(mbinfo));
                            break;
                        case MemberTypes.Method:
                            Method method;
                            object _method;
                            if (cache.TryGetValue(mbinfo.Name, out _method))
                                method = _method as Method;
                            else
                                cache.Add(mbinfo.Name, method = new Method());
                            method.Add(mbinfo);
                            break;
                        case MemberTypes.Constructor:
                            constructor.Add(mbinfo);
                            break;
                    }
                }
                cache.Add("toString", cache["ToString"]);
            }
            private sealed class InvokableObject : Invokable
            {
                private readonly object obj;
                private readonly Method method;
                private readonly ReflectionCache refcache;
                internal InvokableObject(object obj, Method method, ReflectionCache refcache)
                {
                    this.obj = obj;
                    this.method = method;
                    this.refcache = refcache;
                }
                public object Invoke(object[] parameters)
                {
                    return method.Invoke(obj, parameters, null, refcache);
                }
                public object GetTarget()
                {
                    return typeof(Delegate).IsInstanceOfType(obj) ? obj : this;
                }
            }
            internal object GetValue(object obj, object key)
            {
                object info;
                if (cache.TryGetValue(key, out info))
                {
                    ValueOperation gs = info as ValueOperation;
                    if (gs != null)
                        return gs.GetValue(obj);
                    Method method = info as Method;
                    if (method != null)
                        return new InvokableObject(obj, method, refcache);
                    return DBNull.Value;
                }
                return indexedPropertyGetter.Invoke(obj, new object[] { key }, null, refcache);
            }
            internal void SetValue(object obj, object key, object value)
            {
                object info;
                if (cache.TryGetValue(key, out info))
                {
                    ValueOperation gs = info as ValueOperation;
                    if (gs != null)
                        gs.SetValue(obj, refcache.Cast(value, gs.TargetType()));
                    return;
                }
                indexedPropertySetter.Invoke(obj, new object[] { key }, value, refcache);
            }
            internal object Construct(object[] parameters)
            {
                return constructor.Invoke(null, parameters, null, refcache);
            }
        }
        private Class GetClass(Type type)
        {
            Class clazz;
            lock (cache)
            {
                if (!cache.TryGetValue(type, out clazz))
                    cache.Add(type, clazz = new Class(type, this));
            }
            return clazz;
        }
        public object GetValue(object obj, object name)
        {
            return GetClass(obj.GetType()).GetValue(obj, name);
        }
        public void SetValue(object obj, object name, object value)
        {
            GetClass(obj.GetType()).SetValue(obj, name, value);
        }
        public object Construct(object obj, object[] parameters)
        {
            return GetClass((Type)obj).Construct(parameters);
        }
    }
    #endregion
}