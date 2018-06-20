using limax.codec;
using limax.defines;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;

namespace limax.endpoint.variant
{
    internal interface AbstractVariantView : VariantView
    {
        View getView();
    }
    public interface CollectionDeclaration : Declaration
    {
        Declaration getValue();
    }
    public interface Declaration
    {
        VariantType getType();
        MarshalMethod createMarshalMethod();
    }
    public static class DeclarationCreator
    {
        public static Declaration create(VariantType type, params Declaration[] args)
        {
            switch (type)
            {
                case VariantType.Null:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.Null;
                case VariantType.Boolean:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.Boolean;
                case VariantType.Byte:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.Byte;
                case VariantType.Short:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.Short;
                case VariantType.Int:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.Int;
                case VariantType.Long:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.Long;
                case VariantType.Float:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.Float;
                case VariantType.Double:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.Double;
                case VariantType.String:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.String;
                case VariantType.Binary:
                    if (0 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.Binary;
                case VariantType.List:
                    if (1 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.createList(args[0]);
                case VariantType.Vector:
                    if (1 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.createVector(args[0]);
                case VariantType.Set:
                    if (1 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.createSet(args[0]);
                case VariantType.Map:
                    if (2 != args.Length)
                        throw new ArgumentException();
                    return DeclarationImpl.createMap(args[0], args[1]);
                default:
                    throw new Exception("unsupported operation");
            }
        }
    }
    internal static class DeclarationImpl
    {
        private class NullMarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.Null;
            }

            public Declaration getDeclaration()
            {
                return DeclarationImpl.Null;
            }
        }
        private static readonly MarshalMethod nullMarshalMethod = new NullMarshalMethod();
        private sealed class NullDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Null;
            }
            public MarshalMethod createMarshalMethod()
            {
                return nullMarshalMethod;
            }
        }
        internal static readonly Declaration Null = new NullDeclaration();
        private sealed class ObjectDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Object;
            }
            public MarshalMethod createMarshalMethod()
            {
                return nullMarshalMethod;
            }
        }
        internal static readonly Declaration Object = new ObjectDeclaration();
        private sealed class BooleanDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Boolean;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.booleanMethod;
            }
        };
        internal static readonly Declaration Boolean = new BooleanDeclaration();
        private sealed class ByteDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Byte;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.int8Method;
            }
        };
        internal static readonly Declaration Byte = new ByteDeclaration();
        private sealed class ShortDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Short;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.int16Method;
            }
        }
        internal static readonly Declaration Short = new ShortDeclaration();
        private sealed class IntDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Int;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.int32Method;
            }
        }
        internal static readonly Declaration Int = new IntDeclaration();
        private sealed class LongDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Long;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.int64Method;
            }
        }
        internal static Declaration Long = new LongDeclaration();
        private sealed class FloatDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Float;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.floatMethod;
            }
        }
        internal static Declaration Float = new FloatDeclaration();
        private sealed class DoubleDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Double;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.doubleMethod;
            }
        }
        internal static readonly Declaration Double = new DoubleDeclaration();
        private sealed class StringDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.String;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.stringMethod;
            }
        }
        internal static readonly Declaration String = new StringDeclaration();
        private sealed class BinaryDeclaration : Declaration
        {
            public VariantType getType()
            {
                return VariantType.Binary;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.octetsMethod;
            }
        }
        internal static readonly Declaration Binary = new BinaryDeclaration();
        private sealed class ListDeclaration : CollectionDeclaration
        {
            private readonly Declaration value;
            public ListDeclaration(Declaration value)
            {
                this.value = value;
            }
            public VariantType getType()
            {
                return VariantType.List;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.createListMethod(this, value.createMarshalMethod());
            }
            public Declaration getValue()
            {
                return value;
            }
        }
        internal static Declaration createList(Declaration value)
        {
            return new ListDeclaration(value);
        }
        private sealed class VectorDeclaration : CollectionDeclaration
        {
            private readonly Declaration value;
            public VectorDeclaration(Declaration value)
            {
                this.value = value;
            }
            public VariantType getType()
            {
                return VariantType.Vector;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.createVectorMethod(this, value.createMarshalMethod());
            }
            public Declaration getValue()
            {
                return value;
            }
        };
        internal static Declaration createVector(Declaration value)
        {
            return new VectorDeclaration(value);
        }
        private sealed class SetDeclaration : CollectionDeclaration
        {
            private readonly Declaration value;
            public SetDeclaration(Declaration value)
            {
                this.value = value;
            }
            public VariantType getType()
            {
                return VariantType.Set;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.createSetMethod(this, value.createMarshalMethod());
            }
            public Declaration getValue()
            {
                return value;
            }
        };
        internal static Declaration createSet(Declaration value)
        {
            return new SetDeclaration(value);
        }
        private sealed class MapDeclaration : limax.endpoint.variant.MapDeclaration
        {
            private readonly Declaration key;
            private readonly Declaration value;
            public MapDeclaration(Declaration key, Declaration value)
            {
                this.key = key;
                this.value = value;
            }
            public Declaration getValue()
            {
                return value;
            }
            public Declaration getKey()
            {
                return key;
            }
            public VariantType getType()
            {
                return VariantType.Map;
            }
            public MarshalMethod createMarshalMethod()
            {
                return MarshalMethods.createMapMethod(this, key.createMarshalMethod(), value.createMarshalMethod());
            }
        };
        internal static Declaration createMap(Declaration key, Declaration value)
        {
            return new MapDeclaration(key, value);
        }
    }
    public sealed class DynamicTemporaryView : TemporaryView
    {
        private readonly DynamicViewImpl impl;
        private readonly TemporaryViewHandler handler;
        internal readonly VariantView variantView;
        internal DynamicTemporaryView(int providerId, ViewDefine viewDefine, TemporaryViewHandler handler, ViewContext vc)
            : base(vc)
        {
            this.impl = new DynamicViewImpl(providerId, viewDefine);
            this.handler = handler;
            this.variantView = new VariantViewImpl(this);
        }
        protected override void onOpen(ICollection<long> sessionids)
        {
            handler.onOpen(variantView, sessionids);
        }
        protected override void onClose()
        {
            handler.onClose(variantView);
        }
        protected override void onAttach(long sessionid)
        {
            handler.onAttach(variantView, sessionid);
        }
        protected override void detach(long sessionid, byte reason)
        {
            onDetach(sessionid, reason);
            this.impl.removeSession(sessionid);
        }
        protected override void onDetach(long sessionid, byte reason)
        {
            handler.onDetach(variantView, sessionid, reason);
        }
        public override short getClassIndex()
        {
            return this.impl.classindex;
        }
        protected override void onData(long sessionid, byte index, byte field, Octets data, Octets dataremoved)
        {
            this.impl.onData(sessionid, index, field, data, dataremoved, (fieldname, value, type) => onViewChanged(sessionid, fieldname, value, type));
        }
        protected override ISet<string> getFieldNames()
        {
            return this.impl.getFieldNames();
        }
        private class VariantViewImpl : AbstractVariantView
        {
            private readonly DynamicTemporaryView outer;
            internal VariantViewImpl(DynamicTemporaryView outer)
            {
                this.outer = outer;
            }
            private class _Control : Control
            {
                private readonly DynamicTemporaryView outer;
                private readonly ViewDefine.ControlDefine define;
                private readonly Variant arg;
                internal _Control(DynamicTemporaryView outer, ViewDefine.ControlDefine define, Variant arg)
                    : base(outer)
                {
                    this.outer = outer;
                    this.define = define;
                    this.arg = arg;
                }
                public override short getViewClassIndex()
                {
                    return outer.getClassIndex();
                }
                public override OctetsStream marshal(OctetsStream os)
                {
                    return define.method.marshal(os, arg);
                }
                public override OctetsStream unmarshal(OctetsStream os)
                {
                    return os;
                }
                public override byte getControlIndex()
                {
                    return define.ctrlindex;
                }
            }
            public void sendControl(string name, Variant arg)
            {
                ViewDefine.ControlDefine define = outer.impl.getControlDefine(name);
                new _Control(outer, define, arg).send();
            }
            public void sendMessage(string msg)
            {
                outer.getViewContext().sendMessage(outer, msg);
            }
            private void _onViewChanged(VariantViewChangedListener listener, ViewChangedEvent e)
            {
                Variant value = e.value != null ? (Variant)e.value : Variant.Null;
                listener(new VariantViewChangedEvent(outer.variantView, e.sessionid, e.fieldname, value, e.type));
            }
            public Action registerListener(VariantViewChangedListener listener)
            {
                return outer.registerListener(e => _onViewChanged(listener, e));
            }
            public Action registerListener(string fieldname, VariantViewChangedListener listener)
            {
                return outer.registerListener(fieldname, e => _onViewChanged(listener, e));
            }
            public String getViewName()
            {
                return outer.impl.viewName;
            }
            public void visitField(string fieldname, ViewVisitor<Variant> visitor)
            {
                lock (outer) { outer.impl.visitField(fieldname, visitor); }
            }
            public override string ToString()
            {
                return "[view = " + outer.impl.viewName + " ProviderId = " + outer.impl.providerId + " classindex = " + outer.impl.classindex + " instanceindex = " + getInstanceIndex() + "]";
            }
            public int getInstanceIndex()
            {
                return outer.getInstanceIndex();
            }
            public bool isTemporaryView()
            {
                return true;
            }
            private class _VariantViewDefinition : VariantViewDefinition
            {
                private readonly DynamicViewImpl impl;
                internal _VariantViewDefinition(DynamicViewImpl impl)
                {
                    this.impl = impl;
                }
                public string getViewName()
                {
                    return impl.viewName;
                }
                public bool isTemporary()
                {
                    return true;
                }
                public ICollection<string> getFieldNames()
                {
                    return impl.getDefineVariableNames();
                }
                public ICollection<string> getSubscribeNames()
                {
                    return impl.getDefineSubscribeNames();
                }
                public ICollection<string> getControlNames()
                {
                    return impl.getDefineControlNames();
                }
            }
            public VariantViewDefinition getDefinition()
            {
                return new _VariantViewDefinition(outer.impl);
            }
            public View getView()
            {
                return outer;
            }
        }
    }
    internal sealed class DynamicView : View
    {
        private readonly DynamicViewImpl impl;
        internal readonly VariantView variantView;
        internal DynamicView(int providerId, ViewDefine viewDefine, ViewContext vc)
            : base(vc)
        {
            this.impl = new DynamicViewImpl(providerId, viewDefine);
            variantView = new VariantViewImpl(this);
        }
        public override short getClassIndex()
        {
            return this.impl.classindex;
        }
        protected override void onData(long sessionid, byte index, byte field, Octets data, Octets dataremoved)
        {
            this.impl.onData(sessionid, index, field, data, dataremoved, (fieldname, value, type) => { onViewChanged(sessionid, fieldname, value, type); });
        }
        protected override ISet<string> getFieldNames()
        {
            return impl.getFieldNames();
        }
        private class VariantViewImpl : AbstractVariantView
        {
            private readonly DynamicView outer;
            internal VariantViewImpl(DynamicView outer)
            {
                this.outer = outer;
            }
            private class _Control : Control
            {
                private readonly DynamicView outer;
                private readonly ViewDefine.ControlDefine define;
                private readonly Variant arg;
                internal _Control(DynamicView outer, ViewDefine.ControlDefine define, Variant arg)
                    : base(outer)
                {
                    this.outer = outer;
                    this.define = define;
                    this.arg = arg;
                }
                public override short getViewClassIndex()
                {
                    return outer.getClassIndex();
                }
                public override OctetsStream marshal(OctetsStream os)
                {
                    return define.method.marshal(os, arg);
                }
                public override OctetsStream unmarshal(OctetsStream os)
                {
                    return os;
                }
                public override byte getControlIndex()
                {
                    return define.ctrlindex;
                }
            }
            public void sendControl(string name, Variant arg)
            {
                ViewDefine.ControlDefine define = outer.impl.getControlDefine(name);
                new _Control(outer, define, arg).send();
            }
            public void sendMessage(string msg)
            {
                outer.getViewContext().sendMessage(outer, msg);
            }
            private void _onViewChanged(VariantViewChangedListener listener, ViewChangedEvent e)
            {
                Variant value = e.value != null ? (Variant)e.value : Variant.Null;
                listener(new VariantViewChangedEvent(outer.variantView, e.sessionid, e.fieldname, value, e.type));
            }
            public Action registerListener(VariantViewChangedListener listener)
            {
                return outer.registerListener(e => _onViewChanged(listener, e));
            }
            public Action registerListener(string fieldname, VariantViewChangedListener listener)
            {
                return outer.registerListener(fieldname, e => _onViewChanged(listener, e));
            }
            public String getViewName()
            {
                return outer.impl.viewName;
            }
            public void visitField(string fieldname, ViewVisitor<Variant> visitor)
            {
                lock (outer) { outer.impl.visitField(fieldname, visitor); }
            }
            public override string ToString()
            {
                return "[view = " + outer.impl.viewName + " ProviderId = " + outer.impl.providerId + " classindex = " + outer.impl.classindex + "]";
            }
            public int getInstanceIndex()
            {
                return 0;
            }
            public bool isTemporaryView()
            {
                return false;
            }
            private class _VariantViewDefinition : VariantViewDefinition
            {
                private readonly DynamicViewImpl impl;
                internal _VariantViewDefinition(DynamicViewImpl impl)
                {
                    this.impl = impl;
                }
                public string getViewName()
                {
                    return impl.viewName;
                }
                public bool isTemporary()
                {
                    return false;
                }
                public ICollection<string> getFieldNames()
                {
                    return impl.getDefineVariableNames();
                }
                public ICollection<string> getSubscribeNames()
                {
                    return impl.getDefineSubscribeNames();
                }
                public ICollection<string> getControlNames()
                {
                    return impl.getDefineControlNames();
                }
            }
            public VariantViewDefinition getDefinition()
            {
                return new _VariantViewDefinition(outer.impl);
            }
            public View getView()
            {
                return outer;
            }
        }
    }
    internal class DynamicViewImpl
    {
        internal readonly int providerId;
        internal readonly string viewName;
        internal readonly short classindex;
        private readonly IDictionary<byte, ViewDefine.VariableDefine> vardefMap = new Dictionary<byte, ViewDefine.VariableDefine>();
        private readonly IDictionary<string, ViewDefine.ControlDefine> ctrldefMap = new Dictionary<string, ViewDefine.ControlDefine>();
        private readonly IDictionary<string, Variant> variableMap = new Dictionary<string, Variant>();
        private readonly IDictionary<string, Variant> subscribeMap = new Dictionary<string, Variant>();
        private readonly ISet<string> fieldnames;
        private ViewChangedType type = ViewChangedType.TOUCH;
        internal DynamicViewImpl(int providerId, ViewDefine viewDefine)
        {
            this.providerId = providerId;
            this.viewName = viewDefine.viewName;
            this.classindex = viewDefine.classindex;
            ISet<string> set = new HashSet<string>();
            foreach (ViewDefine.VariableDefine vd in viewDefine.vars)
            {
                vardefMap.Add(vd.varindex, vd);
                set.Add(vd.name);
            }
            foreach (ViewDefine.ControlDefine cd in viewDefine.ctrls)
                ctrldefMap.Add(cd.name, cd);
            this.fieldnames = set;
        }
        internal ISet<string> getFieldNames()
        {
            return fieldnames;
        }
        internal void visitField(string fieldname, ViewVisitor<Variant> visitor)
        {
            if (!fieldnames.Contains(fieldname))
                throw new Exception("In View " + viewName + " field " + fieldname + " not exists.");
            Variant o;
            if (!variableMap.TryGetValue(fieldname, out o))
            {
                if (!subscribeMap.TryGetValue(fieldname, out o))
                    o = Variant.createMap();
            }
            visitor(o);
        }
        internal void removeSession(long sessionid)
        {
            foreach (var subs in subscribeMap.Values)
                subs.getMapValue().Remove(Variant.create(sessionid));
        }
        internal ViewDefine.ControlDefine getControlDefine(string name)
        {
            ViewDefine.ControlDefine def;
            if (!ctrldefMap.TryGetValue(name, out def))
                throw new Exception("unknown control name \"" + name + "\"");
            return def;
        }
        internal delegate void FireViewChanged(string fieldname, object value, ViewChangedType type);
        private Variant removeMemberValue(long sessionid, string name)
        {
            Variant map;
            if (!subscribeMap.TryGetValue(name, out map))
                return Variant.Null;
            Variant key = Variant.create(sessionid);
            Variant old = map.getMapValue().TryGetValue(key, out old) ? old : Variant.Null;
            map.getMapValue().Remove(key);
            return old;
        }
        private Variant putMemberValue(long sessionid, string name, Variant var)
        {
            Variant map;
            if (!subscribeMap.TryGetValue(name, out map))
                subscribeMap.Add(name, map = Variant.createMap());
            return map.mapInsert(sessionid, var);
        }
        private Variant getMemberValue(long sessionid, string name)
        {
            Variant map;
            if (!subscribeMap.TryGetValue(name, out map))
                return Variant.Null;
            Variant val;
            return map.getMapValue().TryGetValue(Variant.create(sessionid), out val) ? val : Variant.Null;
        }
        internal void onData(long sessionid, byte index, byte field, Octets data, Octets dataremoved, FireViewChanged fvc)
        {
            Variant value;
            ViewDefine.VariableDefine vd;
            if (!vardefMap.TryGetValue((byte)(index & 0x7f), out vd))
                throw new Exception("view \"" + this + "\" lost var index = \"" + index + "\"");
            if ((index & 0x80) == 0x80)
            {
                if (vd.isSubscribe)
                    value = removeMemberValue(sessionid, vd.name);
                else
                {
                    variableMap.TryGetValue(vd.name, out value);
                    variableMap.Remove(vd.name);
                }
                fvc(vd.name, value, ViewChangedType.DELETE);
            }
            else if (data.size() == 0)
            {
                if (vd.isSubscribe)
                    value = getMemberValue(sessionid, vd.name);
                else
                    variableMap.TryGetValue(vd.name, out value);
                fvc(vd.name, value, type);
                type = ViewChangedType.TOUCH;
            }
            else if ((field & 0x80) == 0x80)
            {
                Variant o;
                value = vd.method.unmarshal(new OctetsStream(data));
                if (vd.isSubscribe)
                    o = putMemberValue(sessionid, vd.name, value);
                else
                {
                    if (variableMap.TryGetValue(vd.name, out o))
                        variableMap.Remove(vd.name);
                    else
                        o = Variant.Null;
                    variableMap.Add(vd.name, value);
                }
                fvc(vd.name, value, o == Variant.Null ? ViewChangedType.NEW : ViewChangedType.REPLACE);
            }
            else
            {
                if (variableMap.TryGetValue(vd.name, out value))
                {
                    type = ViewChangedType.REPLACE;
                }
                else
                {
                    type = ViewChangedType.NEW;
                    value = Variant.createStruct();
                    variableMap.Add(vd.name, value);
                }
                ViewDefine.BindVarDefine bindvar;
                vd.bindVars.TryGetValue(field, out bindvar);
                VariantType vt = bindvar.method.getDeclaration().getType();
                if (vt == VariantType.Map)
                {
                    Variant n = bindvar.method.unmarshal(OctetsStream.wrap(data));
                    Variant v = value.getVariant(bindvar.name);
                    if (Variant.Null == v)
                    {
                        v = n;
                        value.setValue(bindvar.name, v);
                    }
                    else
                        foreach (var i in n.getMapValue())
                            v.getMapValue().Add(i.Key, i.Value);
                    foreach (var i in DeclarationCreator.create(VariantType.Vector, ((MapDeclaration)bindvar.method.getDeclaration()).getKey()).createMarshalMethod().unmarshal(OctetsStream.wrap(dataremoved)).getCollectionValue())
                        v.getMapValue().Remove(i);
                }
                else if (vt == VariantType.Set)
                {
                    Variant v = value.getVariant(bindvar.name);
                    if (Variant.Null == v)
                        value.setValue(bindvar.name, v = bindvar.method.unmarshal(OctetsStream.wrap(data)));
                    else
                        foreach (var i in DeclarationCreator.create(VariantType.Vector, ((CollectionDeclaration)bindvar.method.getDeclaration()).getValue()).createMarshalMethod().unmarshal(OctetsStream.wrap(data)).getCollectionValue())
                            v.getCollectionValue().Add(i);
                    foreach (var i in DeclarationCreator.create(VariantType.Vector, ((CollectionDeclaration)bindvar.method.getDeclaration()).getValue()).createMarshalMethod().unmarshal(OctetsStream.wrap(dataremoved)).getCollectionValue())
                        v.getMapValue().Remove(i);
                }
                else
                {
                    value.setValue(bindvar.name, bindvar.method.unmarshal(OctetsStream.wrap(data)));
                }
            }
        }
        internal ICollection<string> getDefineVariableNames()
        {
            ICollection<string> names = new List<string>();
            foreach (ViewDefine.VariableDefine vd in vardefMap.Values)
                if (!vd.isSubscribe)
                    names.Add(vd.name);
            return names;
        }
        internal ICollection<string> getDefineSubscribeNames()
        {
            ICollection<string> names = new List<string>();
            foreach (ViewDefine.VariableDefine vd in vardefMap.Values)
                if (vd.isSubscribe)
                    names.Add(vd.name);
            return names;
        }
        internal ICollection<string> getDefineControlNames()
        {
            return ctrldefMap.Keys;
        }
    }
    public interface MapDeclaration : CollectionDeclaration
    {
        Declaration getKey();
    }
    public interface MarshalMethod
    {
        OctetsStream marshal(OctetsStream os, Variant v);
        Variant unmarshal(OctetsStream os);
        Declaration getDeclaration();
    }
    static internal class MarshalMethods
    {
        private sealed class BooleanMarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                os.marshal(v.getBooleanValue());
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.create(os.unmarshal_bool());
            }
            public Declaration getDeclaration()
            {
                return DeclarationImpl.Boolean;
            }
        }
        private sealed class Int8MarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                os.marshal(v.getInt8Value());
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.create(os.unmarshal_byte());
            }
            public Declaration getDeclaration()
            {
                return DeclarationImpl.Byte;
            }
        }
        private sealed class Int16MarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                os.marshal(v.getInt16Value());
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.create(os.unmarshal_short());
            }
            public Declaration getDeclaration()
            {
                return DeclarationImpl.Short;
            }
        }
        private sealed class Int32MarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                os.marshal(v.getInt32Value());
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.create(os.unmarshal_int());
            }
            public Declaration getDeclaration()
            {
                return DeclarationImpl.Int;
            }
        }
        private sealed class Int64MarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                os.marshal(v.getInt64Value());
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.create(os.unmarshal_long());
            }
            public Declaration getDeclaration()
            {
                return DeclarationImpl.Long;
            }
        }
        private sealed class FloatMarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                os.marshal(v.getFloatValue());
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.create(os.unmarshal_float());
            }
            public Declaration getDeclaration()
            {
                return DeclarationImpl.Float;
            }
        }
        private sealed class DoubleMarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                os.marshal(v.getDoubleValue());
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.create(os.unmarshal_double());
            }
            public Declaration getDeclaration()
            {
                return DeclarationImpl.Double;
            }
        }
        private sealed class StringMarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                os.marshal(v.getStringValue());
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.create(os.unmarshal_string());
            }
            public Declaration getDeclaration()
            {
                return DeclarationImpl.String;
            }
        }
        private sealed class OctetsMarshalMethod : MarshalMethod
        {
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                os.marshal(v.getOctetsValue());
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                return Variant.create(os.unmarshal_Octets());
            }
            public Declaration getDeclaration()
            {
                return DeclarationImpl.Binary;
            }
        }
        private abstract class CollectionMarshalMethod : MarshalMethod
        {
            private readonly MarshalMethod valuemm;
            private readonly Declaration decl;
            public CollectionMarshalMethod(MarshalMethod valuemm, Declaration decl)
            {
                this.valuemm = valuemm;
                this.decl = decl;
            }
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                ICollection<Variant> vs = v.getCollectionValue();
                os.marshal_size(vs.Count);
                foreach (var sv in vs)
                    valuemm.marshal(os, sv);
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                Variant v = createVariant();
                int count = os.unmarshal_size();
                for (int i = 0; i < count; i++)
                    v.collectionInsert(valuemm.unmarshal(os));
                return v;
            }
            public Declaration getDeclaration()
            {
                return decl;
            }
            protected abstract Variant createVariant();
        }
        private sealed class ListMarshalMethod : CollectionMarshalMethod
        {
            public ListMarshalMethod(MarshalMethod valuemm, Declaration decl)
                : base(valuemm, decl)
            {
            }
            protected override Variant createVariant()
            {
                return Variant.createList();
            }
        }
        private sealed class SetMarshalMethod : CollectionMarshalMethod
        {
            public SetMarshalMethod(MarshalMethod valuemm, Declaration decl)
                : base(valuemm, decl)
            {
            }
            protected override Variant createVariant()
            {
                return Variant.createSet();
            }
        }
        private sealed class VectorMarshalMethod : CollectionMarshalMethod
        {
            public VectorMarshalMethod(MarshalMethod valuemm, Declaration decl)
                : base(valuemm, decl)
            {
            }
            protected override Variant createVariant()
            {
                return Variant.createVector();
            }
        }
        private sealed class MapMarshalMethod : MarshalMethod
        {
            private readonly MarshalMethod keymm;
            private readonly MarshalMethod valuemm;
            private readonly Declaration decl;
            public MapMarshalMethod(MarshalMethod keymm, MarshalMethod valuemm, Declaration decl)
            {
                this.keymm = keymm;
                this.valuemm = valuemm;
                this.decl = decl;
            }
            public OctetsStream marshal(OctetsStream os, Variant v)
            {
                IDictionary<Variant, Variant> vs = v.getMapValue();
                os.marshal_size(vs.Count);
                foreach (var e in vs)
                {
                    keymm.marshal(os, e.Key);
                    valuemm.marshal(os, e.Value);
                }
                return os;
            }
            public Variant unmarshal(OctetsStream os)
            {
                Variant v = Variant.createMap();
                int count = os.unmarshal_size();
                for (int i = 0; i < count; i++)
                {
                    Variant key = keymm.unmarshal(os);
                    Variant value = valuemm.unmarshal(os);
                    v.mapInsert(key, value);
                }
                return v;
            }
            public Declaration getDeclaration()
            {
                return decl;
            }
        }
        internal static readonly MarshalMethod booleanMethod = new BooleanMarshalMethod();
        internal static readonly MarshalMethod int8Method = new Int8MarshalMethod();
        internal static readonly MarshalMethod int16Method = new Int16MarshalMethod();
        internal static readonly MarshalMethod int32Method = new Int32MarshalMethod();
        internal static readonly MarshalMethod int64Method = new Int64MarshalMethod();
        internal static readonly MarshalMethod floatMethod = new FloatMarshalMethod();
        internal static readonly MarshalMethod doubleMethod = new DoubleMarshalMethod();
        internal static readonly MarshalMethod stringMethod = new StringMarshalMethod();
        internal static readonly MarshalMethod octetsMethod = new OctetsMarshalMethod();
        internal static MarshalMethod createListMethod(Declaration list, MarshalMethod valueMethod)
        {
            return new ListMarshalMethod(valueMethod, list);
        }
        internal static MarshalMethod createSetMethod(Declaration set, MarshalMethod valueMethod)
        {
            return new SetMarshalMethod(valueMethod, set);
        }
        internal static MarshalMethod createVectorMethod(Declaration vector, MarshalMethod valueMethod)
        {
            return new VectorMarshalMethod(valueMethod, vector);
        }
        internal static MarshalMethod createMapMethod(Declaration map, MarshalMethod keyMethod, MarshalMethod valueMethod)
        {
            return new MapMarshalMethod(keyMethod, valueMethod, map);
        }
    }
    public interface Field
    {
        String getName();
        Declaration getDeclaration();
    }
    public interface StructDeclaration : Declaration
    {
        ICollection<Field> getFields();
    }
    public class StructDeclarationCreator
    {
        private class FieldImpl : Field
        {
            private readonly string name;
            private readonly MarshalMethod mm;
            public FieldImpl(string name, Declaration decl)
            {
                this.name = name;
                this.mm = decl.createMarshalMethod();
            }
            public string getName()
            {
                return name;
            }
            public Declaration getDeclaration()
            {
                return mm.getDeclaration();
            }
            internal MarshalMethod getMarshalMethod()
            {
                return mm;
            }
        }
        private IList<FieldImpl> variables;
        public StructDeclarationCreator()
        {
            this.variables = new List<FieldImpl>();
        }
        private class _StructDeclaration : StructDeclaration
        {
            private readonly StructDeclarationCreator creator;
            internal _StructDeclaration(StructDeclarationCreator creator)
            {
                this.creator = creator;
            }
            public VariantType getType()
            {
                return VariantType.Struct;
            }
            private class _MarshalMethod : MarshalMethod
            {
                private readonly StructDeclarationCreator creator;
                private readonly _StructDeclaration parent;
                internal _MarshalMethod(StructDeclarationCreator creator, _StructDeclaration parent)
                {
                    this.creator = creator;
                    this.parent = parent;
                }
                public OctetsStream marshal(OctetsStream os, Variant v)
                {
                    foreach (FieldImpl i in creator.variables)
                        i.getMarshalMethod().marshal(os, v.getVariant(i.getName()));
                    return os;
                }
                public Variant unmarshal(OctetsStream os)
                {
                    Variant v = Variant.createStruct();
                    foreach (FieldImpl i in creator.variables)
                        v.structSetValue(i.getName(), i.getMarshalMethod().unmarshal(os));
                    return v;
                }
                public Declaration getDeclaration()
                {
                    return parent;
                }
            }
            public MarshalMethod createMarshalMethod()
            {
                return new _MarshalMethod(creator, this);
            }
            public ICollection<Field> getFields()
            {
                return (ICollection<Field>)creator.variables;
            }
        }
        public StructDeclaration create()
        {
            return new _StructDeclaration(this);
        }
        public StructDeclarationCreator addFieldDefinition(string fieldname, Declaration decl)
        {
            variables.Add(new FieldImpl(fieldname, decl));
            return this;
        }
    }
    public interface SupportManageVariant
    {
        void setTemporaryViewHandler(string name, TemporaryViewHandler handler);
        TemporaryViewHandler getTemporaryViewHandler(string name, bool returnDeafault);
        short getViewClassIndex(string name);
        VariantManager getVariantManager();
    }
    public interface TemporaryViewHandler
    {
        void onOpen(VariantView view, ICollection<long> sessionids);
        void onClose(VariantView view);
        void onAttach(VariantView view, long sessionid);
        void onDetach(VariantView view, long sessionid, int reason);
    }
    public sealed class Variant
    {
        public static readonly Variant Null = new Variant(Data.nullData);
        public static readonly Variant True = new Variant(BooleanData.trueData);
        public static readonly Variant False = new Variant(BooleanData.falseData);
        private readonly Data data;
        private Variant(Data data)
        {
            this.data = data;
        }
        private Variant(ICollection<Variant> v, Declaration decl)
        {
            data = new CollectionData(v, decl);
        }
        private Variant(IDictionary<Variant, Variant> v, Declaration decl)
        {
            data = new MapData(v, decl);
        }
        private delegate Variant CreateVariantAction(object v);
        private static IDictionary<Type, CreateVariantAction> createVariantActionMap = new Dictionary<Type, CreateVariantAction>();
        static Variant()
        {
            createVariantActionMap.Add(typeof(byte), (v) => { return create((byte)v); });
            createVariantActionMap.Add(typeof(bool), (v) => { return create((bool)v); });
            createVariantActionMap.Add(typeof(short), (v) => { return create((short)v); });
            createVariantActionMap.Add(typeof(int), (v) => { return create((int)v); });
            createVariantActionMap.Add(typeof(long), (v) => { return create((long)v); });
            createVariantActionMap.Add(typeof(float), (v) => { return create((float)v); });
            createVariantActionMap.Add(typeof(double), (v) => { return create((double)v); });
            createVariantActionMap.Add(typeof(string), (v) => { return create((string)v); });
            createVariantActionMap.Add(typeof(Octets), (v) => { return create((Octets)v); });
        }
        public static Variant create(object v)
        {
            CreateVariantAction a;
            if (createVariantActionMap.TryGetValue(v.GetType(), out a))
                return a(v);
            throw new Exception("unsupported type \"" + v.GetType() + "\"");
        }
        public static Variant create(bool v)
        {
            return v ? True : False;
        }
        public static Variant create(byte v)
        {
            return new Variant(new NumberData<byte>(v, DeclarationImpl.Byte));
        }
        public static Variant create(short v)
        {
            return new Variant(new NumberData<short>(v, DeclarationImpl.Short));
        }
        public static Variant create(int v)
        {
            return new Variant(new NumberData<int>(v, DeclarationImpl.Int));
        }
        public static Variant create(long v)
        {
            return new Variant(new NumberData<long>(v, DeclarationImpl.Long));
        }
        public static Variant create(float v)
        {
            return new Variant(new NumberData<float>(v, DeclarationImpl.Float));
        }
        public static Variant create(double v)
        {
            return new Variant(new NumberData<double>(v, DeclarationImpl.Double));
        }
        public static Variant create(string v)
        {
            return new Variant(new StringData(v));
        }
        public static Variant create(Octets v)
        {
            return new Variant(new OctetsData(v));
        }
        public static Variant createList()
        {
            return new Variant(new LinkedList<Variant>(), DeclarationImpl.createList(DeclarationImpl.Object));
        }
        public static Variant createVector()
        {
            return new Variant(new List<Variant>(), DeclarationImpl.createVector(DeclarationImpl.Object));
        }
        public static Variant createSet()
        {
            return new Variant(new HashSet<Variant>(), DeclarationImpl.createSet(DeclarationImpl.Object));
        }
        public static Variant createMap()
        {
            return new Variant(new Dictionary<Variant, Variant>(), DeclarationImpl.createMap(DeclarationImpl.Object, DeclarationImpl.Object));
        }
        public static Variant createStruct()
        {
            return new Variant(new StructData());
        }
        public bool getBooleanValue()
        {
            return data.getBooleanValue();
        }
        public byte getInt8Value()
        {
            return data.getInt8Value();
        }
        public short getInt16Value()
        {
            return data.getInt16Value();
        }
        public int getInt32Value()
        {
            return data.getInt32Value();
        }
        public long getInt64Value()
        {
            return data.getInt64Value();
        }
        public float getFloatValue()
        {
            return data.getFloatValue();
        }
        public double getDoubleValue()
        {
            return data.getDoubleValue();
        }
        public string getStringValue()
        {
            return data.getStringValue();
        }
        public Octets getOctetsValue()
        {
            return data.getOctetsValue();
        }
        public ICollection<Variant> getCollectionValue()
        {
            return data.getListValue();
        }
        public IDictionary<Variant, Variant> getMapValue()
        {
            return data.getMapValue();
        }
        public bool getBoolean(string name)
        {
            return data.getBoolean(name);
        }
        public byte getInt8(string name)
        {
            return data.getInt8(name);
        }
        public short getInt16(string name)
        {
            return data.getInt16(name);
        }
        public int getInt32(string name)
        {
            return data.getInt32(name);
        }
        public long getInt64(string name)
        {
            return data.getInt64(name);
        }
        public float getFloat(string name)
        {
            return data.getFloat(name);
        }
        public double getDouble(string name)
        {
            return data.GetDouble(name);
        }
        public string getString(string name)
        {
            return data.GetString(name);
        }
        public Octets getOctets(string name)
        {
            return data.getOctets(name);
        }
        public Variant getVariant(string name)
        {
            return data.getVariant(name);
        }
        public bool isNull()
        {
            return Data.nullData == data;
        }
        public ICollection<Variant> getCollection(string name)
        {
            return data.getCollection(name);
        }
        public IDictionary<Variant, Variant> getMap(string name)
        {
            return data.getMap(name);
        }
        public void collectionInsert(object v)
        {
            collectionInsert(create(v));
        }
        public void collectionInsert(Variant v)
        {
            data.listInsert(v);
        }
        public Variant mapInsert(object k, object v)
        {
            return mapInsert(create(k), create(v));
        }
        public Variant mapInsert(object k, Variant v)
        {
            return mapInsert(create(k), v);
        }
        public Variant mapInsert(Variant k, object v)
        {
            return mapInsert(k, create(v));
        }
        public Variant mapInsert(Variant k, Variant v)
        {
            return data.mapInsert(k, v);
        }
        public void structSetValue(string name, Variant v)
        {
            data.structSetValue(name, v);
        }
        public void setValue(string name, object v)
        {
            structSetValue(name, create(v));
        }
        public void setValue(string name, Variant v)
        {
            structSetValue(name, v);
        }
        public Variant copy()
        {
            return new Variant(data.copy());
        }
        public override int GetHashCode()
        {
            return data.GetHashCode();
        }
        public override bool Equals(object obj)
        {
            Variant v = obj as Variant;
            return null == v ? false : v.data.Equals(data);
        }
        public override string ToString()
        {
            return data.ToString();
        }
        public Declaration makeDeclaration()
        {
            return data.makeDeclaration();
        }
        private class Data
        {
            public static readonly Data nullData = new Data();
            public virtual Declaration makeDeclaration()
            {
                return DeclarationImpl.Null;
            }
            public virtual bool getBooleanValue()
            {
                return default(bool);
            }
            public virtual byte getInt8Value()
            {
                return default(byte);
            }
            public virtual short getInt16Value()
            {
                return default(short);
            }
            public virtual int getInt32Value()
            {
                return default(int);
            }
            public virtual long getInt64Value()
            {
                return default(long);
            }
            public virtual float getFloatValue()
            {
                return default(float);
            }
            public virtual double getDoubleValue()
            {
                return default(double);
            }
            public virtual string getStringValue()
            {
                return "";
            }
            private static readonly Octets nullOctets = new Octets();
            public virtual Octets getOctetsValue()
            {
                return nullOctets;
            }
            public virtual ICollection<Variant> getListValue()
            {
                return util.Helper.emptyCollection<Variant>();
            }
            public virtual IDictionary<Variant, Variant> getMapValue()
            {
                return util.Helper.emptyDictionary<Variant, Variant>();
            }
            public bool getBoolean(string name)
            {
                return getVariant(name).getBooleanValue();
            }
            public byte getInt8(string name)
            {
                return getVariant(name).getInt8Value();
            }
            public short getInt16(string name)
            {
                return getVariant(name).getInt16Value();
            }
            public int getInt32(string name)
            {
                return getVariant(name).getInt32Value();
            }
            public long getInt64(string name)
            {
                return getVariant(name).getInt64Value();
            }
            public float getFloat(string name)
            {
                return getVariant(name).getFloatValue();
            }
            public double GetDouble(string name)
            {
                return getVariant(name).getDoubleValue();
            }
            public string GetString(string name)
            {
                return getVariant(name).getStringValue();
            }
            public Octets getOctets(string name)
            {
                return getVariant(name).getOctetsValue();
            }
            public virtual Variant getVariant(string name)
            {
                return Null;
            }
            public ICollection<Variant> getCollection(string name)
            {
                return getVariant(name).getCollectionValue();
            }
            public IDictionary<Variant, Variant> getMap(string name)
            {
                return getVariant(name).getMapValue();
            }
            public virtual void listInsert(Variant v)
            {
            }
            public virtual Variant mapInsert(Variant k, Variant v)
            {
                return Null;
            }
            public virtual void structSetValue(string name, Variant v)
            {
            }
            public virtual Data copy()
            {
                return this;
            }
            public override string ToString()
            {
                return "";
            }
            public override int GetHashCode()
            {
                return ToString().GetHashCode();
            }
            public override bool Equals(object obj)
            {
                return ToString().Equals(obj.ToString());
            }
        }
        private sealed class BooleanData : Data
        {
            public readonly static BooleanData trueData = new BooleanData(true);
            public readonly static BooleanData falseData = new BooleanData(false);
            private readonly bool value;
            private BooleanData(bool v)
            {
                value = v;
            }
            public override Declaration makeDeclaration()
            {
                return DeclarationImpl.Boolean;
            }
            public override byte getInt8Value()
            {
                return (byte)getInt32Value();
            }
            public override short getInt16Value()
            {
                return (short)getInt32Value();
            }
            public override int getInt32Value()
            {
                return value ? 1 : 0;
            }
            public override long getInt64Value()
            {
                return getInt64Value();
            }
            public override int GetHashCode()
            {
                return value ? 1231 : 1237;
            }
            public override string getStringValue()
            {
                return value ? "true" : "false";
            }
            public override string ToString()
            {
                return getStringValue();
            }
            public override bool Equals(object obj)
            {
                BooleanData d = obj as BooleanData;
                return null == d ? false : d.value == value;
            }
        }
        private sealed class NumberData<E> : Data
        {
            private readonly E value;
            private readonly Declaration decl;
            public NumberData(E v, Declaration decl)
            {
                value = v;
                this.decl = decl;
            }
            public override Declaration makeDeclaration()
            {
                return decl;
            }
            public override byte getInt8Value()
            {
                return Convert.ToByte(value);
            }
            public override short getInt16Value()
            {
                return Convert.ToInt16(value);
            }
            public override int getInt32Value()
            {
                return Convert.ToInt32(value);
            }
            public override long getInt64Value()
            {
                return Convert.ToInt64(value);
            }
            public override float getFloatValue()
            {
                return Convert.ToSingle(value);
            }
            public override double getDoubleValue()
            {
                return Convert.ToDouble(value);
            }
            public override string ToString()
            {
                return value.ToString();
            }
            public override int GetHashCode()
            {
                return value.GetHashCode();
            }
            public override bool Equals(object obj)
            {
                NumberData<E> d = obj as NumberData<E>;
                return null == d ? false : d.value.Equals(value);
            }
        }
        private sealed class StringData : Data
        {
            private readonly string value;
            public StringData(string v)
            {
                value = v;
            }
            public override Declaration makeDeclaration()
            {
                return DeclarationImpl.String;
            }
            public override String getStringValue()
            {
                return value;
            }
            public override string ToString()
            {
                return value;
            }
            public override bool Equals(object obj)
            {
                StringData d = obj as StringData;
                return null == d ? false : d.value.Equals(value);
            }
            public override int GetHashCode()
            {
                return value.GetHashCode();
            }
        }
        private sealed class OctetsData : Data
        {
            private readonly Octets value;
            public OctetsData(Octets v)
            {
                value = v;
            }
            public override Declaration makeDeclaration()
            {
                return DeclarationImpl.Binary;
            }
            public override Octets getOctetsValue()
            {
                return value;
            }
            public override string ToString()
            {
                return value.ToString();
            }
            public override bool Equals(object obj)
            {
                OctetsData d = obj as OctetsData;
                return null == d ? false : d.value.Equals(value);
            }
            public override int GetHashCode()
            {
                return value.GetHashCode();
            }
        }
        private sealed class StructData : Data
        {
            private readonly IDictionary<string, Variant> values = new Dictionary<string, Variant>();
            public StructData()
            {
            }
            public override Declaration makeDeclaration()
            {
                StructDeclarationCreator dec = new StructDeclarationCreator();
                foreach (var e in values)
                    dec.addFieldDefinition(e.Key, e.Value.makeDeclaration());
                return dec.create();
            }
            public override Variant getVariant(string name)
            {
                Variant v;
                return values.TryGetValue(name, out v) ? v : base.getVariant(name);
            }
            public override string ToString()
            {
                StringBuilder sb = new StringBuilder();
                sb.Append('[');
                foreach (var e in values)
                    sb.Append('(').Append(e.Key).Append(',').Append(e.Value.ToString()).Append(')');
                sb.Append(']');
                return sb.ToString();
            }
            public override int GetHashCode()
            {
                return util.Utils.hashCode(values);
            }
            public override bool Equals(object obj)
            {
                var d = obj as StructData;
                return null == d ? false : util.Utils.equals(d.values, values);
            }
            public override void structSetValue(string name, Variant v)
            {
                values.Remove(name);
                values.Add(name, v);
            }
            public override Data copy()
            {
                var data = new StructData();
                foreach (var e in values)
                    data.values.Add(e.Key, e.Value.copy());
                return data;
            }
        }
        private sealed class CollectionData : Data
        {
            private readonly ICollection<Variant> values;
            private readonly Declaration decl;
            public CollectionData(ICollection<Variant> v, Declaration decl)
            {
                this.values = v;
                this.decl = decl;
            }
            public override Declaration makeDeclaration()
            {
                return decl;
            }
            public override ICollection<Variant> getListValue()
            {
                return values;
            }
            public override string ToString()
            {
                StringBuilder sb = new StringBuilder();
                sb.Append('[');
                foreach (var v in values)
                    sb.Append(v.ToString()).Append(',');
                sb.Append(']');
                return sb.ToString();
            }
            public override int GetHashCode()
            {
                return util.Utils.hashCode_Collection(values);
            }
            public override bool Equals(object obj)
            {
                var d = obj as CollectionData;
                return null == d ? false : d.Equals(this);
            }
            public override void listInsert(Variant v)
            {
                values.Add(v);
            }
            public override Data copy()
            {
                var c = (ICollection<Variant>)Activator.CreateInstance(values.GetType());
                CollectionData data = new CollectionData(c, decl);
                foreach (var v in values)
                    data.values.Add(v.copy());
                return data;
            }
        }
        private sealed class MapData : Data
        {
            private readonly IDictionary<Variant, Variant> values;
            private readonly Declaration decl;
            public MapData(IDictionary<Variant, Variant> v, Declaration decl)
            {
                this.values = v;
                this.decl = decl;
            }
            public override Declaration makeDeclaration()
            {
                return decl;
            }
            public override IDictionary<Variant, Variant> getMapValue()
            {
                return values;
            }
            public override string ToString()
            {
                StringBuilder sb = new StringBuilder();
                sb.Append('[');
                foreach (var e in values)
                    sb.Append('(').Append(e.Key.ToString()).Append(',')
                            .Append(e.Value.ToString()).Append(')');
                sb.Append(']');
                return sb.ToString();
            }
            public override int GetHashCode()
            {
                return util.Utils.hashCode_Dictionary(values);
            }
            public override bool Equals(object obj)
            {
                var d = obj as MapData;
                return null == d ? false : util.Utils.equals_map(values, d.values);
            }
            public override Variant mapInsert(Variant k, Variant v)
            {
                Variant o;
                if (values.TryGetValue(k, out o))
                    values.Remove(k);
                else
                    o = Null;
                values.Add(k, v);
                return o;
            }
            public override Data copy()
            {
                var c = (IDictionary<Variant, Variant>)Activator.CreateInstance(values.GetType());
                var data = new MapData(c, decl);
                foreach (var e in values)
                    data.values.Add(e.Key, e.Value.copy());
                return data;
            }
        }
    }
    public class VariantManager
    {
        internal static View createDynamicViewInstance(int providerId, Object viewDefine, TemporaryViewHandler handler, ViewContext vc)
        {
            ViewDefine vd = (ViewDefine)viewDefine;
            if (vd.isTemporary)
                return new DynamicTemporaryView(providerId, vd, handler, vc);
            return new DynamicView(providerId, vd, vc);
        }
        static void parseViewDefines(VariantDefines vds, IDictionary<short, object> viewdefines, ISet<string> tempviewnames)
        {
            new ViewDefine.VariantDefineParser(vds).parse(viewdefines, tempviewnames);
        }
        private readonly SupportManageVariant manager;
        private readonly ViewContext viewContext;
        internal VariantManager(SupportManageVariant manager, ViewContext viewContext)
        {
            this.manager = manager;
            this.viewContext = viewContext;
        }
        public void setTemporaryViewHandler(string name,
                TemporaryViewHandler handler)
        {
            manager.setTemporaryViewHandler(name, handler);
        }
        public TemporaryViewHandler getTemporaryViewHandler(string name)
        {
            return manager.getTemporaryViewHandler(name, false);
        }
        public VariantView getSessionOrGlobalView(string name)
        {
            try
            {
                short classindex = manager.getViewClassIndex(name);
                return ((DynamicView)viewContext.getSessionOrGlobalView(classindex)).variantView;
            }
            catch (Exception)
            {
                return null;
            }
        }
        public VariantView findTemporaryView(string name, int instanceindex)
        {
            try
            {
                short classindex = manager.getViewClassIndex(name);
                return ((DynamicTemporaryView)viewContext.findTemporaryView(classindex, instanceindex)).variantView;
            }
            catch (Exception)
            {
                return null;
            }
        }
        public void sendMessage(VariantView view, string msg)
        {
            viewContext.sendMessage(((AbstractVariantView)view).getView(), msg);
        }

        public static VariantManager getInstance(EndpointManager manager, int pvid)
        {
            ViewContext vc = manager.getViewContext(pvid, ViewContextKind.Variant);
            return vc == null ? null : ((SupportManageVariant)vc).getVariantManager();
        }
    }
    public enum VariantType
    {
        Null,
        Boolean,
        Byte,
        Short,
        Int,
        Long,
        Float,
        Double,
        String,
        Binary,
        List,
        Vector,
        Set,
        Map,
        Struct,
        Object
    }
    public interface VariantView
    {
        string getViewName();
        void visitField(string fieldname, ViewVisitor<Variant> visitor);
        void sendControl(string controlname, Variant arg);
        void sendMessage(string msg);
        Action registerListener(VariantViewChangedListener listener);
        Action registerListener(string fieldname, VariantViewChangedListener listener);
        int getInstanceIndex();
        bool isTemporaryView();
        VariantViewDefinition getDefinition();
    }
    public struct VariantViewChangedEvent
    {
        readonly VariantView _view;
        readonly long _sessionid;
        readonly String _fieldname;
        readonly Variant _value;
        readonly ViewChangedType _type;
        internal VariantViewChangedEvent(VariantView view, long sessionid, string fieldname, Variant value, ViewChangedType type)
        {
            _view = view;
            _sessionid = sessionid;
            _fieldname = fieldname;
            _value = value;
            _type = type;
        }
        public VariantView view { get { return _view; } }
        public long sessionid { get { return _sessionid; } }
        public string fieldname { get { return _fieldname; } }
        public Variant value { get { return _value; } }
        public ViewChangedType type { get { return _type; } }
        public override string ToString()
        {
            return _view + " " + _sessionid + " " + _fieldname + " " + _value + " " + type;
        }
    }
    public delegate void VariantViewChangedListener(VariantViewChangedEvent e);
    public interface VariantViewDefinition
    {
        string getViewName();
        bool isTemporary();
        ICollection<string> getFieldNames();
        ICollection<string> getSubscribeNames();
        ICollection<string> getControlNames();
    }
    internal enum VariableType
    {
        Variable, Bind, Subscribe
    }
    internal class ViewDefine
    {
        internal readonly string viewName;
        internal readonly short classindex;
        internal readonly bool isTemporary;
        internal readonly ICollection<VariableDefine> vars = new List<VariableDefine>();
        internal readonly ICollection<ControlDefine> ctrls = new List<ControlDefine>();
        internal class BindVarDefine
        {
            internal readonly string name;
            internal readonly MarshalMethod method;
            public BindVarDefine(string name, Declaration decl)
            {
                this.name = name;
                this.method = decl.createMarshalMethod();
            }
        }
        internal class VariableDefine
        {
            internal readonly byte varindex;
            internal readonly bool isSubscribe;
            internal readonly bool isBind;
            internal readonly string name;
            internal readonly MarshalMethod method;

            internal readonly IDictionary<byte, BindVarDefine> bindVars = new Dictionary<byte, BindVarDefine>();

            public VariableDefine(byte varindex, bool isSubscribe, bool isBind, string name, Declaration decl)
            {
                this.varindex = varindex;
                this.isSubscribe = isSubscribe;
                this.isBind = isBind;
                this.name = name;
                this.method = decl.createMarshalMethod();
            }
        }

        internal class ControlDefine
        {
            internal readonly byte ctrlindex;
            internal readonly string name;
            internal readonly MarshalMethod method;

            public ControlDefine(byte ctrlindex, string name, Declaration decl)
            {
                this.ctrlindex = ctrlindex;
                this.name = name;
                this.method = decl.createMarshalMethod();
            }
        }
        public override string ToString()
        {
            return viewName;
        }
        ViewDefine(short classindex, string viewName, bool isTemporary)
        {
            this.viewName = viewName;
            this.classindex = classindex;
            this.isTemporary = isTemporary;
        }
        internal class VariantDefineParser
        {
            private class DeclarationStore
            {
                internal readonly IDictionary<int, Declaration> basemap = new Dictionary<int, Declaration>();
                internal readonly IDictionary<int, Declaration> beanmap = new Dictionary<int, Declaration>();

                internal DeclarationStore()
                {

                    basemap.Add(VariantDefines.BASE_TYPE_BINARY, DeclarationCreator.create(VariantType.Binary));
                    basemap.Add(VariantDefines.BASE_TYPE_BOOLEAN, DeclarationCreator.create(VariantType.Boolean));
                    basemap.Add(VariantDefines.BASE_TYPE_BYTE, DeclarationCreator.create(VariantType.Byte));
                    basemap.Add(VariantDefines.BASE_TYPE_DOUBLE, DeclarationCreator.create(VariantType.Double));
                    basemap.Add(VariantDefines.BASE_TYPE_FLOAT, DeclarationCreator.create(VariantType.Float));
                    basemap.Add(VariantDefines.BASE_TYPE_INT, DeclarationCreator.create(VariantType.Int));
                    basemap.Add(VariantDefines.BASE_TYPE_LONG, DeclarationCreator.create(VariantType.Long));
                    basemap.Add(VariantDefines.BASE_TYPE_SHORT, DeclarationCreator.create(VariantType.Short));
                    basemap.Add(VariantDefines.BASE_TYPE_STRING, DeclarationCreator.create(VariantType.String));
                }
                public void add(int type, Declaration dec)
                {
                    beanmap.Add(type, dec);
                }
                private Declaration getBase(int type, int typeKey, int typeValue)
                {
                    switch (type)
                    {
                        case VariantDefines.BASE_TYPE_LIST:
                            return DeclarationCreator.create(VariantType.List, get(typeValue));
                        case VariantDefines.BASE_TYPE_MAP:
                            return DeclarationCreator.create(VariantType.Map, get(typeKey), get(typeValue));
                        case VariantDefines.BASE_TYPE_SET:
                            return DeclarationCreator.create(VariantType.Set, get(typeValue));
                        case VariantDefines.BASE_TYPE_VECTOR:
                            return DeclarationCreator.create(VariantType.Vector, get(typeValue));
                    }
                    Declaration decl;
                    return basemap.TryGetValue(type, out decl) ? decl : null;
                }
                private Declaration get(int type)
                {
                    Declaration decl;
                    if (type < VariantDefines.BASE_TYPE_MAX)
                        return basemap.TryGetValue(type, out decl) ? decl : null;
                    return beanmap.TryGetValue(type, out decl) ? decl : null;
                }
                public Declaration get(int type, int typeKey, int typeValue)
                {
                    if (type < VariantDefines.BASE_TYPE_MAX)
                        return getBase(type, typeKey, typeValue);
                    Declaration decl;
                    return beanmap.TryGetValue(type, out decl) ? decl : null;
                }
            }
            private readonly VariantDefines defines;
            private readonly ICollection<ViewDefine> views = new List<ViewDefine>();
            private readonly DeclarationStore declarationStore = new DeclarationStore();
            internal VariantDefineParser(VariantDefines defines)
            {
                this.defines = defines;
            }
            string lookupdict(int key)
            {
                string r;
                return defines.namedict.TryGetValue(key, out r) ? r : null;
            }
            private string getName(VariantNameIds ids)
            {
                StringBuilder sb = new StringBuilder();
                bool had = false;
                foreach (int i in ids.ids)
                {
                    if (had)
                        sb.Append('.');
                    sb.Append(lookupdict(i));
                    had = true;
                }
                return sb.ToString();
            }
            private Declaration parseStructDeclaration(ICollection<VariantVariableDefine> vars)
            {
                StructDeclarationCreator decl = new StructDeclarationCreator();
                foreach (VariantVariableDefine vvd in vars)
                    decl.addFieldDefinition(lookupdict(vvd.name), declarationStore.get(vvd.type, vvd.typeKey, vvd.typeValue));
                return decl.create();
            }
            private VariantBeanDefine findVariantBeanDefine(int type)
            {
                if (declarationStore.basemap.ContainsKey(type))
                    return null;
                foreach (VariantBeanDefine bean in defines.beans)
                    if (bean.type == type)
                        return bean;
                throw new Exception("lost bean type = " + type);
            }
            private void parseBeans()
            {
                foreach (VariantBeanDefine vbd in defines.beans)
                    declarationStore.add(vbd.type, parseStructDeclaration(vbd.vars));
            }
            private void makeBindVariables(int type, VariableDefine define)
            {
                VariantBeanDefine beandefine = findVariantBeanDefine(type);
                if (null != beandefine)
                {
                    byte fieldindex = 0;
                    foreach (VariantVariableDefine var in beandefine.vars)
                        define.bindVars.Add(fieldindex++, new BindVarDefine(lookupdict(var.name), declarationStore.get(var.type, var.typeKey, var.typeValue)));
                }
            }

            private ViewDefine parseView(VariantViewDefine viewdef)
            {
                ViewDefine viewDefine = new ViewDefine(viewdef.clsindex, getName(viewdef.name), viewdef.istemp);
                byte index = 0;
                foreach (VariantViewVariableDefine vardef in viewdef.vars)
                {
                    VariableDefine define = new VariableDefine(index++, false, vardef.bind, lookupdict(vardef.name), declarationStore.get(vardef.type, vardef.typeKey, vardef.typeValue));
                    if (vardef.bind)
                        makeBindVariables(vardef.type, define);
                    viewDefine.vars.Add(define);
                }
                foreach (VariantViewVariableDefine subdef in viewdef.subs)
                {
                    VariableDefine define = new VariableDefine(index++, true, subdef.bind, lookupdict(subdef.name), declarationStore.get(subdef.type, subdef.typeKey, subdef.typeValue));
                    if (subdef.bind)
                        makeBindVariables(subdef.type, define);
                    viewDefine.vars.Add(define);
                }
                foreach (VariantViewControlDefine control in viewdef.ctrls)
                    viewDefine.ctrls.Add(new ControlDefine(index++, lookupdict(control.name), parseStructDeclaration(control.vars)));
                return viewDefine;
            }
            private void parseViews()
            {
                foreach (VariantViewDefine viewdef in defines.views)
                    views.Add(parseView(viewdef));
            }
            internal void parse(IDictionary<short, object> viewdefines, ISet<string> tempviewnames)
            {
                parseBeans();
                parseViews();
                foreach (ViewDefine vd in views)
                {
                    viewdefines.Add(vd.classindex, vd);
                    if (vd.isTemporary)
                        tempviewnames.Add(vd.viewName);
                }
            }
        }
    }
}
