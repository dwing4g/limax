// chatroom.cpp : Defines the entry point for the application.
//

#include "stdafx.h"
#include "chatroom.h"
#include "xmlgeninc/_xmlgen_.hpp"

#include <sstream>

enum
{
	WM_USER_UI_THREAD_SCHEDUL = WM_USER + 0x0001,
};

HINSTANCE g_hInstance = nullptr;
HWND g_mainHwnd = nullptr;
HWND g_chatroomHwnd = nullptr;
HBITMAP g_imageicon = nullptr;

INT_PTR CALLBACK MainDialogProc(HWND hwndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam);
INT_PTR CALLBACK LoginDialogProc(HWND hwndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam);
INT_PTR CALLBACK SetNicknameDialogProc(HWND hwndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam);
INT_PTR CALLBACK ChatRoomDialogProc(HWND hwndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam);

int APIENTRY _tWinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance, LPTSTR lpCmdLine, int nCmdShow)
{
	UNREFERENCED_PARAMETER(hPrevInstance);
	UNREFERENCED_PARAMETER(lpCmdLine);

	limax::Endpoint::openEngine();
	g_hInstance = hInstance;
	g_imageicon = (HBITMAP)LoadImage(hInstance, MAKEINTRESOURCE(IDB_BITMAP_ICON), IMAGE_BITMAP, 0, 0, LR_CREATEDIBSECTION);
	g_mainHwnd = CreateDialogParam(hInstance, MAKEINTRESOURCE(IDD_DIALOG_MAIN), nullptr, MainDialogProc, 0);

	{
		RECT rtdesktop, rtdialog;
		GetWindowRect(g_mainHwnd, &rtdialog);
		GetClientRect(GetDesktopWindow(), &rtdesktop);
		int w = rtdialog.right - rtdialog.left;
		int h = rtdialog.bottom - rtdialog.top;
		int x = ((rtdesktop.right - rtdesktop.left) - w) / 2;
		int y = ((rtdesktop.bottom - rtdesktop.top) - h) / 2;
		MoveWindow(g_mainHwnd, x, y, w, h, FALSE);
	}
	UpdateWindow(g_mainHwnd);
	ShowWindow(g_mainHwnd, SW_SHOW);

	MSG msg;
	while (true)
	{
		if (PeekMessage(&msg, nullptr, 0, 0, PM_REMOVE))
		{
			if (WM_QUIT == msg.message)
				break;
			if (!IsDialogMessage(g_mainHwnd, &msg))
			{
				TranslateMessage(&msg);
				DispatchMessage(&msg);
			}
		}
		else
		{
			limax::uiThreadSchedule();
			Sleep(1);
		}
	}
	bool done = false;
	limax::Endpoint::closeEngine([&done](){ done = true; });
	while (!done)
	{
		limax::uiThreadSchedule();
		Sleep(1);
	}
	return (int)msg.wParam;
}

template<class Type> inline const Type& getTypedValue(const limax::ViewChangedEvent& e)
{
	return *(Type*)e.getValue();
}

void showModalDialog(std::function<void(void)> f)
{
	volatile bool runing = true;
	std::thread t([&runing]()
	{
		while (runing)
		{
			PostMessage(g_mainHwnd, WM_USER_UI_THREAD_SCHEDUL, 0, 0);
			Sleep(1);
		}
	});
	f();
	runing = false;
	t.join();
}

void showLoginDialog()
{
	DialogBox(g_hInstance, MAKEINTRESOURCE(IDD_DIALOG_LOGIN), g_mainHwnd, LoginDialogProc);
}

void showRenameDialog()
{
	showModalDialog([]()
	{
		DialogBox(g_hInstance, MAKEINTRESOURCE(IDD_DIALOG_SET_NICKNAME), g_mainHwnd, SetNicknameDialogProc);
	});
}

void showChatRoomDialog()
{
	if (nullptr == g_chatroomHwnd)
	{
		g_chatroomHwnd = CreateDialog(g_hInstance, MAKEINTRESOURCE(IDD_DIALOG_CHATROOM), g_mainHwnd, ChatRoomDialogProc);
		UpdateWindow(g_chatroomHwnd);
		ShowWindow(g_chatroomHwnd, SW_SHOW);
	}
	else
	{
		BringWindowToTop(g_chatroomHwnd);
	}
}

#ifdef LIMAX_PLAT_32
class Int64idhelper
{
	limax::hashmap<int32_t, int64_t> m_idcache;
	int32_t m_idgenerator = 1;
public:
	inline Int64idhelper() {}
	inline ~Int64idhelper() {}
public:
	inline LPARAM set(int64_t id64)
	{
		auto id32 = m_idgenerator++;
		m_idcache.insert(std::make_pair(id32, id64));
		return (LPARAM)id32;
	}
	inline int64_t get(LPARAM id32)
	{
		return m_idcache[(int32_t)id32];
	}
	inline void clear()
	{
		m_idcache.clear();
		m_idgenerator = 1;
	}
};
#else
struct Int64idhelper
{
	inline LPARAM set( int64_t id64)
	{
		return (LPARAM)id64;
	}
	inline int64_t get(LPARAM id)
	{
		return (int64_t)id;
	}
	inline void clear(){}
};
#endif

class Lisener : public limax::EndpointListener
{
	limax::EndpointManager* m_manager = nullptr;

	chat::chatclient::chatviews::ChatRoom* chatroom = nullptr;

	Int64idhelper roomtreeidheper;
	Int64idhelper memlistidheper;
public:
	Lisener() {}
	virtual ~Lisener() {}
public:
	virtual void onManagerInitialized(limax::EndpointManager* mng, limax::EndpointConfig*) override
	{
		m_manager = mng;
	}
	virtual void onTransportAdded(limax::Transport*) override
	{
		EnableWindow(GetDlgItem(g_mainHwnd, IDC_BUTTON_LOGOUT), TRUE);
		EnableWindow(GetDlgItem(g_mainHwnd, IDC_BUTTON_RENAME), TRUE);

		chat::chatclient::chatviews::CommonInfo::getInstance()->registerListener([this](const limax::ViewChangedEvent& e)
		{
			const auto& halls = *(std::vector<chat::chatviews::RoomChatHallInfo>*)e.getValue();
			auto htree = ::GetDlgItem(g_mainHwnd, IDC_TREE_ROOMS);
			TreeView_DeleteAllItems(htree);
			roomtreeidheper.clear();
			for (auto& hall : halls)
			{
				TVINSERTSTRUCTA tvinshall;
				tvinshall.hParent = TVI_ROOT;
				tvinshall.hInsertAfter = TVI_LAST;
				tvinshall.item.mask = TVIF_TEXT | TVIF_PARAM;
				tvinshall.item.pszText = (LPSTR)hall.name.c_str();
				tvinshall.item.cchTextMax = hall.name.size();
				tvinshall.item.lParam = 0;

				auto hHall = (HTREEITEM)SendMessageA(htree, TVM_INSERTITEMA, 0, (LPARAM)&tvinshall);
				for (auto& room : hall.rooms)
				{
					TVINSERTSTRUCTA tvinsroom;
					tvinsroom.hParent = hHall;
					tvinsroom.hInsertAfter = TVI_LAST;
					tvinsroom.item.mask = TVIF_TEXT | TVIF_PARAM;
					tvinsroom.item.pszText = (LPSTR)room.name.c_str();
					tvinsroom.item.cchTextMax = room.name.size();
					tvinsroom.item.lParam = roomtreeidheper.set(room.roomid);
					SendMessageA(htree, TVM_INSERTITEMA, 0, (LPARAM)&tvinsroom);
				}
				TreeView_Expand(htree, (LPARAM)hHall, TVE_EXPAND);
			}
		});

		auto userinfo = chat::chatclient::chatviews::UserInfo::getInstance();
		userinfo->registerListener("name", [](const limax::ViewChangedEvent& e)
		{
			const auto& name = getTypedValue<class chat::chatclient::chatviews::UserInfo::name>(e).nickname;
			if (name.empty())
				showRenameDialog();
			else
			{
				std::ostringstream ss;
				ss << "chatroom [" << name << "]";
				SetWindowTextA(g_mainHwnd, ss.str().c_str());
			}
		});
		userinfo->registerListener("lasterror", [](const limax::ViewChangedEvent& e)
		{
			int code = getTypedValue<int>(e);
			std::string codemsg;
			switch (code)
			{
			case chat::chatviews::ErrorCodes::EC_NAME_EXISTING:
				codemsg = "name already existing";
				break;
			case chat::chatviews::ErrorCodes::EC_NAME_UNMODIFIED:
				codemsg = "name unmodify";
				break;
			case chat::chatviews::ErrorCodes::EC_BAD_ROOM_ID:
				codemsg = "bad room id";
				break;
			default:
				codemsg = "[unknow code value]";
				break;
			}
			showModalDialog([&]()
			{
				MessageBoxA(g_mainHwnd, codemsg.c_str(), "chatroom", MB_OK);
			});
		});
		userinfo->registerListener("recvedmessage", [this](const limax::ViewChangedEvent& e)
		{
			const auto& msg = getTypedValue<chat::chatviews::ChatMessage>(e);
			if (m_manager->getSessionId() == msg.user)
				showChatMessage(msg.msg);
			else
				showPrivateMessage(msg.user, msg.msg, true);
		});
		userinfo->registerListener("sendedmessage", [this](const limax::ViewChangedEvent& e)
		{
			const auto& msg = getTypedValue<chat::chatviews::ChatMessage>(e);
			showPrivateMessage(msg.user, msg.msg, false);
		});
	}
	virtual void onTransportRemoved(limax::Transport*) override
	{
		auto htree = ::GetDlgItem(g_mainHwnd, IDC_TREE_ROOMS);
		TreeView_DeleteAllItems(htree);
		roomtreeidheper.clear();

		EnableWindow(GetDlgItem(g_mainHwnd, IDC_BUTTON_LOGIN), TRUE);
		EnableWindow(GetDlgItem(g_mainHwnd, IDC_BUTTON_LOGOUT), FALSE);
		EnableWindow(GetDlgItem(g_mainHwnd, IDC_BUTTON_RENAME), FALSE);
		EnableWindow(GetDlgItem(g_mainHwnd, IDC_BUTTON_JOIN), FALSE);
	}
	virtual void onAbort(limax::Transport*) override
	{
		MessageBoxA(g_mainHwnd, "connect abort!", "chatroom", MB_OK);
		EnableWindow(GetDlgItem(g_mainHwnd, IDC_BUTTON_LOGIN), TRUE);
	}
	virtual void onManagerUninitialized(limax::EndpointManager*) override
	{
		m_manager = nullptr;
	}
	virtual void onSocketConnected() override {}
	virtual void onKeyExchangeDone() override {}
	virtual void onKeepAlived(int ping) override {}
	virtual void onErrorOccured(int errorsource, int errorvalue, const std::string& info) override
	{}
	virtual void destroy() override
	{}
public:
	void logout()
	{
		if (nullptr != m_manager)
			m_manager->close();
	}
	void onChatRoomOpen(chat::chatclient::chatviews::ChatRoom* view)
	{
		chatroom = view;
		showChatRoomDialog();

		chat::chatclient::chatviews::UserInfo::getInstance()->visitName([](const class chat::chatclient::chatviews::UserInfo::name& name)
		{
			SetWindowTextA(g_chatroomHwnd, name.nickname.c_str());
		});

		{
			HWND hlist = GetDlgItem(g_chatroomHwnd, IDC_LIST_MEMBERS);
			int index = SendMessageA(hlist, LB_ADDSTRING, 0, (LPARAM)"[all]");
			SendMessageA(hlist, LB_SETITEMDATA, index, memlistidheper.set(-1L));
		}

		view->visitNames([this](const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>& names)
		{
			limax::hashset<int64_t> addedset;
			HWND hlist = GetDlgItem(g_chatroomHwnd, IDC_LIST_MEMBERS);
			addedset.insert(-1L);
			addedset.insert(m_manager->getSessionId());
			{
				int count = SendMessageA(hlist, LB_GETCOUNT, 0, 0);
				for (int i = count - 1; i >= 0; i--)
				{
					auto id = memlistidheper.get(SendMessageA(hlist, LB_GETITEMDATA, i, 0));
					if (-1L == id)
						continue;
					if (names.find(id) == names.end())
						SendMessageA(hlist, LB_DELETESTRING, i, 0);
					else
						addedset.insert(id);
				}
			}
			for (const auto& it : names)
			{
				auto id = it.first;
				if (addedset.find(id) != addedset.end())
					continue;
				int index = SendMessageA(hlist, LB_ADDSTRING, 0, (LPARAM)it.second.nickname.c_str());
				SendMessageA(hlist, LB_SETITEMDATA, index, memlistidheper.set(id));
			}
		});

		view->registerListener("names", [this](const limax::ViewChangedEvent& e)
		{
			if (m_manager->getSessionId() == e.getSessionId())
				return;
			std::string oldname;
			HWND hlist = GetDlgItem(g_chatroomHwnd, IDC_LIST_MEMBERS);
			int count = SendMessageA(hlist, LB_GETCOUNT, 0, 0);
			for (int i = 0; i < count; i++)
			{
				auto id = memlistidheper.get(SendMessageA(hlist, LB_GETITEMDATA, i, 0));
				if (e.getSessionId() == id)
				{
					size_t length = SendMessageA(hlist, LB_GETTEXTLEN, i, 0);
					oldname.resize(length + 1);
					length = SendMessageA(hlist, LB_GETTEXT, i, (LPARAM)&oldname[0]);
					oldname.resize(length);
					SendMessageA(hlist, LB_DELETESTRING, i, 0);
					break;
				}
			}
			const auto& name = getTypedValue<class chat::chatclient::chatviews::UserInfo::name>(e).nickname;
			int index = SendMessageA(hlist, LB_ADDSTRING, 0, (LPARAM)name.c_str());
			SendMessageA(hlist, LB_SETITEMDATA, index, memlistidheper.set(e.getSessionId()));
			if (!oldname.empty())
			{
				std::ostringstream ss;
				ss << "[user '" << oldname << "' change name to '" << name << "']";
				showChatMessage(ss.str());
			}
		});
		view->registerListener("lastmessage", [this](const limax::ViewChangedEvent& e)
		{
			if (limax::ViewChangedType::Replace == e.getType() || limax::ViewChangedType::Touch == e.getType())
			{
				auto msg = getTypedValue<chat::chatviews::ChatMessage>(e);
				showMessageToAll(msg.user, msg.msg);
			}
		});
	}
	void onChatRoomAttach(chat::chatclient::chatviews::ChatRoom* view, int64_t sessionid)
	{
		std::string name;
		chatroom->visitNames([sessionid, &name](const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>& names)
		{
			auto it = names.find(sessionid);
			if (it != names.end())
				name = it->second.nickname;
		});
		if (name.empty())
		{
			limax::runOnUiThread([this, view, sessionid](){ onChatRoomAttach(view, sessionid); });
			return;
		}
		std::ostringstream ss;
		ss << "[user \"" << name << "\" enter room]";
		showChatMessage(ss.str());
	}
	void onChatRoomDetach(chat::chatclient::chatviews::ChatRoom* view, int64_t sessionid, int reason)
	{
		std::string name;
		chatroom->visitNames([sessionid, &name](const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>& names)
		{
			auto it = names.find(sessionid);
			if (it != names.end())
				name = it->second.nickname;
		});
		auto msg = reason > 0 ? "leave room" : "disconnected";
		std::ostringstream ss;
		ss << "[user \"" << name << "\" " << msg;
		showChatMessage(ss.str());

		HWND hlist = GetDlgItem(g_chatroomHwnd, IDC_LIST_MEMBERS);
		int count = SendMessageA(hlist, LB_GETCOUNT, 0, 0);
		for (int i = 0; i < count; i++)
		{
			auto id = memlistidheper.get(SendMessageA(hlist, LB_GETITEMDATA, i, 0));
			if (sessionid == id)
			{
				SendMessageA(hlist, LB_DELETESTRING, i, 0);
				break;
			}
		}

	}
	void onChatRoomClose(chat::chatclient::chatviews::ChatRoom* view)
	{
		chatroom = nullptr;
		DestroyWindow(g_chatroomHwnd);
		g_chatroomHwnd = nullptr;
		memlistidheper.clear();
	}
	bool hashChatRoom() const
	{
		return nullptr != chatroom;
	}
	chat::chatclient::chatviews::ChatRoom* getChatRoom()
	{
		return chatroom;
	}
	int64_t getRoomIdByParam(LPARAM p)
	{
		return roomtreeidheper.get(p);
	}
	int64_t getSessionIdByParam(LPARAM p)
	{
		return memlistidheper.get(p);
	}
private:
	void showChatMessage(const std::string& msg)
	{
		HWND hMsgs = GetDlgItem(g_chatroomHwnd, IDC_EDIT_MESSAGES);
		size_t msize = GetWindowTextLengthA(hMsgs);

		std::string oldmesage;
		if (msize)
		{
			oldmesage.resize(msize + 1);
			msize = GetWindowTextA(hMsgs, &oldmesage[0], msize + 1);
			oldmesage.resize(msize);
		}

		std::ostringstream ss;
		ss << oldmesage << msg << "\r\n";
		auto allmsg = ss.str();
		OutputDebugStringA(allmsg.c_str());
		SetWindowTextA(hMsgs, allmsg.c_str());

		int nLines = ::SendMessageA(hMsgs, EM_GETLINECOUNT, 0, 0);
		::SendMessageA(hMsgs, EM_LINESCROLL, 0, nLines);
	}
	void showPrivateMessage(int64_t sessionid, const std::string& msg, bool recved)
	{
		std::ostringstream ss;
		if (!recved)
			ss << "you to ";
		chatroom->visitNames([&ss, sessionid](const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>& names)
		{
			auto it = names.find(sessionid);
			if (it != names.end())
				ss << it->second.nickname;
		});
		if (recved)
			ss << " to you";
		ss << " : " << msg;
		showChatMessage(ss.str());
	}
	void showMessageToAll(int64_t sessionid, const std::string& msg)
	{
		std::ostringstream ss;
		chatroom->visitNames([sessionid, &ss](const limax::hashmap<int64_t, class chat::chatclient::chatviews::UserInfo::name>& names)
		{
			auto it = names.find(sessionid);
			if (it != names.end())
				ss << it->second.nickname;
		});
		ss << " to all : " << msg;
		showChatMessage(ss.str());
	}

} g_listener;

INT_PTR CALLBACK MainDialogProc(HWND hwndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	switch (uMsg)
	{
	case WM_INITDIALOG:
		limax::runOnUiThread([](){ showLoginDialog(); });
		return TRUE;
	case WM_PAINT:
		if (g_imageicon)
		{
			PAINTSTRUCT ps;
			::BeginPaint(hwndDlg, &ps);
			HDC dc = ::CreateCompatibleDC(ps.hdc);
			::SelectObject(dc, g_imageicon);
			::BitBlt(ps.hdc, 260, 130, 100, 61, dc, 0, 0, SRCCOPY);
			::EndPaint(hwndDlg, &ps);
			::DeleteDC(dc);
			return TRUE;
		}
		return FALSE;
	case WM_COMMAND:
		switch (LOWORD(wParam))
		{
		case IDOK:
			return TRUE;
		case IDC_BUTTON_LOGIN:
			showLoginDialog();
			return TRUE;
		case IDC_BUTTON_LOGOUT:
			g_listener.logout();
			return TRUE;
		case IDC_BUTTON_RENAME:
			showRenameDialog();
			return TRUE;
		case IDC_BUTTON_JOIN:
		{
			TVITEM item;
			item.mask = TVIF_HANDLE;

			auto htree = ::GetDlgItem(hwndDlg, IDC_TREE_ROOMS);
			item.hItem = TreeView_GetSelection(htree);
			if (nullptr == item.hItem)
				return TRUE;
			if (!TreeView_GetItem(htree, &item))
				return TRUE;
			auto id = g_listener.getRoomIdByParam(item.lParam);
			std::ostringstream ss;
			ss << "join=" << id;
			chat::chatclient::chatviews::UserInfo::getInstance()->sendMessage(ss.str());
			EnableWindow(GetDlgItem(hwndDlg, IDC_BUTTON_JOIN), FALSE);
			return TRUE;
		}
		case IDCANCEL:
			DestroyWindow(hwndDlg);
			return TRUE;
		default:
			break;
		}
		return FALSE;
	case  WM_NOTIFY:
		if (IDC_TREE_ROOMS == wParam)
		{
			auto nmhdr = (LPNMHDR)lParam;
			switch (nmhdr->code)
			{
			case TVN_SELCHANGING:
			{
				auto p = (LPNMTREEVIEWA)lParam;
				bool isRoomItem = 0 != p->itemNew.lParam;
				bool hasname = false;
				chat::chatclient::chatviews::UserInfo::getInstance()->visitName([&hasname](const class chat::chatclient::chatviews::UserInfo::name& name) { hasname = !name.nickname.empty(); });
				EnableWindow(GetDlgItem(hwndDlg, IDC_BUTTON_JOIN), isRoomItem && hasname && !g_listener.hashChatRoom());
				return TRUE;
			}
			default:
				break;
			}
		}
		return FALSE;
	case WM_DESTROY:
		PostQuitMessage(0);
		return TRUE;
	case WM_USER_UI_THREAD_SCHEDUL:
		limax::uiThreadSchedule();
		return TRUE;
	default:
		break;
	}
	return FALSE;
}

INT_PTR CALLBACK LoginDialogProc(HWND hwndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	switch (uMsg)
	{
	case WM_INITDIALOG:
		SetDlgItemTextA(hwndDlg, IDC_EDIT_USERNAME, "win32apitest");
		SetDlgItemTextA(hwndDlg, IDC_EDIT_TOKEN, "123456");
		SetDlgItemTextA(hwndDlg, IDC_EDIT_PLATFLAG, "test");
		SetDlgItemTextA(hwndDlg, IDC_EDIT_SERVERIP, "127.0.0.1");
		SetDlgItemTextA(hwndDlg, IDC_EDIT_SERVERPORT, "10000");
		return TRUE;
	case WM_COMMAND:
		switch (LOWORD(wParam))
		{
		case IDOK:
		{
			const size_t bfsize = 1024;
			char buffer[bfsize];
			size_t ss = GetDlgItemTextA(hwndDlg, IDC_EDIT_USERNAME, buffer, bfsize);
			std::string username(buffer, ss);
			ss = GetDlgItemTextA(hwndDlg, IDC_EDIT_TOKEN, buffer, bfsize);
			std::string token(buffer, ss);
			ss = GetDlgItemTextA(hwndDlg, IDC_EDIT_PLATFLAG, buffer, bfsize);
			std::string platflag(buffer, ss);
			ss = GetDlgItemTextA(hwndDlg, IDC_EDIT_SERVERIP, buffer, bfsize);
			std::string serverip(buffer, ss);
			ss = GetDlgItemTextA(hwndDlg, IDC_EDIT_SERVERPORT, buffer, bfsize);
			std::string serverport(buffer, ss);
			auto builder = limax::Endpoint::createEndpointConfigBuilder(serverip, std::stoi(serverport), limax::LoginConfig::plainLogin(username, token, platflag));
			builder->executor(limax::runOnUiThread)->staticViewCreatorManagers({ chat::getChatviewsViewCreatorManager(100) });
			limax::Endpoint::start(builder->build(), &g_listener);
			EnableWindow(GetDlgItem(g_mainHwnd, IDC_BUTTON_LOGIN), FALSE);
			EndDialog(hwndDlg, IDOK);
			return TRUE;
		}
		case IDCANCEL:
			EndDialog(hwndDlg, IDCANCEL);
			return TRUE;
		default:
			return FALSE;
		}
	default:
		break;
	}
	return FALSE;
}

INT_PTR CALLBACK SetNicknameDialogProc(HWND hwndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	switch (uMsg)
	{
	case WM_INITDIALOG:
		chat::chatclient::chatviews::UserInfo::getInstance()->visitName([hwndDlg](const class chat::chatclient::chatviews::UserInfo::name& name)
		{
			SetDlgItemTextA(hwndDlg, IDC_EDIT_USERNAME, name.nickname.c_str());
		});
		return TRUE;
	case WM_COMMAND:
		switch (LOWORD(wParam))
		{
		case IDOK:
		{
			const size_t bfsize = 1024;
			char buffer[bfsize];
			size_t st = GetDlgItemTextA(hwndDlg, IDC_EDIT_NICKNAME, buffer, bfsize);
			std::string newnickname(buffer, st);
			if (newnickname.empty())
				return TRUE;
			auto view = chat::chatclient::chatviews::UserInfo::getInstance();
			bool same = false;
			view->visitName([&newnickname, &same](const class chat::chatclient::chatviews::UserInfo::name& name){ same = name.nickname == newnickname; });
			if (same)
				return TRUE;
			std::ostringstream ss;
			ss << "nickname=" << newnickname;
			view->sendMessage(ss.str());
			EndDialog(hwndDlg, IDOK);
			return TRUE;
		}
		case IDCANCEL:
			EndDialog(hwndDlg, IDCANCEL);
			return TRUE;
		default:
			return FALSE;
		}
	default:
		break;
	}
	return FALSE;
}

namespace chat {
	namespace chatclient {
		namespace chatviews {

			void ChatRoom::onOpen(const std::vector<int64_t>& sessionids)
			{
				g_listener.onChatRoomOpen(this);
			}
			void ChatRoom::onAttach(int64_t sessionid)
			{
				g_listener.onChatRoomAttach(this, sessionid);
			}
			void ChatRoom::onDetach(int64_t sessionid, int reason)
			{
				g_listener.onChatRoomDetach(this, sessionid, reason);
			}
			void ChatRoom::onClose()
			{
				g_listener.onChatRoomClose(this);
			}

		}
	}
}

INT_PTR CALLBACK ChatRoomDialogProc(HWND hwndDlg, UINT uMsg, WPARAM wParam, LPARAM lParam)
{
	switch (uMsg)
	{
	case WM_INITDIALOG:
		return TRUE;
	case WM_COMMAND:
		switch (LOWORD(wParam))
		{
		case IDOK:
		{
			std::string message;
			HWND hmsg = GetDlgItem(hwndDlg, IDC_EDIT_INPUT_MESSAGE);
			auto size = GetWindowTextLengthA(hmsg);
			message.resize(size + 1);
			size = GetWindowTextA(hmsg, &message[0], size + 1);
			message.resize(size);
			if (message.empty())
				return TRUE;
			SetWindowTextA(hmsg, "");
			if (message == ".quit")
			{
				g_listener.getChatRoom()->sendMessage("leave=");
				return TRUE;
			}

			HWND hlist = GetDlgItem(g_chatroomHwnd, IDC_LIST_MEMBERS);
			int64_t target = -1L;
			int index = SendMessageA(hlist, LB_GETCURSEL, 0, 0);
			if (LB_ERR != index)
				target = g_listener.getSessionIdByParam(SendMessageA(hlist, LB_GETITEMDATA, index, 0));

			std::ostringstream ss;
			ss << "message=" << target << "," << message;
			g_listener.getChatRoom()->sendMessage(ss.str());
			return TRUE;
		}
		case IDC_LIST_MEMBERS:
		{
			if (HIWORD(wParam) == LBN_SELCHANGE)
			{
				HWND hlist = GetDlgItem(g_chatroomHwnd, IDC_LIST_MEMBERS);
				int index = SendMessageA(hlist, LB_GETCURSEL, 0, 0);
				if (LB_ERR != index)
				{
					std::string name;
					size_t length = SendMessageA(hlist, LB_GETTEXTLEN, index, 0);
					name.resize(length + 1);
					length = SendMessageA(hlist, LB_GETTEXT, index, (LPARAM)&name[0]);
					name.resize(length);
					SetDlgItemTextA(hwndDlg, IDC_STATIC_TARGET, name.c_str());
				}
				else
				{
					SetDlgItemTextA(hwndDlg, IDC_STATIC_TARGET, "[all]");
				}
				return TRUE;
			}
			break;
		}
		default:
			return FALSE;
		}
		return FALSE;
	case WM_CLOSE:
	{
		auto view = g_listener.getChatRoom();
		view->sendMessage("leave=");
		return TRUE;
	}
	default:
		break;
	}
	return FALSE;
}