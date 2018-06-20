#pragma once

namespace limax {

	class ViewDefine
	{
	public:
		class BindVarDefine 
		{
			std::string name;
			std::shared_ptr<MarshalMethod> method;
		public:
			BindVarDefine( const std::string& _name, std::shared_ptr<MarshalMethod> _method);
			BindVarDefine();
			~BindVarDefine();
		public:
			const std::string& getName() const;
			std::shared_ptr<MarshalMethod> getMarshalMethod() const;
		};
		class VariableDefine
		{
			int8_t	varIndex;
			bool subscribe;
			bool bind;
			std::string name;
			std::shared_ptr<MarshalMethod> method;
			hashmap<int8_t,BindVarDefine> bindVars;
		public:
			VariableDefine(int8_t _varIndex, bool _subscribe, bool _bind, const std::string& _name, std::shared_ptr<MarshalMethod> _method);
			VariableDefine();
			~VariableDefine();
		public:
			int8_t getIndex() const;
			bool isSubscribe() const;
			bool isBind() const;
			const std::string& getName() const;
			std::shared_ptr<MarshalMethod> getMarshalMethod() const;
			const BindVarDefine* getBindVarDefine( int8_t field) const;

			void addBindVarDefine( int8_t field, const BindVarDefine& var);
		};
		class ControlDefine
		{
			int8_t ctrlIndex;
			std::string name;
			std::shared_ptr<MarshalMethod> method;
		public:
			ControlDefine(int8_t _ctrlIndex, const std::string& _name, std::shared_ptr<MarshalMethod> _method);
			ControlDefine();
			~ControlDefine();
		public:
			int8_t getIndex() const;
			const std::string& getName() const;
			std::shared_ptr<MarshalMethod> getMarshalMethod() const;
		};
	private:
		std::string viewName;
		int16_t classindex;
		bool temporary;
		std::vector<VariableDefine> vars;
		std::vector<ControlDefine> ctrls;
	public:
		ViewDefine();
		ViewDefine(int16_t _classindex, const std::string& _viewName, bool _temporary);
		~ViewDefine();
	public:
		int16_t getClassIndex() const;
		const std::string& getViewName() const;
		bool isTemporary() const;
		void addVaribaleDefine(const VariableDefine& vd);
		const std::vector<VariableDefine>& getVaribaleDefine() const;
		void addControlDefine(const ControlDefine& cd);
		const std::vector<ControlDefine>& getControlDefine() const;
	};

} //namespace limax {
