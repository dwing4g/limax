
local function main()

  local limaxlib = require "loadlib"
  local limaxctx = limaxlib()
  local cbvars = require "callback"

  limaxctx.openTrace( function(msg) print("limax trace", msg) end, 5)
  limaxctx.openEngine();
  print "press any key to start auservice.temporary"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
    limaxctx.oshelper.sleep( 100)
  end

  local appId = 1
  local credential = nil

  limaxctx.auanyservice.bind("127.0.0.1", 8181, appId, "machine identity", "luatest", "123456", "test", 10000,
    function(errorSource, errorCode, _credential)
      print("auservice temporary result", errorSource, errorCode, _credential)
      if errorSource == 0 and errorCode == 0 then
        credential = _credential
      end
    end)

  if nil ~= credential then
    print( "press any key to start login", credential)
    while not limaxctx.oshelper.haskeyhit() do
      limaxctx.idle()
      limaxctx.oshelper.sleep( 100)
    end

    local login
    local config = { serverip = "127.0.0.1", serverport = 10000, username = credential, token = "machine identity", platflag = "credential", script = cbvars,

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

    login = limaxctx.start( config)

    print "press any key to close login"
    while not limaxctx.oshelper.haskeyhit() do
      limaxctx.idle()
      limaxctx.oshelper.sleep( 100)
    end

    login:close();
  end

  print "press any key to close engine"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
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
