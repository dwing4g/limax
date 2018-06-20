#pragma once

namespace limax {
	namespace helper {

		class DynamicControl : public View::Control
		{
			Variant arg;
			const ViewDefine::ControlDefine&	define;
		public:
			DynamicControl(Variant _arg, const ViewDefine::ControlDefine& _define)
				: arg(_arg), define(_define)
			{}
			virtual ~DynamicControl() {}
		public:
			virtual int8_t getControlIndex() const override
			{
				return define.getIndex();
			}
			virtual MarshalStream& marshal(MarshalStream& ms) const override
			{
				return define.getMarshalMethod()->marshal(ms, arg);
			}
			virtual const UnmarshalStream& unmarshal(const UnmarshalStream& us) override
			{
				return us;
			}
		};

		class DynamicViewImpl
		{
			int32_t providerId;
			std::string viewName;
			int16_t classindex;
			hashmap<int8_t, ViewDefine::VariableDefine> vardefMap;
			hashmap<std::string, ViewDefine::ControlDefine> ctrldefMap;
			hashmap<std::string, Variant> variableMap;
			hashmap<std::string, Variant> subscribeMap;
			hashset<std::string> fieldnames;
			ViewChangedType type;
		public:
			DynamicViewImpl(int32_t _providerId, std::shared_ptr<limax::ViewDefine> viewDefine)
				: providerId(_providerId), viewName(viewDefine->getViewName()), classindex(viewDefine->getClassIndex()), type(ViewChangedType::Touch)
			{
				for (const auto& var : viewDefine->getVaribaleDefine())
				{
					vardefMap.insert(std::make_pair(var.getIndex(), var));
					fieldnames.insert(var.getName());
				}
				for (const auto& ctrl : viewDefine->getControlDefine())
					ctrldefMap.insert(std::make_pair(ctrl.getName(), ctrl));
			}
			~DynamicViewImpl() {}
		public:
			int32_t getProviderId() const
			{
				return providerId;
			}
			int16_t getClassIndex() const
			{
				return classindex;
			}
			const std::string& getViewName() const
			{
				return viewName;
			}
			const hashset<std::string>& getFieldNames() const
			{
				return fieldnames;
			}
			void makeVariableNames(std::vector<std::string>& names) const
			{
				names.clear();
				for (const auto& define : vardefMap)
					if (!define.second.isSubscribe())
						names.push_back(define.second.getName());
			}
			void makeSubscribeNames(std::vector<std::string>& names)
			{
				names.clear();
				for (const auto& define : vardefMap)
				{
					if (define.second.isSubscribe())
						names.push_back(define.second.getName());
				}
			}
			void makeControlNames(std::vector<std::string>& names)
			{
				names.clear();
				for (const auto& define : ctrldefMap)
					names.push_back(define.second.getName());
			}
			void removeSession(int64_t sessionid)
			{
				for (auto& subs : subscribeMap)
					subs.second.getRawMapValue().erase(Variant::create(sessionid));
			}
			bool visitField(const std::string& fieldname, std::function<void(const Variant&)> visitor) const
			{
				if (fieldnames.find(fieldname) == fieldnames.end())
					return false;
				auto i1 = variableMap.find(fieldname);
				if (i1 != variableMap.end())
				{
					visitor(i1->second);
					return true;
				}
				Variant o;
				auto i2 = subscribeMap.find(fieldname);
				if (i2 != subscribeMap.end())
					o = i2->second;
				else
					o = Variant::Null;
				visitor(o);
				return true;
			}
			bool sendControl(View *view, const std::string& name, Variant arg, int32_t instanceindex) const
			{
				auto it = ctrldefMap.find(name);
				if (it == ctrldefMap.end())
					return false;
				const auto& define = it->second;
				DynamicControl control(arg, define);
				control.send(view);
				return true;
			}
		private:
			Variant getMemberValue(const std::string& name, int64_t sessionid) const
			{
				auto i1 = subscribeMap.find(name);
				if (i1 == subscribeMap.end())
					return Variant::Null;
				const auto& map = i1->second.getMapValue();
				auto i2 = map.find(Variant::create(sessionid));
				return i2 == map.end() ? Variant::Null : i2->second;
			}
			Variant putMemberValue(int64_t sessionid, const std::string& name, Variant var)
			{
				Variant value;
				auto it = subscribeMap.find(name);
				if (it == subscribeMap.end())
				{
					value = Variant::createMap();
					subscribeMap.insert(std::make_pair(name, value));
				}
				else
				{
					value = it->second;
				}
				return value.mapInsert(Variant::create(sessionid), var);
			}
			Variant removeMemberValue(int64_t sessionid, const std::string& name)
			{
				auto i1 = subscribeMap.find(name);
				if (i1 == subscribeMap.end())
					return Variant::Null;
				auto& map = i1->second.getRawMapValue();
				auto i2 = map.find(Variant::create(sessionid));
				if (i2 == map.end())
					return Variant::Null;
				Variant old = i2->second;
				map.erase(i2);
				return old;
			}
			Variant removeValue(const std::string& name)
			{
				auto it = variableMap.find(name);
				if (it == variableMap.end())
					return Variant::Null;
				Variant old = it->second;
				variableMap.erase(it);
				return old;
			}
			Variant putValue(const std::string& name, Variant var)
			{
				auto r = variableMap.insert(std::make_pair(name, var));
				if (r.second)
					return Variant::Null;
				auto old = r.first->second;
				variableMap[name] = var;
				return old;
			}
		public:
			bool onData(View* instance, int64_t sessionid, int8_t index, int8_t field, const limax::Octets& data, const Octets& dataremoved, void (View::*pfire)(View*, int64_t, const std::string&, const void*, ViewChangedType))
			{
				auto it = vardefMap.find((int8_t)(index & 0x7f));
				if (it == vardefMap.end())
					return false;
				const auto& vd = it->second;

				if (index < 0)
				{
					Variant old = vd.isSubscribe() ? removeMemberValue(sessionid, vd.getName()) : removeValue(vd.getName());
					((*instance).*pfire)(instance, sessionid, vd.getName(), &old, ViewChangedType::Delete);
				}
				else if (0 == data.size())
				{
					Variant value = vd.isSubscribe() ? getMemberValue(vd.getName(), sessionid) : variableMap.find(vd.getName())->second;
					((*instance).*pfire)(instance, sessionid, vd.getName(), &value, type);
					type = ViewChangedType::Touch;
				}
				else if (field < 0)
				{
					OctetsUnmarshalStreamSource source(data);
					UnmarshalStream us(source);
					Variant value = vd.getMarshalMethod()->unmarshal(us);
					Variant old = vd.isSubscribe() ? putMemberValue(sessionid, vd.getName(), value) : putValue(vd.getName(), value);
					((*instance).*pfire)(instance, sessionid, vd.getName(), &value, Variant::Null == old ? ViewChangedType::New : ViewChangedType::Replace);
				}
				else
				{
					Variant value;
					if (vd.isSubscribe())
					{
						value = getMemberValue(vd.getName(), sessionid);
						if (Variant::Null == value)
						{
							type = ViewChangedType::New;
							value = Variant::createStruct();
							putMemberValue(sessionid, vd.getName(), value);
						}
						else
						{
							type = ViewChangedType::Replace;
						}
					}
					else
					{
						auto it = variableMap.find(vd.getName());
						if (it == variableMap.end())
						{
							type = ViewChangedType::New;
							value = Variant::createStruct();
							putValue(vd.getName(), value);
						}
						else
						{
							type = ViewChangedType::Replace;
							value = it->second;
						}
					}

					const ViewDefine::BindVarDefine* bindvar = vd.getBindVarDefine(field);
					if (nullptr == bindvar)
						return false;
					switch (bindvar->getMarshalMethod()->getDeclaration()->getType())
					{
					case VariantType::Map:
					{
						Variant n;
						{
							OctetsUnmarshalStreamSource source(data);
							UnmarshalStream us(source);
							n = bindvar->getMarshalMethod()->unmarshal(us);
						}
						Variant v = value.getVariant(bindvar->getName());
						if (Variant::Null == v)
						{
							v = n;
							value.setValue(bindvar->getName(), v);
						}
						else
						{
							const auto& map = n.getMapValue();
							for (const auto& it : map)
								v.getRawMapValue()[it.first] = it.second;
						}
											 {
												 auto keydecl = ((MapDeclaration*)bindvar->getMarshalMethod()->getDeclaration().get())->getKey();
												 auto keysetdecl = Declaration::create(VariantType::Vector, keydecl);
												 OctetsUnmarshalStreamSource source(dataremoved);
												 UnmarshalStream us(source);
												 auto rv = keysetdecl->createMarshalMethod()->unmarshal(us);
												 const auto& keyset = rv.getVectorValue();
												 auto& vmap = v.getRawMapValue();
												 for (const auto& key : keyset)
													 vmap.erase(key);
											 }
											 break;
					}
					case VariantType::Set:
					{
						Variant v = value.getVariant(bindvar->getName());
						if (Variant::Null == v)
						{
							OctetsUnmarshalStreamSource source(data);
							UnmarshalStream us(source);
							v = bindvar->getMarshalMethod()->unmarshal(us);
							value.setValue(bindvar->getName(), v);
						}
						else
						{
							auto valuedecl = ((CollectionDeclaration*)bindvar->getMarshalMethod()->getDeclaration().get())->getValue();
							auto valuesetdecl = Declaration::create(VariantType::Vector, valuedecl);
							OctetsUnmarshalStreamSource source(data);
							UnmarshalStream us(source);
							auto nv = valuesetdecl->createMarshalMethod()->unmarshal(us);
							const auto& nvs = nv.getVectorValue();
							v.getRawSetValue().insert(nvs.begin(), nvs.end());
						}
											 {
												 auto valuedecl = ((CollectionDeclaration*)bindvar->getMarshalMethod()->getDeclaration().get())->getValue();
												 auto valuesetdecl = Declaration::create(VariantType::Vector, valuedecl);
												 OctetsUnmarshalStreamSource source(dataremoved);
												 UnmarshalStream us(source);
												 auto r = valuesetdecl->createMarshalMethod()->unmarshal(us);
												 const auto& keyset = r.getVectorValue();
												 auto& vset = v.getRawSetValue();
												 for (const auto& key : keyset)
													 vset.erase(key);
											 }
											 break;
					}
					default:
					{
						OctetsUnmarshalStreamSource source(data);
						UnmarshalStream us(source);
						value.setValue(bindvar->getName(), bindvar->getMarshalMethod()->unmarshal(us));
						break;
					}
					}
				}
				return true;
			}
		};

		class GetViewInfoInterface
		{
		public:
			GetViewInfoInterface() {}
			virtual ~GetViewInfoInterface() {}
		public:
			virtual View* getView() = 0;
			virtual DynamicViewImpl* getImpl() = 0;
			virtual int32_t getInstanceIndex() = 0;
			virtual bool isTemporaryView() = 0;
			virtual VariantView* getVariantView() = 0;
		};

		class VariantViewImpl : public VariantView
		{
			class DefineImpl : public Define
			{
				mutable std::vector<std::string> varnames;
				mutable std::vector<std::string> subnames;
				mutable std::vector<std::string> ctrlnames;
				GetViewInfoInterface*	info;
			public:
				DefineImpl(GetViewInfoInterface* _info) : info(_info)
				{}
				virtual ~DefineImpl() {}
			public:
				virtual const std::string& getViewName() const override
				{
					return info->getImpl()->getViewName();
				}
				virtual bool isTemporary() const override
				{
					return info->isTemporaryView();
				}
				virtual const std::vector<std::string>& getFieldNames() const override
				{
					if (varnames.empty())
						info->getImpl()->makeVariableNames(varnames);
					return varnames;
				}
				virtual const std::vector<std::string>& getSubscribeNames() const override
				{
					if (subnames.empty())
						info->getImpl()->makeSubscribeNames(subnames);
					return subnames;
				}
				virtual const std::vector<std::string>& getControlNames() const override
				{
					if (ctrlnames.empty())
						info->getImpl()->makeControlNames(ctrlnames);
					return ctrlnames;
				}
			};
			class VariantViewChangedEventImpl : public VariantViewChangedEvent
			{
				VariantView* view;
				const ViewChangedEvent& viewevent;
			public:
				VariantViewChangedEventImpl(VariantView* _view, const ViewChangedEvent& _viewevent)
					: view(_view), viewevent(_viewevent)
				{}
				virtual ~VariantViewChangedEventImpl() {}
			public:
				virtual VariantView* getView() const override
				{
					return view;
				}
				virtual int64_t getSessionId() const override
				{
					return viewevent.getSessionId();
				}
				virtual const std::string& getFieldName() const override
				{
					return viewevent.getFieldName();
				}
				virtual Variant getValue() const override
				{
					return *((Variant*)viewevent.getValue());
				}
				virtual ViewChangedType getType() const override
				{
					return viewevent.getType();
				}
			};
		private:
			GetViewInfoInterface*	info;
			DefineImpl				define;
		public:
			VariantViewImpl(GetViewInfoInterface* _info)
				: info(_info), define(_info)
			{}
			virtual ~VariantViewImpl() {}
		public:
			virtual const std::string& getViewName() const override
			{
				return info->getImpl()->getViewName();
			}
			virtual void visitField(const std::string& fieldname, std::function<void(const Variant&)> visitor) const override
			{
				std::lock_guard<std::recursive_mutex> l(info->getView()->mutex);
				if (!info->getImpl()->visitField(fieldname, visitor))
					limax::erroroccured::fireErrorOccured(info->getView()->getViewContext()->getEndpointManager(), SOURCE_ENDPOINT, SYSTEM_VIEW_LOST_FIELD, fieldname);
			}
			virtual void sendControl(const std::string& name, Variant arg) const override
			{
				info->getImpl()->sendControl(info->getView(), name, arg, getInstanceIndex());
			}
			virtual void sendMessage(const std::string& msg) const override
			{
				info->getView()->sendMessage(msg);
			}
			virtual Runnable registerListener(VariantViewChangedListener handler) override
			{
				return info->getView()->registerListener([this, handler](const ViewChangedEvent& e){
					VariantViewChangedEventImpl vve(info->getVariantView(), e);
					handler(vve);
				});
			}
			virtual Runnable registerListener(const std::string& varname, VariantViewChangedListener handler) override
			{
				return info->getView()->registerListener(varname, [this, handler](const ViewChangedEvent& e){
					VariantViewChangedEventImpl vve(info->getVariantView(), e);
					handler(vve);
				});
			}
			virtual int32_t getInstanceIndex() const override
			{
				return info->getInstanceIndex();

			}
			virtual bool isTemporaryView() const override
			{
				return info->isTemporaryView();
			}
			virtual const Define& getDefine() const override
			{
				return define;
			}
			virtual std::string toString() const override
			{
				std::stringstream ss;
				ss << "[view = " << info->getImpl()->getViewName() << " ProviderId = " << info->getImpl()->getProviderId() << " classindex = " << info->getImpl()->getClassIndex();
				if (info->isTemporaryView())
					ss << " instanceindex = " << info->getInstanceIndex();
				ss << "]";
				return ss.str();
			}
		public:
			View* getView()
			{
				return info->getView();
			}
		};

		class DynamicView : public View, public GetViewInfoInterface
		{
			DynamicViewImpl* impl;
			VariantView* variantView;
		public:
			DynamicView(std::shared_ptr<limax::ViewContext> vc, int32_t providerId, std::shared_ptr<limax::ViewDefine> viewDefine)
				: View(vc), impl(new DynamicViewImpl(providerId, viewDefine)), variantView(new VariantViewImpl(this))
			{
			}
			virtual ~DynamicView()
			{
				delete variantView;
				delete impl;
			}
		protected:
			virtual int16_t getClassIndex() const override
			{
				return impl->getClassIndex();
			}
			virtual bool onData(View* instance, int64_t sessionid, int8_t nameIndex, int8_t fieldIndex, const limax::Octets& data, const Octets& dataremoved) override
			{
				return impl->onData(instance, sessionid, nameIndex, fieldIndex, data, dataremoved, &DynamicView::fireViewChange);
			}
		public:
			virtual const hashset<std::string>& getFieldNames() const override
			{
				return impl->getFieldNames();
			}
			virtual std::string toString() const override
			{
				return impl->getViewName();
			}

		public:
			virtual View* getView() override
			{
				return this;
			}
			virtual DynamicViewImpl* getImpl() override
			{
				return impl;
			}
			virtual int32_t getInstanceIndex() override
			{
				return 0;
			}
			virtual bool isTemporaryView() override
			{
				return false;
			}
			virtual VariantView* getVariantView() override
			{
				return variantView;
			}
		};

		class DynamicTemporaryView : public TemporaryView, public GetViewInfoInterface
		{
			DynamicViewImpl* impl;
			std::shared_ptr<TemporaryViewHandler> handler;
			VariantView*	variantView;
		public:
			DynamicTemporaryView(std::shared_ptr<limax::ViewContext> vc, int32_t providerId, std::shared_ptr<limax::ViewDefine> viewDefine, std::shared_ptr<TemporaryViewHandler> _handler)
				: TemporaryView(vc), impl(new DynamicViewImpl(providerId, viewDefine)), handler(_handler), variantView(new VariantViewImpl(this))
			{
			}
			virtual ~DynamicTemporaryView()
			{
				delete variantView;
				delete impl;
			}
		protected:
			virtual int16_t getClassIndex() const override
			{
				return impl->getClassIndex();
			}
			virtual bool onData(View* instance, int64_t sessionid, int8_t nameIndex, int8_t fieldIndex, const limax::Octets& data, const Octets& dataremoved) override
			{
				return impl->onData(instance, sessionid, nameIndex, fieldIndex, data, dataremoved, &DynamicTemporaryView::fireViewChange);
			}
			virtual void onOpen(const std::vector<int64_t>& sessionids) override
			{
				if (handler)
					handler->onOpen(variantView, sessionids);
			}
			virtual void onClose() override
			{
				if (handler)
					handler->onClose(variantView);
			}
			virtual void onAttach(int64_t sessionid) override
			{
				if (handler)
					handler->onAttach(variantView, sessionid);
			}
			virtual void onDetach(int64_t sessionid, int reason) override
			{
				if (handler)
					handler->onDetach(variantView, sessionid, reason);
			}
			virtual void detach(int64_t sessionid, int reason) override
			{
				onDetach(sessionid, reason);
				impl->removeSession(sessionid);
			}
		public:
			virtual const hashset<std::string>& getFieldNames() const override
			{
				return impl->getFieldNames();
			}
			virtual std::string toString() const override
			{
				return impl->getViewName();
			}
		public:
			virtual View* getView() override
			{
				return this;
			}
			virtual DynamicViewImpl* getImpl() override
			{
				return impl;
			}
			virtual int32_t getInstanceIndex() override
			{
				return TemporaryView::getInstanceIndex();
			}
			virtual bool isTemporaryView() override
			{
				return true;
			}
			virtual VariantView* getVariantView() override
			{
				return variantView;
			}
		};

	} // namespace helper {
} // namespace limax {
