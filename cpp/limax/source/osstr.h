namespace limax {
	namespace helper {

#ifdef LIMAX_OS_WINDOWS

		inline int stringcasecompare( const std::string& x, const std::string& y)
		{
			return _stricmp( x.c_str(), y.c_str());
		}

#endif // #ifdef LIMAX_OS_WINDOWS

#ifdef LIMAX_OS_UNIX_FAMILY

		inline int stringcasecompare( const std::string& x, const std::string& y)
		{
			return strcasecmp( x.c_str(), y.c_str());
		}

#endif // #ifdef LIMAX_OS_UNIX_FAMILY

	} // namespace helper {
} // namespace limax {
