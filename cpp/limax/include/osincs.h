#pragma once

#include <stdint.h>
#include <string.h>
#include <stdlib.h>

#include <unordered_map>
#include <unordered_set>
#include <map>
#include <set>
#include <queue>
#include <list>
#include <vector>
#include <deque>
#include <string>
#include <memory>
#include <ostream>
#include <atomic>
#include <thread>
#include <mutex>
#include <sstream>
#include <functional>
#include <condition_variable>

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

#ifdef LIMAX_VS_DEBUG_MEMORY_LEAKS_DETECT
#define new new( _NORMAL_BLOCK, __FILE__, __LINE__)
#endif

