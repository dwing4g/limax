
local l101;
local l102;
local vars102;
local limaxctx;

local function connect(vars)
  local pvid = vars.pvids[1];
  local config = { serverip = "10.137.20.32", serverport = 10000, username = "luatest", token = "123456", platflag = "test", script = vars,

    onManagerInitialized = function()
      print( pvid, "onManagerInitialized")
    end,

    onManagerUninitialized = function()
      print( pvid, "onManagerUninitialized")
    end,

    onSocketConnected = function ()
      print( pvid, "onSocketConnected")
    end,

    onAbort = function ()
      print( pvid, "onAbort")
    end,

    onTransportAdded = function ()
      print( pvid, "onTransportAdded")
    end,

    onTransportRemoved = function ()
      print( pvid, "onTransportRemoved")
    end,

    onKeepAlived = function ( ping)
      print( pvid, "onKeepAlived ", ping)
    end,

    onErrorOccured = function ( t, c, i)
      print (pvid, "onErrorOccured", t, c, i)
    end
  }

  return limaxctx.start( config)
end

local function initialize101(ctx)
  local type = {[0] = 'NEW', [1] = 'REPLACE', [2] = 'TOUCH', [3] = 'DELETE'}
  local v101 = ctx[101]
  v101.users.UserView.onchange = function(e)
    print("v101.users.UserView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
    l101:close()
    l102 = connect(vars102)
  end
end

local vars101 = { callback = function(ctx)
  ctx.onscript = function(a, s)
    print( 101, a, s)
  end
  ctx.onerror = function(e)
    print('101 limax error', tostring(e))
  end
  ctx.onclose = function(e)
    print('101 limax close', tostring(e));
  end
  ctx.onopen = function()
    xpcall(
      function ()
        initialize101(ctx)
      end,
      function (msg)
        print('LUA ERROR: ', tostring(msg), '\n')
        print(debug.traceback())
      end)
  end
end, pvids = {101} }

local function initialize102(ctx)
  local type = {[0] = 'NEW', [1] = 'REPLACE', [2] = 'TOUCH', [3] = 'DELETE'}
  local v102 = ctx[102]
  v102.roles.role.RoleView.onchange = function(e)
    print("v102.roles.role.RoleView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.building.BuildingView.onchange = function(e)
    print("v102.roles.building.BuildingView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.building.ArmyView.onchange = function(e)
    print("v102.roles.building.ArmyView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.item.BagView.onchange = function(e)
    print("v102.roles.item.BagView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.attr.RoleAttr.onchange = function(e)
    print("v102.roles.attr.RoleAttr.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.move.MoveView.onchange = function(e)
    print("v102.roles.move.MoveView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
end

vars102 = { callback = function(ctx)
  ctx.onscript = function(a, s)
    print( 102, a, s)
  end

  ctx.onerror = function(e)
    print('102 limax error', tostring(e))
  end
  ctx.onclose = function(e)
    print('102 limax close', tostring(e));
  end
  ctx.onopen = function()
    xpcall(
      function ()
        initialize102(ctx)
      end,
      function (msg)
        print('LUA ERROR: ', tostring(msg), '\n')
        print(debug.traceback())
      end)
  end
end, pvids = { 102 } }

local function main()

  local limaxlib = require "loadlib"
  limaxctx = limaxlib()

  limaxctx.openEngine();
  print "press any key to start login"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
    limaxctx.oshelper.sleep( 100)
  end

  l101 = connect( vars101)

  print "press any key to close engine"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
    limaxctx.oshelper.sleep( 100)
  end

  print "closeEngine now"
  limaxctx.closeEngine();
  print "press any key to quit"
  limaxctx.oshelper.waitkeyhit();
end

local function error_traceback(msg)
  print("----------------------------------------")
  print("LUA ERROR: ", tostring(msg), "\n")
  print(debug.traceback())
  print("----------------------------------------")
end

xpcall( main, error_traceback)
