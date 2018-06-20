
local function main()

  local limaxlib = require "loadlib"
  local limaxctx = limaxlib();

  limaxctx.openEngine();
  print "press any key to start ping"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
    limaxctx.oshelper.sleep( 100)
  end

  local config = { ping = true, serverip = "127.0.0.1", serverport = 10000,
    onSocketConnected = function ()
      print "onSocketConnected"
    end,

    onAbort = function ()
      print "onAbort"
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

  local ping = limaxctx.start( config)

  print "press any key to close ping"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
    limaxctx.oshelper.sleep( 100)
  end

  ping:close();

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
