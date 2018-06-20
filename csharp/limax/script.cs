using System;
using System.Collections.Generic;
using limax.endpoint.providerendpoint;
using limax.codec;
using limax.defines;

namespace limax.endpoint.script
{
    public interface DictionaryCache
    {
        void put(string key, string value);
        string get(string key);
        ICollection<string> keys();
    }
    public sealed class SimpleDictionaryCache : DictionaryCache
    {
        private readonly IDictionary<string, string> map = new Dictionary<string, string>();
        public void put(string key, string value)
        {
            lock (map)
            {
                map.Add(key, value);
            }
        }
        public string get(string key)
        {
            lock (map)
            {
                string value;
                return map.TryGetValue(key, out value) ? value : null;
            }
        }
        public ICollection<string> keys()
        {
            lock (map)
            {
                return map.Keys;
            }
        }
    }
    public interface ScriptSender
    {
        Exception send(string s);
    }
    public interface LmkDataReceiver
    {
        void onLmkData(string lmkdata, Action done);
    }
    public interface ScriptEngineHandle
    {
        ISet<int> getProviders();
        int action(int t, object p);
        void registerScriptSender(ScriptSender sender);
        void registerLmkDataReceiver(LmkDataReceiver receiver);
        void registerProviderLoginDataManager(ProviderLoginDataManager pldm);
        DictionaryCache getDictionaryCache();
        void tunnel(int providerid, int label, string data);
        void tunnel(int providerid, int label, Octets data);
    }
    public delegate void TunnelReceivier(int providerid, int label, string data);
}
