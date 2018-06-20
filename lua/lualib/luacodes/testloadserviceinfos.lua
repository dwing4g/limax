
require "json"

local function main()
  local limaxlib = require "loadlib"
  local limaxctx = limaxlib()
  local cbvars = require "callback"
  local loadfunc = require "loadserviceinfos"
  local services = loadfunc(limaxctx, JSON, "127.0.0.1", 8181, 12, "", 10000, 4096, "", false)
  
  if #services < 1 then
    print ("not service found")
    return
  end
  
  if not services[1].running then
    print ("service not running")
    return
  end
  
  local config = services[1].config(cbvars, { username = "luatest", token = "123456", platflag = "test",

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
  })

  limaxctx.openTrace( function(msg) print("limax trace", msg) end, 5)
  limaxctx.openEngine();
  print "press any key to start login"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
    limaxctx.oshelper.sleep( 100)
  end

  login = limaxctx.start( config)

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
