var providers = [ 100 ];
var connector;
var limax;
var context;
var currentChatView;

function createLimax() {
	return Limax(function(ctx) {
		context = ctx;
		ctx.onerror = function(e) {
			console.log(e);
			if (context == ctx)
				showErrorFrame(e);
		}
		ctx.onclose = function(e) {
			console.log(e);
			if (context == ctx)
				showCloseFrame(e);
		}
		ctx.onopen = function() {

			var type = [ 'NEW', 'REPLACE', 'TOUCH', 'DELETE' ];
			var v100 = ctx[100];

			v100.chatviews.ChatRoom.onopen = function(instanceid, memberids) {
				currentChatView = this[instanceid];
				showChatFrame(currentChatView);

				ctx.register(currentChatView, "info", function(e) {
					showChatRoomName(e.value);
				});
				ctx.register(currentChatView, "names", function(e) {
					updateName(e.sessionid, e.value.nickname);
				});
				ctx.register(currentChatView, "lastmessage", function(e) {
					if (e.type == 1 || e.type == 2)
						showMessageToAll(e.value.user, e.value.msg);
				});
			}
			v100.chatviews.ChatRoom.onattach = function(instanceid, memberid) {
				onMemberAttach(memberid);
			}
			v100.chatviews.ChatRoom.ondetach = function(instanceid, memberid,
					reason) {
				onMemberDetach(memberid, reason >= 0);
			}
			v100.chatviews.ChatRoom.onclose = function(instanceid) {
				showMainFrame();
				currentChatView = null;
			}

			showMainFrame();

			ctx.register(v100.chatviews.CommonInfo, "halls", function(e) {
				showHalls(e.value);
			});

			ctx.register(v100.chatviews.UserInfo, "name", function(e) {
				if (e.value.nickname == "")
					doRename();
				else
					showNickname();
			});

			ctx.register(v100.chatviews.UserInfo, "lasterror", function(e) {
				if (e.value == 2)
					doRename();
				else
					showErrorFrame(e.value);
			});

			ctx.register(v100.chatviews.UserInfo, "recvedmessage", function(e) {
				onReceiveMessage(e.value);
			});
			ctx.register(v100.chatviews.UserInfo, "sendedmessage", function(e) {
				onSendedMessage(e.value);
			});
		}
	}, {
		put : function(key, value) {
			sessionStorage[key] = value;
		},
		get : function(key) {
			return sessionStorage[key];
		},
		keys : function() {
			var list = [];
			for (var key in sessionStorage)
				list.push(key);
			return list;
		}
	});
}

function doLogin() {
	var _usernane = document.getElementById('username').value;
	var _token = document.getElementById('token').value;
	var _platflag = document.getElementById('platflag').value;
	var _host = document.getElementById('serverhost').value;
	var login = {
		scheme : 'ws',
		host : _host,
		username : _usernane,
		token : _token,
		platflag : _platflag,
		pvids : [ 100 ],
	};
	limax = createLimax();
	connector = WebSocketConnector(limax, login);
	document.getElementById('errorInfo').innerHTML = "";
}

function showLoginFrame() {
	var htmlstring = "<table>";
	htmlstring += "<tr>";
	htmlstring += "<td>username</td>";
	htmlstring += "<td><input type='text' id='username' value='jstest'/></td>";
	htmlstring += "</tr>";
	htmlstring += "<tr>";
	htmlstring += "<td>token</td>";
	htmlstring += "<td><input type='password' id='token' value='123456'/></td>";
	htmlstring += "</tr>";
	htmlstring += "<tr>";
	htmlstring += "<td>platflag</td>";
	htmlstring += "<td><input type='text' id='platflag' value='test' disabled='true'/></td>";
	htmlstring += "</tr>";
	htmlstring += "<tr>";
	htmlstring += "<td>host</td>";
	htmlstring += "<td><input type='text' id='serverhost' value='127.0.0.1:10001'/></td>";
	htmlstring += "<td><input type='button' value='login' onclick='doLogin()'/></td>";
	htmlstring += "</tr>";
	htmlstring += "</table>";
	document.getElementById('dynamicShow').innerHTML = htmlstring;
}

function showErrorFrame(e) {
	document.getElementById('errorInfo').innerHTML = "<td>error</td><td>" + e
			+ "</td>";
}

function showCloseFrame(e) {
	var htmlstring = "<table>";
	htmlstring += "<tr>";
	htmlstring += "<td>close</td>";
	htmlstring += "<td>";
	htmlstring += e;
	htmlstring += "</td>";
	htmlstring += "<td><input type='button' value='relogin' onclick='showLoginFrame()'/></td>";
	htmlstring += "</tr><br>";
	htmlstring += "</table>";
	document.getElementById('dynamicShow').innerHTML = htmlstring;
}

function doRelogin() {
	connector.close("relogin");
}

function doRename() {
	var v100 = context[100];
	var name = prompt("input name", v100.chatviews.UserInfo.name.nickname);
	if (name != null && name.length != 0 && name != v100.chatviews.UserInfo.name.nickname)
		context.send(v100.chatviews.UserInfo, "nickname=" + name);
}

function showNickname() {
	var v100 = context[100];
	var nickname;
	if (typeof (v100.chatviews.UserInfo.name) == "undefined")
		nickname = "";
	else
		nickname = v100.chatviews.UserInfo.name.nickname;
	document.getElementById('nickname').innerHTML = nickname;
}

function doJoin(roomid) {
	var v100 = context[100];
	context.send(v100.chatviews.UserInfo, "join=" + roomid);
}

function doJoinByName(hallname, roomname) {
	var v100 = context[100];
	context.send(v100.chatviews.UserInfo, "joinbyname=" + hallname + "," + roomname);
}

function showMainFrame() {
	var htmlstring = "<table>";
	htmlstring += "<tr>";
	htmlstring += "<td><div id='nickname'></div></td>";
	htmlstring += "<td>";
	htmlstring += "<tr>";
	htmlstring += "<td><div id='rooms'></div></td>";
	htmlstring += "<td>";
	htmlstring += "<table>";
	htmlstring += "<tr><td><input type='button' value='relogin' onclick='doRelogin()'/></td></tr>";
	htmlstring += "<tr><td><input type='button' value='rename' onclick='doRename()'/></td></tr>";
	htmlstring += "<tr><td><div id='joinbutton'><input type='button' value='join' disabled='1'/></td></tr>";
	htmlstring += "</table>";
	htmlstring += "</td>";
	htmlstring += "</tr><br>";
	htmlstring += "</table>";
	document.getElementById('dynamicShow').innerHTML = htmlstring;

	showNickname();

	var v100 = context[100];
	if (typeof (v100.chatviews.CommonInfo.halls) != "undefined")
		showHalls(v100.chatviews.CommonInfo.halls);
}

function onRoomSelect(roomid) {
	var v100 = context[100];
	var htmlstring = "<input type='button' value='join'";
	if (v100.chatviews.UserInfo.name.nickname == "")
		htmlstring += "disabled='1' ";
	else
		htmlstring += "onclick='doJoin(" + roomid + ")' ";
	htmlstring += "/>";
	document.getElementById('joinbutton').innerHTML = htmlstring;
}

function showHalls(halls) {
	var htmlstring = "<ul>";
	for ( var i in halls) {
		var h = halls[i];
		htmlstring += "<li>";
		htmlstring += h.name;
		htmlstring += "<ul>";
		for ( var j in h.rooms) {
			var r = h.rooms[j];
			htmlstring += "<li onclick='onRoomSelect( " + r.roomid + ")'>";
			htmlstring += r.name;
			htmlstring += "</li>";
		}
		htmlstring += "</ul>";
		htmlstring += "</li>";
	}
	htmlstring += "</ul>";
	document.getElementById('rooms').innerHTML = htmlstring;
}

function showChatFrame(view) {
	currentChatView = view;
	var htmlstring = "<table>";
	htmlstring += "<tr>";
	htmlstring += "<td><a id='roomname'></a></td><td colSpan='2'><a id='nickname'></a><input type='button' value='rename' onclick='doRename()'/><input type='button' value='leave' onclick='doLeave()'/></td>";
	htmlstring += "</tr>";
	htmlstring += "<tr>";
	htmlstring += "<td colSpan='2'><textarea rows='22' cols='50' id='messages'></textarea></td>";
	htmlstring += "<td><select id='members' size='20' onchange='messageToChange()'><option value='-1' selected>[all]</option></select></td>";
	htmlstring += "</tr>";
	htmlstring += "<tr>";
	htmlstring += "<td id='messageto'>[all]</td>";
	htmlstring += "<td><input type='input' id='sendmsg'/></td>";
	htmlstring += "<td><input type='button' value='send' onclick='sendMessage()'/></td>";
	htmlstring += "</tr>";
	htmlstring += "</table>";
	document.getElementById('dynamicShow').innerHTML = htmlstring;
	showNickname();
}

function doLeave() {
	context.send(currentChatView, "leave=");
}

function messageToChange() {
	var members = document.getElementById('members');
	var index = members.selectedIndex;
	if (-1 != index) {
		var selected = members.options[index];
		var msgto = document.getElementById('messageto');
		msgto.innerHTML = selected.text;
	}
}

function getMessageTo() {
	var members = document.getElementById('members');
	var index = members.selectedIndex;
	if (-1 != index) {
		var selected = members.options[index];
		return selected.value;
	}
	return -1;
}

function sendMessage() {
	var sendmsg = document.getElementById('sendmsg');
	var msg = sendmsg.value.trim();
	if (msg.length > 0)
		context.send(currentChatView, "message=" + getMessageTo() + "," + msg);
	sendmsg.value = "";
}

function showChatRoomName(info) {
	document.getElementById('roomname').innerHTML = info.name;
}

function showTextToMessages(line) {
	var msgs = document.getElementById('messages');
	if (msgs != null)
		msgs.value += (line + "\r\n");
}

function onReceiveMessage(msg) {
	if (context.i == msg.user) {
		showTextToMessages(msg.msg);
	} else {
		var text = currentChatView[msg.user].names.nickname + " to you : " + msg.msg;
		showTextToMessages(text);
	}
}

function onSendedMessage(msg) {
	var text = "you to " + currentChatView[msg.user].names.nickname + " : "	+ msg.msg;
	showTextToMessages(text);
}

function updateName(sessionid, name) {
	if (context.i == sessionid)
		return;
	var members = document.getElementById('members');
	var selid = -1;
	var index = members.selectedIndex;
	if (-1 != index) {
		var selected = members.options[index];
		selid = selected.value;
	}

	var oldname = null;
	for (var i = 0; i < members.length; i++) {
		if (sessionid == members.options[i].value) {
			oldname = members.options[i].text;
			members.remove(i)
			break;
		}
	}
	var nu = document.createElement('option');
	nu.value = sessionid;
	nu.text = name;
	try {
		members.add(nu, null); // standards compliant
	} catch (ex) {
		members.add(nu); // IE only
	}

	for (var i = 0; i < members.length; i++) {
		if (selid == members.options[i].value) {
			members.selectedIndex = i;
			messageToChange();
			break;
		}
	}

	if (null != oldname)
		showTextToMessages("[user '" + oldname + "' change name to '" + name
				+ "']");
}

function onMemberAttach(sessionid) {
	var done = false;
	if (typeof (currentChatView[sessionid]) != "undefined"
			&& typeof (currentChatView[sessionid].names) != "undefined") {
		showTextToMessages("[user \"" + currentChatView[sessionid].names.nickname
				+ "\" enter room]");
		done = true;
	}
	if (!done)
		setTimeout("onMemberAttach( " + sessionid + ")", 1);
}

function onMemberDetach(sessionid, leave) {
	var msg = leave ? "leave room" : "disconnected";
	var text = "[user \"" + currentChatView[sessionid].names.nickname + "\" " + msg
			+ "]";
	showTextToMessages(text);

	var members = document.getElementById('members');
	for (var i = 0; i < members.length; i++) {
		if (sessionid == members.options[i].value) {
			members.remove(i)
			break;
		}
	}
	if (-1 != members.selectedIndex)
		return;
	for (var i = 0; i < members.length; i++) {
		if (-1 == members.options[i].value) {
			members.selectedIndex = i;
			messageToChange();
			break;
		}
	}
}

function showMessageToAll(sessionid, text) {
	var text = currentChatView[sessionid].names.nickname + " to all : " + text;
	showTextToMessages(text);
}
