local dump = require "dump"
local providers = {12}

local function initialize(ctx)
  local type = {[0] = 'NEW', [1] = 'REPLACE', [2] = 'TOUCH', [3] = 'DELETE'}
  local v12 = ctx[12]
  v12.gs.testview.onchange = function(e)
    print("v12.gs.testview.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
    print("v12.gs.testview.onchange2", e.view.rolename);
  end

  local tempview = nil
  v12.gs.tempview.onchange = function(e)
    print("v12.gs.tempview.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
    print("v12.gs.tempview.onchange2", tempview.rolename,e.view.rolename);
    print("v12.gs.tempview.onchange2", dump(tempview.bind1),dump(e.view.bind1));
  end
  v12.gs.tempview.onopen = function(this, instanceid, memberids)
    this[instanceid].onchange = this.onchange
    tempview = this[instanceid]
    print('v12.gs.tempview.onopen', this[instanceid], instanceid, memberids)
    print("")
    print(tempview)
    print("")
    print(dump(tempview))
    print("")
    print(getmetatable(tempview))
    print("")
    print(dump(getmetatable(tempview)))
  end
  v12.gs.tempview.onattach = function(this, instanceid, memberid)
    print('v12.gs.tempview.onattach', this[instanceid], memberid);
  end
  v12.gs.tempview.ondetach = function(this, instanceid, memberid, reason)
    print('v12.gs.tempview.ondetach', this[instanceid], memberid, reason)
  end
  v12.gs.tempview.onclose = function(this, instanceid)
    print('v12.gs.tempview.onclose', this[instanceid])
  end

  ctx.register(v12.gs.testview, "buildings", function(e)
    local v = e.value.buildingMap[101];
    if v ~= nil then
      print('buildings level', v.level, 'time', v.finishtime)
    end
  end)
  ctx.register(v12.gs.testview, "prop", function(e)
    local v = e.value
    if v ~= nil then
      print('properties exp', v.exp)
    else
      print('properties is null')
    end
  end)
end

local function main()

  local limaxlib = require "loadlib"
  local limaxctx = limaxlib()
  local cbvars = { callback = function(ctx)
    ctx.onerror = function(e)
      print('limax error', tostring(e))
    end
    ctx.onclose = function(e)
      print('limax close', tostring(e));
    end
    ctx.onopen = function()
      xpcall(
        function ()
          initialize(ctx)
        end,
        function (msg)
          print('LUA ERROR: ', tostring(msg), '\n')
          print(debug.traceback())
        end)
    end
  end, pvids = providers }

  limaxctx.openEngine();
  print "press any key to start login"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
    limaxctx.oshelper.sleep( 100)
  end

  local config = { serverip = "127.0.0.1", serverport = 11000, username = "luamaptest", token = "123456", platflag = "test", fastscript = cbvars,

    onManagerInitialized = function()
      print "onManagerInitialized"
    end,

    onManagerUninitialized = function()
      print "onManagerUninitialized"
    end,

    onSocketConnected = function ()
      print "onSocketConnected"
    end,

    onAbort = function ()
      print "onAbort"
    end,

    onTransportAdded = function ()
      print "onTransportAdded"
    end,

    onTransportRemoved = function ()
      print "onTransportRemoved"
    end,

    onKeepAlived = function ( ping)
      print( "onKeepAlived ", ping)
    end,

    onErrorOccured = function ( t, c, i)
      print ("onErrorOccured", t, c, i)
    end
  }

  local login = limaxctx.start( config)

  print "press any key to close login"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
    limaxctx.oshelper.sleep( 100)
  end

  login:close();

  print "press any key to close engine"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle(1)
    limaxctx.oshelper.sleep( 100)
  end

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
