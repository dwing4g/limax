package limax.xmlgen.cpp;

import java.io.File;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import limax.provider.ViewLifecycle;
import limax.util.StringUtils;
import limax.xmlgen.Bind;
import limax.xmlgen.FileOperation;
import limax.xmlgen.Main;
import limax.xmlgen.NameStringToIndex;
import limax.xmlgen.Subscribe;
import limax.xmlgen.Type;
import limax.xmlgen.TypeMap;
import limax.xmlgen.TypeSet;
import limax.xmlgen.TypeVector;
import limax.xmlgen.Variable;
import limax.xmlgen.View;
import limax.xmlgen.Xbean;

public class ViewFormatter {

	private final View view;
	private final Viewgen viewgen;
	private final NameStringToIndex nameindex;
	private final int viewindex;
	private final String namespace;

	public ViewFormatter(Viewgen viewgen, View view, int index) {
		this.view = view;
		this.viewgen = viewgen;
		this.nameindex = view.getMemberNameStringToIndex();
		this.viewindex = index;
		this.namespace = viewgen.getService().getFullName() + "." + view.getFirstName();
	}

	private boolean hasVariables() {
		return view.getVariables().size() > 0 || view.getBinds().size() > 0 || view.getSubscribes().size() > 0;
	}

	public void make(File outputInc, File outputSrc) {
		final boolean isTemp = ViewLifecycle.temporary == view.getLifecycle();

		try (final PrintStream ps = FileOperation.fopen(outputInc, view.getFullName() + ".h")) {
			ps.println("#pragma once");
			ps.println();
			BeanFormatter.printCommonInclude(ps);
			final String vectIncs = isTemp ? "#include <vector>" : "";
			for (String inc : Include.includes(view, "../" + Xmlgen.beans_path_name + "/", vectIncs))
				ps.println(inc);
			ps.println();

			final String baseClassName = isTemp ? "limax::TemporaryView" : "limax::View";
			final String className = isTemp ? ("_" + view.getLastName()) : view.getLastName();
			Xmlgen.begin(namespace, ps);
			ps.println();
			if (isTemp)
				ps.println("	class " + view.getLastName() + ";");
			ps.println("	class " + className + " : public " + baseClassName);
			ps.println("	{");
			printDefine(ps);
			ps.println("	};");
			ps.println();
			Xmlgen.end(namespace, ps);
			ps.println();
		}
		if (isTemp) {
			final String name = view.getLastName();
			final String extname = Main.isObjectiveC ? ".mm" : ".cpp";
			if (!new File(outputSrc, view.getFullName() + extname).isFile()) {
				try (final PrintStream ps = FileOperation.fopen(outputSrc, view.getFullName() + extname)) {
					ps.println();
					ps.println("#include \"" + view.getFullName() + ".h\"");
					ps.println();
					Xmlgen.begin(namespace, ps);
					ps.println();
					ps.println("	void " + name + "::onOpen(const std::vector<int64_t>& sessionids) {}");
					ps.println("	void " + name + "::onAttach(int64_t sessionid) {}");
					ps.println("	void " + name + "::onDetach(int64_t sessionid, int reason)");
					ps.println("	{");
					ps.println("		if (reason >= 0)");
					ps.println("		{");
					ps.println("			//Application Reason");
					ps.println("		}");
					ps.println("		else");
					ps.println("		{");
					ps.println("			//Connection abort Reason");
					ps.println("		}");
					ps.println("	}");
					ps.println("	void " + name + "::onClose() {}");
					ps.println();
					Xmlgen.end(namespace, ps);
					ps.println();
				}
			}
			if (!new File(outputSrc, view.getFullName() + ".h").isFile()) {
				try (final PrintStream ps = FileOperation.fopen(outputSrc, view.getFullName() + ".h")) {
					ps.println();
					ps.println("#pragma once");
					ps.println();
					ps.println("#include \"../" + Xmlgen.path_xmlgen_inc + "/" + Xmlgen.views_path_name + "/"
							+ view.getFullName() + ".h\"");
					ps.println();
					Xmlgen.begin(namespace, ps);
					ps.println();
					ps.println("	class " + name + " : public _" + name);
					ps.println("	{");
					ps.println("	public:");
					ps.println("		" + name + "(std::shared_ptr<limax::ViewContext> vc)");
					ps.println("			: _" + name + "( vc)");
					ps.println("		{}");
					ps.println("		virtual ~" + name + "() {}");
					ps.println("	protected:");
					ps.println("		virtual " + name + "* _to" + name + "() override { return this; }");
					ps.println("		virtual void onOpen(const std::vector<int64_t>& sessionids) override;");
					ps.println("		virtual void onAttach(int64_t sessionid) override;");
					ps.println("		virtual void onDetach(int64_t sessionid, int reason) override;");
					ps.println("		virtual void onClose() override;");
					ps.println("	};");
					ps.println();
					Xmlgen.end(namespace, ps);
					ps.println();
				}
			}
		}
	}

	private String getBindTypeName(Bind bind, boolean istempview) {
		if (bind.isFullBind())
			return TypeName.getName(bind.getValueType());
		if (istempview)
			return "class _" + view.getName() + "::" + bind.getName();
		else
			return "class " + view.getName() + "::" + bind.getName();
	}

	private String getSubscribeTypeName(Subscribe ref) {
		if (ref.getVariable() != null)
			return TypeName.getName(ref.getVariable().getType());
		final Bind bind = ref.getBind();
		return bind.isFullBind() ? TypeName.getName(bind.getValueType())
				: "class " + viewgen.getViewFormatter(ref.getView()).namespace.replace(".", "::") + "::"
						+ ref.getView().getName() + "::" + bind.getName();
	}

	private void printViewChangeType(PrintStream ps, View view, int start) {
		if (view.getSubscribes().isEmpty()) {
			ps.println("			bool __old_has_value__ = __flags__.set(index);");
		} else {
			ps.println("			bool __old_has_value__;");
			ps.println("			if (index < " + start + ")");
			ps.println("				__old_has_value__ = __flags__.set(index);");
			ps.println("			else");
			ps.println("				__old_has_value__ = __subsFlags__.set(sessionid, index - " + start + ");");
		}
		ps.println(
				"			limax::ViewChangedType __t__ = __old_has_value__ ? limax::ViewChangedType::Replace : limax::ViewChangedType::New;");
	}

	private void printXbeanBindUpdate(PrintStream ps, Bind bind) {
		AtomicInteger i = new AtomicInteger();
		ps.println("					switch(field)");
		ps.println("					{");
		(bind.isFullBind() ? ((Xbean) bind.getValueType()).getVariables() : bind.getVariables()).forEach(var -> {
			ps.println("					case " + i.getAndIncrement() + ":");
			ps.println("						{");
			Type type = var.getType();
			String field = "__s__." + var.getName();
			if (type instanceof TypeMap) {
				ps.println("							{");
				ps.println("								" + TypeName.getName(type) + " __newvalue__;");
				type.accept(new Unmarshal("__newvalue__", ps, "								"));
				ps.println("								" + field
						+ ".insert(__newvalue__.begin(), __newvalue__.end());");
				ps.println("							}");
				ps.println("							{");
				ps.println(
						"								limax::OctetsUnmarshalStreamSource __source__(_dataremoved_);");
				ps.println("								limax::UnmarshalStream _os_(__source__);");
				TypeVector removestype = ((TypeMap) type).getKeySetType().asTypeVector();
				ps.println("								" + TypeName.getName(removestype) + " __removes__;");
				removestype.accept(new Unmarshal("__removes__", ps, "								"));
				ps.println(
						"								for(auto it = __removes__.begin(), ite = __removes__.end(); it != ite; ++ it)");
				ps.println("									" + field + ".erase(*it);");
				ps.println("							}");
			} else if (type instanceof TypeSet) {
				TypeVector vectortype = ((TypeSet) type).asTypeVector();
				ps.println("							{");
				ps.println("								" + TypeName.getName(vectortype) + " __newvalue__;");
				vectortype.accept(new Unmarshal("__newvalue__", ps, "								"));
				ps.println("								" + field
						+ ".insert(__newvalue__.begin(), __newvalue__.end());");
				ps.println("							}");
				ps.println("							{");
				ps.println(
						"								limax::OctetsUnmarshalStreamSource __source__(_dataremoved_);");
				ps.println("								limax::UnmarshalStream _os_(__source__);");
				ps.println("								" + TypeName.getName(vectortype) + " __removes__;");
				vectortype.accept(new Unmarshal("__removes__", ps, "								"));
				ps.println(
						"								for(auto it = __removes__.begin(), ite = __removes__.end(); it != ite; ++ it)");
				ps.println("									" + field + ".erase(*it);");
				ps.println("							}");
			} else {
				type.accept(new Unmarshal(field, ps, "							"));
			}
			ps.println("							break;");
			ps.println("						}");
		});
		ps.println("					default:");
		ps.println("						return false;");
		ps.println("					}");
	}

	public void printDefine(PrintStream ps) {
		ps.println("		static int& __getpvid__()");
		ps.println("		{");
		ps.println("			static int __pvid__;");
		ps.println("			return __pvid__;");
		ps.println("		}");
		ps.println();
		final boolean isTemp = ViewLifecycle.temporary == view.getLifecycle();
		int start = view.getVariables().size() + view.getBinds().size();
		if (hasVariables()) {
			ps.println("		limax::BitSet __flags__;");
			if (!view.getSubscribes().isEmpty())
				ps.println("		limax::MapBitSet<int64_t> __subsFlags__;");
			ps.println("		limax::ViewChangedType __type__ = limax::ViewChangedType::Touch;");
		}
		if (!view.getBinds().isEmpty())
			ps.println("	public:");
		view.getBinds().forEach(bind -> new BindFormatter(bind).make(ps));
		if (!view.getEnums().isEmpty())
			ps.println("	public:");
		BeanFormatter.declareEnums(ps, view.getEnums());
		if (hasVariables()) {
			ps.println("	private:");
			BeanFormatter.declareVariables(ps, view.getVariables(), "		");
			view.getBinds().forEach(
					bind -> ps.println("		" + getBindTypeName(bind, isTemp) + " " + bind.getName() + ";"));
			view.getSubscribes().forEach(subs -> ps.println(
					"		limax::hashmap<int64_t, " + getSubscribeTypeName(subs) + "> " + subs.getName() + ";"));
			ps.println("	public:");
			view.getVariables().forEach(var -> {
				ps.println("		void visit" + StringUtils.upper1(var.getName()) + "(std::function<void("
						+ TypeReferenceName.getName(var.getType())
						+ ")> v) { std::lock_guard<std::recursive_mutex> l(mutex); if(__flags__.get("
						+ nameindex.getIndex(var) + ")) v(" + var.getName() + "); }");
			});
			view.getBinds().forEach(bind -> {
				ps.println("		void visit" + StringUtils.upper1(bind.getName()) + "(std::function<void(const "
						+ getBindTypeName(bind, isTemp)
						+ "&)> v) { std::lock_guard<std::recursive_mutex> l(mutex); if(__flags__.get("
						+ nameindex.getIndex(bind) + ")) v(" + bind.getName() + "); }");
			});
			view.getSubscribes().forEach(subs -> {
				ps.println("		void visit" + StringUtils.upper1(subs.getName())
						+ "(std::function<void(const limax::hashmap<int64_t, " + getSubscribeTypeName(subs)
						+ ">&)> v) { std::lock_guard<std::recursive_mutex> l(mutex); " + "v(" + subs.getName()
						+ "); }");
			});
		}
		ps.println("	public:");
		Construct.make(view, ps, "		", isTemp);
		ps.println("	protected:");
		ps.println("		virtual int16_t getClassIndex() const override");
		ps.println("		{");
		ps.println("			return " + viewindex + ";");
		ps.println("		}");
		ps.println();
		ps.println("	private:");
		ps.println("		static const limax::hashset<std::string> makeFieldNames()");
		ps.println("		{");
		ps.println("			limax::hashset<std::string> __names__;");
		view.getVariables().forEach(var -> ps.println("			__names__.insert(\"" + var.getName() + "\");"));
		view.getBinds().forEach(bind -> ps.println("			__names__.insert(\"" + bind.getName() + "\");"));
		view.getSubscribes().forEach(subs -> ps.println("			__names__.insert(\"" + subs.getName() + "\");"));
		ps.println("			return __names__;");
		ps.println("		}");
		ps.println("	public:");
		ps.println("		virtual const limax::hashset<std::string>& getFieldNames() const override");
		ps.println("		{");
		ps.println("			static limax::hashset<std::string> __names__ = makeFieldNames();");
		ps.println("			return __names__;");
		ps.println("		}");
		ps.println();
		ps.println("		virtual std::string getClassName() const override");
		ps.println("		{");
		ps.println("			return \"" + viewgen.getService().getFullName() + "." + view.getFullName() + "\";");
		ps.println("		}");
		ps.println();
		// override onData
		ps.println("	protected:");
		ps.println(
				"		virtual bool onData(View* instance, int64_t sessionid, int8_t nameIndex, int8_t fieldIndex, const limax::Octets& _data_, const limax::Octets& _dataremoved_) override");
		ps.println("		{");
		if (hasVariables()) {
			ps.println("			if(nameIndex < 0)");
			ps.println("				return onRemoved(instance, sessionid, (int8_t)(nameIndex & 0x7f));");
			ps.println("			else if(0 == _data_.size())");
			ps.println("				return onChanged(instance, sessionid, nameIndex);");
			ps.println("			limax::OctetsUnmarshalStreamSource __source__(_data_);");
			ps.println("			limax::UnmarshalStream __us__(__source__);");
			ps.println("			if(fieldIndex < 0)");
			ps.println("				return onChanged(instance, sessionid, nameIndex, __us__);");
			ps.println("			else");
			ps.println(
					"				return onChanged(instance, sessionid, nameIndex, fieldIndex, __us__, _dataremoved_);");
		} else {
			ps.println("			return false;");
		}
		ps.println("		}");
		ps.println();

		if (hasVariables()) {
			ps.println("	private:");
			ps.println("		bool onRemoved(View* instance, int64_t sessionid, int8_t index)");
			ps.println("		{");
			if (view.getSubscribes().isEmpty()) {
				ps.println("			__flags__.clear(index);");
			} else {
				ps.println("			if (index < " + start + ")");
				ps.println("				__flags__.clear(index);");
				ps.println("			else");
				ps.println("				__subsFlags__.clear(sessionid, index - " + start + ");");
			}
			ps.println("			return onChanged(instance, sessionid, index, limax::ViewChangedType::Delete);");
			ps.println("		}");
			ps.println();
			ps.println(
					"		bool onChanged(View* instance, int64_t sessionid, int8_t index, limax::ViewChangedType type)");
			ps.println("		{");
			ps.println("			switch(index)");
			ps.println("			{");

			Stream.concat(view.getVariables().stream(), view.getBinds().stream()).forEach(var -> {
				final int index = nameindex.getIndex(var);
				ps.println("			case " + index + ":");
				ps.println("				{");
				ps.println("					const void* __oldvalue__ = &this->" + var.getName() + ";");
				ps.println("					fireViewChange(instance, sessionid, \"" + var.getName()
						+ "\", __oldvalue__, type);");
				ps.println("					return true;");
				ps.println("				}");
			});
			view.getSubscribes().forEach(subs -> {
				ps.println("			case " + nameindex.getIndex(subs) + ":");
				ps.println("				{");
				ps.println("					const void* __oldvalue__ = nullptr;");
				ps.println("					auto it = this->" + subs.getName() + ".find(sessionid);");
				ps.println("					if(it != this->" + subs.getName() + ".end())");
				ps.println("						__oldvalue__ = &it->second;");
				ps.println("					fireViewChange(instance, sessionid, \"" + subs.getName()
						+ "\", __oldvalue__, type);");
				ps.println("					if(it != this->" + subs.getName() + ".end())");
				ps.println("						this->" + subs.getName() + ".erase(it);");
				ps.println("					return true;");
				ps.println("				}");
			});
			ps.println("			default:");
			ps.println("				return false;");
			ps.println("			}");
			ps.println("			return true;");
			ps.println("		}");
			ps.println();

			ps.println("		bool onChanged(View* instance, int64_t sessionid, int8_t index)");
			ps.println("		{");
			ps.println("			bool result = onChanged(instance, sessionid, index, __type__);");
			ps.println("			__type__ = limax::ViewChangedType::Touch;");
			ps.println("			return result;");
			ps.println("		}");
			ps.println();

			ps.println(
					"		bool onChanged(View* instance, int64_t sessionid, int8_t index, const limax::UnmarshalStream& _os_)");
			ps.println("		{");
			if (view.getSubscribes().isEmpty()) {
				ps.println("			bool __old_has_value__ = __flags__.set(index);");
			} else {
				ps.println("			bool __old_has_value__;");
				ps.println("			if (index < " + start + ")");
				ps.println("				__old_has_value__ = __flags__.set(index);");
				ps.println("			else");
				ps.println("				__old_has_value__ = __subsFlags__.set(sessionid, index - " + start + ");");
			}
			ps.println(
					"			limax::ViewChangedType __t__ = __old_has_value__ ? limax::ViewChangedType::Replace : limax::ViewChangedType::New;");
			ps.println("			switch(index)");
			ps.println("			{");
			view.getVariables().forEach(var -> {
				final int index = nameindex.getIndex(var);
				ps.println("			case " + index + ":");
				ps.println("				{");
				var.getType().accept(new Unmarshal("this->" + var.getName(), ps, "					"));
				ps.println("					fireViewChange(instance, sessionid, \"" + var.getName() + "\", &this->"
						+ var.getName() + ", __t__);");
				ps.println("					return true;");
				ps.println("				}");
			});
			view.getBinds().forEach(bind -> {
				final int index = nameindex.getIndex(bind);
				ps.println("			case " + index + ":");
				ps.println("				{");
				if (bind.isFullBind())
					bind.getValueType().accept(new Unmarshal("this->" + bind.getName(), ps, "					"));
				else
					ps.println("					_os_ >> this->" + bind.getName() + ";");
				ps.println("					fireViewChange(instance, sessionid, \"" + bind.getName() + "\", &this->"
						+ bind.getName() + ", __t__);");
				ps.println("					return true;");
				ps.println("				}");
			});
			view.getSubscribes().forEach(subs -> {
				ps.println("			case " + nameindex.getIndex(subs) + ":");

				ps.println("				{");
				ps.println("					" + getSubscribeTypeName(subs) + " __v__;");
				Variable var = subs.getVariable();
				if (var != null) {
					var.getType().accept(new Unmarshal("__v__", ps, "					"));
				} else {
					Bind bind = subs.getBind();
					if (bind.isFullBind()) {
						bind.getValueType().accept(new Unmarshal("__v__", ps, "					"));
					} else {
						ps.println("					_os_ >> __v__;");
					}
				}
				ps.println("					" + subs.getName() + ".insert(std::make_pair(sessionid, __v__));");
				ps.println("					fireViewChange(instance, sessionid, \"" + subs.getName()
						+ "\", &__v__, __t__);");
				ps.println("					return true;");
				ps.println("				}");
			});

			ps.println("			default:");
			ps.println("				return false;");
			ps.println("			}");
			ps.println("		}");
			ps.println();

			ps.println(
					"		bool onChanged(View* instance, int64_t sessionid, int8_t index, int field, const limax::UnmarshalStream& _os_, const limax::Octets& _dataremoved_)");
			ps.println("		{");
			if (Stream
					.concat(view.getBinds().stream().filter(bind -> bind.getValueType() instanceof Xbean)
							.map(bind -> (Xbean) bind.getValueType()),
							view.getSubscribes().stream().filter(subs -> subs.getBind() != null)
									.filter(subs -> subs.getBind().getValueType() instanceof Xbean)
									.map(subs -> (Xbean) subs.getBind().getValueType()))
					.findAny().isPresent()) {

				printViewChangeType(ps, view, start);
				ps.println("			switch(index)");
				ps.println("			{");
				view.getBinds().stream().filter(bind -> bind.getValueType() instanceof Xbean).forEach(bind -> {
					ps.println("			case " + nameindex.getIndex(bind) + ":");
					ps.println("				{");
					ps.println("					" + getBindTypeName(bind, isTemp) + "& __s__ = this->"
							+ bind.getName() + ";");
					printXbeanBindUpdate(ps, bind);
					ps.println("					__type__ = __t__;");
					ps.println("					break;");
					ps.println("				}");
				});
				view.getSubscribes().stream().filter(subs -> subs.getBind() != null)
						.filter(subs -> subs.getBind().getValueType() instanceof Xbean).forEach(subs -> {
							ps.println("		case " + nameindex.getIndex(subs) + ":");
							ps.println("		{");
							final String field = "this->" + subs.getName();
							ps.println("			auto it = " + field + ".find(sessionid);");
							ps.println("			if(it == " + field + ".end())");
							ps.println("			{");
							ps.println("				" + getSubscribeTypeName(subs) + " __v__;");
							ps.println("				" + field + ".insert(std::make_pair(sessionid, __v__));");
							ps.println("				it = " + field + ".find(sessionid);");
							ps.println("			}");
							ps.println("			" + getSubscribeTypeName(subs) + "& __s__ = it->second;");
							printXbeanBindUpdate(ps, subs.getBind());
							ps.println("			__type__ = __t__;");
							ps.println("			break;");
							ps.println("		}");
						});
				ps.println("			default:");
				ps.println("				return false;");
				ps.println("			}");
			}
			ps.println("			return true;");
			ps.println("		}");
			ps.println();
		}
		if (isTemp) {
			ps.println("	private:");
			ps.println("		virtual void detach(int64_t sessionid, int reason) override");
			ps.println("		{");
			ps.println("			onDetach(sessionid, reason);");
			for (final Subscribe subs : view.getSubscribes()) {
				ps.println("			" + subs.getName() + ".erase(sessionid);");
			}
			ps.println("		}");
			ps.println("	protected:");
			ps.println("		virtual " + view.getName() + "* _to" + view.getName() + "() = 0;");
			// getInstance
			ps.println("	public:");
			ps.println("		static " + view.getName() + "* getInstance(int instance)");
			ps.println("		{");
			ps.println("			if(auto manager = limax::Endpoint::getDefaultEndpointManager())");
			ps.println("				return getInstance(manager, instance);");
			ps.println("			return nullptr;");
			ps.println("		}");
			ps.println();
			ps.println(
					"		static " + view.getName() + "* getInstance(limax::EndpointManager* manager, int instance)");
			ps.println("		{");
			ps.println("			return getInstance(manager, __getpvid__(), instance);");
			ps.println("		}");
			ps.println();
			ps.println("		static " + view.getName()
					+ "* getInstance(limax::EndpointManager* manager, int pvid, int instance)");
			ps.println("		{");
			ps.println("			if(auto vc = manager->getViewContext(pvid, limax::ViewContext::Type::Static))");
			ps.println("				return (dynamic_cast<_" + view.getName() + "*>(vc->findTemporaryView("
					+ viewindex + ", instance)))->_to" + view.getName() + "();");
			ps.println("			return nullptr;");
			ps.println("		}");
			ps.println();
		} else {
			// getInstance
			ps.println("	public:");
			ps.println("		static " + view.getName() + "* getInstance()");
			ps.println("		{");
			ps.println("			if(auto manager = limax::Endpoint::getDefaultEndpointManager())");
			ps.println("				return getInstance(manager);");
			ps.println("			return nullptr;");
			ps.println("		}");
			ps.println();
			ps.println("		static " + view.getName() + "* getInstance(limax::EndpointManager* manager)");
			ps.println("		{");
			ps.println("			return getInstance(manager, __getpvid__());");
			ps.println("		}");
			ps.println();
			ps.println("		static " + view.getName() + "* getInstance(limax::EndpointManager* manager, int pvid)");
			ps.println("		{");
			ps.println("			if(auto vc = manager->getViewContext(pvid, limax::ViewContext::Type::Static))");
			ps.println("				return dynamic_cast<" + view.getName() + "*>(vc->getSessionOrGlobalView("
					+ viewindex + "));");
			ps.println("			return nullptr;");
			ps.println("		}");
			ps.println();
		}

		if (!view.getControls().isEmpty()) {
			ps.println("	private:");
			view.getControls()
					.forEach(control -> new ControlFormatter(control, nameindex.getIndex(control)).makeClass(ps));
			ps.println("	public:");
			view.getControls()
					.forEach(control -> new ControlFormatter(control, nameindex.getIndex(control)).makeMethod(ps));
		}
	}
}
