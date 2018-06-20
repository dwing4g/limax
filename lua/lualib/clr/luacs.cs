using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;

using limax.codec;
using limax.script;
using limax.defines;

namespace limax.endpoint.script
{
    public class LuaScriptHandle : ScriptEngineHandle
    {
        private readonly Lua lua;
        private readonly DictionaryCache cache;
        private readonly ISet<int> providers = new HashSet<int>();
        private readonly LuaFunction instance;
        private volatile LmkDataReceiver lmkDataReceiver;
        public LuaScriptHandle(Lua lua, string init, ICollection<int> providers, DictionaryCache cache, TunnelReceivier ontunnel)
        {
            this.lua = lua;
            this.cache = cache != null ? cache : new SimpleDictionaryCache();
            TunnelReceivier _ontunnel = (providerid, label, data) =>
            {
                if (providerid == AuanyService.providerId)
                {
                    if (lmkDataReceiver != null)
                        lmkDataReceiver.onLmkData(data, () => tunnel(AuanyService.providerId, -1, ""));
                }
                else if (ontunnel != null)
                    ontunnel(providerid, label, data);
            };
            IDictionary r = (IDictionary)lua.name("initscript").eval(init);
            if (providers == null || providers.Count == 0)
                foreach (object pvid in ((IDictionary)r["pvids"]).Values)
                    this.providers.Add((int)(long)pvid);
            else
                foreach (int pvid in providers)
                    this.providers.Add(pvid);
            this.instance = (LuaFunction)lua.name("").eval("return <0>(<1>,<2>,<3>)", lua.name("limax.lua").eval(limax.script.codes.lua.limax), r["callback"], this.cache, _ontunnel);
        }
        public LuaScriptHandle(Lua lua, string init, ICollection<int> providers, DictionaryCache cache)
            : this(lua, init, providers, cache, null)
        {

        }
        public LuaScriptHandle(Lua lua, string init, DictionaryCache cache)
            : this(lua, init, null, cache, null)
        {
        }
        public LuaScriptHandle(Lua lua, string init, ICollection<int> providers)
            : this(lua, init, providers, null, null)
        {
        }
        public LuaScriptHandle(Lua lua, string init)
            : this(lua, init, null, null, null)
        {
        }
        public ISet<int> getProviders()
        {
            return providers;
        }
        public int action(int t, object p)
        {
            return (int)(long)instance(t, p);
        }
        private delegate Exception Send(string s);
        public void registerScriptSender(ScriptSender sender)
        {
            instance(0, (Send)sender.send);
        }
        public void registerLmkDataReceiver(LmkDataReceiver receiver)
        {
            this.lmkDataReceiver = receiver;
        }
        public void registerProviderLoginDataManager(ProviderLoginDataManager pldm)
        {
            StringBuilder sb = new StringBuilder("local __logindatas = {};\n");
            foreach (int pvid in pldm.getProviderIds())
            {
                string data = Encoding.ASCII.GetString(Base64Encode.transform(pldm.getData(pvid).getBytes()));
                sb.Append("__logindatas[" + pvid + "] = { data = '" + data + "',");
                sb.Append(pldm.isSafe(pvid) ? "label = " + pldm.getLabel(pvid) + "}\n" : "base64 = 1}\n");
            }
            sb.Append("return __logindatas");
            instance(lua.name("registerLoginDatas").eval(sb.ToString()));
        }
        public DictionaryCache getDictionaryCache()
        {
            return cache;
        }
        public void tunnel(int providerid, int label, string data)
        {
            instance(providerid, label, data);
        }
        public void tunnel(int providerid, int label, Octets data)
        {
            tunnel(providerid, label, Octets.wrap(Base64Encode.transform(data.getBytes())));
        }
    }
}
