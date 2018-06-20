#pragma once

#ifdef LIMAX_OS_WINDOWS

#	ifndef LIMAX_BUILD_AS_STATIC
#		ifdef LIMAX_EXPORTS
#define LIMAX_DLL_EXPORT_API __declspec( dllexport )
#		else
#define LIMAX_DLL_EXPORT_API __declspec( dllimport )
#		endif

#pragma warning(disable:4251)

#		if defined LIMAX_EXPORTS && !defined _DLL
#error The running library of the dll version is used to compile the limax of the dll version
#		endif

#	else

#define LIMAX_DLL_EXPORT_API

#	endif

#else

#define LIMAX_DLL_EXPORT_API

#endif
