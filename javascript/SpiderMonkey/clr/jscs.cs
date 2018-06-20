using System;
using System.Collections;
using System.Collections.Generic;
using System.Text;
using System.Threading;

using limax.codec;
using limax.script;
using limax.util;
using limax.defines;

namespace limax.endpoint.script
{
    public class JsContext
    {
        public delegate void JsConsumer(Js js);
        private readonly SingleThreadExecutor executor = new SingleThreadExecutor("js");
        private Js js;
        public JsContext(uint maxbytes)
        {
            sync(_ => js = new Js(maxbytes));
        }
        public JsContext()
        {
            sync(_ => js = new Js());
        }
        public void sync(JsConsumer jsc)
        {
            executor.wait(() => jsc(js));
        }
        public void async(JsConsumer jsc)
        {
            executor.execute(() => jsc(js));
        }
        public void shutdown()
        {
            executor.shutdown();
        }
    }
    public class JavaScriptHandle : ScriptEngineHandle
    {
        private readonly JsContext jsc;
        private readonly DictionaryCache cache;
        private readonly ISet<int> providers = new HashSet<int>();
        private JsFunction instance;
        private volatile LmkDataReceiver lmkDataReceiver;
        public JavaScriptHandle(JsContext jsc, string init, ICollection<int> providers, DictionaryCache cache, TunnelReceivier ontunnel)
        {
            this.jsc = jsc;
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
            jsc.sync(js =>
            {
                js.name("limax.js").eval(limax.script.codes.js.limax);
                js.name("cache").eval("var cache=<0>", this.cache);
                js.name("ontunnel").eval("var ontunnel=<0>", _ontunnel);
                js.name("initscript").eval(init);
                if (providers == null || providers.Count == 0)
                    foreach (object pvid in (JsArray)js.eval("providers"))
                        this.providers.Add((int)pvid);
                else
                    foreach (var pvid in providers)
                        this.providers.Add(pvid);
                this.instance = (JsFunction)js.name("").eval("limax");
            });
        }
        public JavaScriptHandle(JsContext jsc, string init, ICollection<int> providers, DictionaryCache cache)
            : this(jsc, init, providers, cache, null)
        {
        }
        public JavaScriptHandle(JsContext jsc, string init, DictionaryCache cache)
            : this(jsc, init, null, cache, null)
        {
        }
        public JavaScriptHandle(JsContext jsc, string init, ICollection<int> providers)
            : this(jsc, init, providers, null, null)
        {
        }
        public JavaScriptHandle(JsContext jsc, string init)
            : this(jsc, init, null, null, null)
        {
        }
        public ISet<int> getProviders()
        {
            return providers;
        }
        public int action(int t, object p)
        {
            int r = 0;
            jsc.sync(_ => r = (int)instance(t, p));
            return r;
        }
        private delegate object Send(string s);
        public void registerScriptSender(ScriptSender sender)
        {
            Send send = s =>
            {
                object r = sender.send(s);
                return r == null ? DBNull.Value : r;
            };
            jsc.async(_ => instance(0, send));
        }
        public void registerLmkDataReceiver(LmkDataReceiver receiver)
        {
            this.lmkDataReceiver = receiver;
        }
        public void registerProviderLoginDataManager(ProviderLoginDataManager pldm)
        {
            StringBuilder sb = new StringBuilder("var __logindatas = {};\n");
            foreach (int pvid in pldm.getProviderIds())
            {
                string data = Encoding.ASCII.GetString(Base64Encode.transform(pldm.getData(pvid).getBytes()));
                sb.Append("__logindatas[" + pvid + "] = { data : '" + data + "', ");
                sb.Append(pldm.isSafe(pvid) ? "label : " + pldm.getLabel(pvid) + "};\n" : "base64 : 1 };\n");
            }
            sb.Append("limax(__logindatas);\n");
            jsc.sync(js => js.name("registerLoginDatas").eval(sb.ToString()));
        }
        public DictionaryCache getDictionaryCache()
        {
            return cache;
        }
        public void tunnel(int providerid, int label, string data)
        {
            jsc.sync(_ => instance(providerid, label, data));
        }
        public void tunnel(int providerid, int label, Octets data)
        {
            tunnel(providerid, label, Octets.wrap(Base64Encode.transform(data.getBytes())));
        }
    }
}