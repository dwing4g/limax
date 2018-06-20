#include "common.h"
#include "oshelper.h"

#ifdef LIMAX_OS_WINDOWS
#include <conio.h>

void waitkeyhit()
{
	_getch();
}

int hasKeyHit()
{
	bool hit = !!_kbhit();
	if (!hit)
		return 0;
	do
	{
		waitkeyhit();
	} while (_kbhit());
	return 1;
}



// #ifdef LIMAX_OS_WINDOWS
#elif defined( LIMAX_OS_ANDROID) || defined( LIMAX_OS_IPHONE_SIMULATOR) || defined( LIMAX_OS_IPHONE)

void waitkeyhit() {}
int hasKeyHit()
{
	return 0;
}

// #else if defined( LIMAX_OS_UNIX_FAMILY) || defined( LIMAX_OS_IPHONE_SIMULATOR) || defined( LIMAX_OS_IPHONE)
#else

#include <termios.h>
#include <unistd.h>
#include <fcntl.h>
#include <curses.h>

int hasKeyHit()
{
	struct termios oldt, newt;
	int ch;
	int oldf;
	tcgetattr(STDIN_FILENO, &oldt);
	newt = oldt;
	newt.c_lflag &= ~(ICANON | ECHO);
	tcsetattr(STDIN_FILENO, TCSANOW, &newt);
	oldf = fcntl(STDIN_FILENO, F_GETFL, 0);
	fcntl(STDIN_FILENO, F_SETFL, oldf | O_NONBLOCK);
	ch = getchar();
	bool result = ch != EOF;
	while (ch != EOF)
		ch = getchar();
	tcsetattr(STDIN_FILENO, TCSANOW, &oldt);
	fcntl(STDIN_FILENO, F_SETFL, oldf);
	return result ? 1 : 0;
}

void waitkeyhit() {
	getch();
}

#endif // #ifdef LIMAX_OS_UNIX_FAMILY

int lua_waitkeyhit(lua_State* L) {
	waitkeyhit();
	return 0;
}

int lua_hasKeyHit(lua_State* L) {
	lua_pushboolean(L, hasKeyHit());
	return 1;
}

int lua_sleep(lua_State* L) {
	std::this_thread::sleep_for(std::chrono::milliseconds(luaL_checkinteger(L, 1)));
	return 0;
}

void table_append_oshelper(lua_State* L) {
	lua_newtable(L);

	lua_pushcfunction(L, lua_waitkeyhit);
	lua_setfield(L, -2, "waitkeyhit");

	lua_pushcfunction(L, lua_hasKeyHit);
	lua_setfield(L, -2, "haskeyhit");

	lua_pushcfunction(L, lua_sleep);
	lua_setfield(L, -2, "sleep");

	lua_setfield(L, -2, "oshelper");
}

