#pragma once

namespace limax {
	namespace variant {
		namespace viewdefineparser {

			class DataException
			{
				std::string		msg;
			public:
				inline DataException(const std::string& _msg)
					: msg(_msg)
				{}
				inline ~DataException() {}
			public:
				inline const std::string& getMessage() const
				{
					return msg;
				}
			};

			class VariantDefineParser
			{
			private:
				class DeclarationStore
				{
					hashmap<int, std::shared_ptr<Declaration> > basemap;
					hashmap<int, std::shared_ptr<Declaration> > beanmap;
				public:
					DeclarationStore();
					~DeclarationStore() {}
				public:
					void add(int type, std::shared_ptr<Declaration> dec)
					{
						beanmap.insert(std::make_pair(type, dec));
					}
				private:
					std::shared_ptr<Declaration> getBase(int type, int typeKey, int typeValue);
					std::shared_ptr<Declaration> get(int type)
					{
						if (type < limax::defines::VariantDefines::BASE_TYPE_MAX)
							return basemap[type];
						else
							return beanmap[type];
					}
				public:
					std::shared_ptr<Declaration> get(int type, int typeKey, int typeValue)
					{
						if (type < limax::defines::VariantDefines::BASE_TYPE_MAX)
							return getBase(type, typeKey, typeValue);
						else
							return beanmap[type];
					}
					bool isBaseType(int type)
					{
						return basemap.find(type) != basemap.end();
					}
				};

			private:
				const limax::defines::VariantDefines& defines;
				std::vector< std::shared_ptr<ViewDefine> >& views;
				DeclarationStore declarationStore;
			public:
				VariantDefineParser(const limax::defines::VariantDefines& _defines, std::vector< std::shared_ptr<ViewDefine> >& _views)
					: defines(_defines), views(_views)
				{
					views.reserve(defines.views.size());
				}
				~VariantDefineParser() {}
			private:
				std::string getName(const limax::defines::VariantNameIds& ids)
				{
					std::stringstream ss;
					bool had = false;
					for (auto& i : ids.ids)
					{
						if (had)
							ss << ".";
						ss << defines.namedict.at(i);
						had = true;
					}
					return ss.str();
				}
				std::shared_ptr<Declaration> parseVariableDefines(const std::vector<limax::defines::VariantVariableDefine>& vars)
				{
					StructDeclarationCreator vsd;
					for (const auto&vvd : vars)
					{
						const std::string& name = defines.namedict.at(vvd.name);
						std::shared_ptr<Declaration> decl = declarationStore.get(vvd.type, vvd.typeKey, vvd.typeValue);
						vsd.insertVariable(name, decl);
					}
					return vsd.create();
				}
				void parseBeans()
				{
					for (const auto& vbd : defines.beans)
						declarationStore.add(vbd.type, parseVariableDefines(vbd.vars));
				}
				const limax::defines::VariantBeanDefine* findVariantBeanDefine(int type)
				{
					if (declarationStore.isBaseType(type))
						return nullptr;
					for (auto& bean : defines.beans)
					if (bean.type == type)
						return &bean;
					std::stringstream ss;
					ss << "lost bean type = " << type;
					throw DataException(ss.str());
				}
				void makeBindVariables(int32_t type, ViewDefine::VariableDefine& define)
				{
					const limax::defines::VariantBeanDefine* beandefine = findVariantBeanDefine(type);
					if (nullptr != beandefine)
					{
						int8_t fieldindex = 0;
						for (const auto& var : beandefine->vars)
						{
							ViewDefine::BindVarDefine vardef(defines.namedict.at(var.name), declarationStore.get(var.type, var.typeKey, var.typeValue)->createMarshalMethod());
							define.addBindVarDefine(fieldindex++, vardef);
						}
					}
				}
				void parseView(const limax::defines::VariantViewDefine& viewdef, ViewDefine& viewDefine)
				{
					int8_t index = 0;
					for (const auto& vardef : viewdef.vars)
					{
						const std::string& varname = defines.namedict.at(vardef.name);
						std::shared_ptr<Declaration> dec = declarationStore.get(vardef.type, vardef.typeKey, vardef.typeValue);
						ViewDefine::VariableDefine define(index++, false, vardef.bind, varname, dec->createMarshalMethod());
						if (vardef.bind)
							makeBindVariables(vardef.type, define);
						viewDefine.addVaribaleDefine(define);
					}
					for (const auto& subdef : viewdef.subs)
					{
						const std::string& subname = defines.namedict.at(subdef.name);
						std::shared_ptr<Declaration> dec = declarationStore.get(subdef.type, subdef.typeKey, subdef.typeValue);
						ViewDefine::VariableDefine define(index++, true, subdef.bind, subname, dec->createMarshalMethod());
						if (subdef.bind)
							makeBindVariables(subdef.type, define);
						viewDefine.addVaribaleDefine(define);
					}
					for (const auto& control : viewdef.ctrls)
					{
						const std::string& ctrlname = defines.namedict.at(control.name);
						std::shared_ptr<Declaration> dec = parseVariableDefines(control.vars);
						viewDefine.addControlDefine(ViewDefine::ControlDefine(index++, ctrlname, dec->createMarshalMethod()));
					}
				}
				void parseViews()
				{
					for (const auto& viewdef : defines.views)
					{
						std::string viewName = getName(viewdef.name);
						ViewDefine*	define = new ViewDefine(viewdef.clsindex, viewName, viewdef.istemp);
						parseView(viewdef, *define);
						views.push_back(std::shared_ptr<ViewDefine>(define));
					}
				}
			public:
				void parse()
				{
					parseBeans();
					parseViews();
				}
			};

			inline void parseVariantDefines(const limax::defines::VariantDefines& defines, std::vector< std::shared_ptr<ViewDefine> >& views)
			{
				VariantDefineParser(defines, views).parse();
			}

		} // namespace viewdefineparser {
	} // namespace variant {
} // namespace limax {
