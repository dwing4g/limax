
local function main()

  local limaxlib = require "loadlib"
  local limaxctx = limaxlib()
  local cbvars = require "callback"

  limaxctx.openTrace( function(msg) print("limax trace", msg) end, 5)
  limaxctx.openEngine();
  print "press any key to start login"
  while not limaxctx.oshelper.haskeyhit() do
    limaxctx.idle()
    limaxctx.oshelper.sleep( 100)
  end

  local config = { serverip = "127.0.0.1", serverport = 10000, username = "luatest", token = "123456", platflag = "test", script = cbvars,

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
      limaxctx.auanyservice.pay(0, 1, 1, 100, 1, "test receipt", 1000, function (s, e, o)
        print("!!! pay Simulation s = ", s , " e = " , e , " o = " , o)
      end)

      local r1 = "{";
      local r2 = " \"signature\" = \"AmXQsMpNa8tqINokkXKNTtNAh2wMNPzxe7wCTsPfr/NFIGKtRqC2JqBaKciNAgwUK2EabqzJdXiT1gyg+dl7zJMpCTTwaaLdANzV/1pibLt4+izMbmTMlouIfkDS1nCmy7ksEUJj0fWVIpPSrK/DaKBedX4hOPZ09rnDSmmXlgotAAADVzCCA1MwggI7oAMCAQICCGUUkU3ZWAS1MA0GCSqGSIb3DQEBBQUAMH8xCzAJBgNVBAYTAlVTMRMwEQYDVQQKDApBcHBsZSBJbmMuMSYwJAYDVQQLDB1BcHBsZSBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eTEzMDEGA1UEAwwqQXBwbGUgaVR1bmVzIFN0b3JlIENlcnRpZmljYXRpb24gQXV0aG9yaXR5MB4XDTA5MDYxNTIyMDU1NloXDTE0MDYxNDIyMDU1NlowZDEjMCEGA1UEAwwaUHVyY2hhc2VSZWNlaXB0Q2VydGlmaWNhdGUxGzAZBgNVBAsMEkFwcGxlIGlUdW5lcyBTdG9yZTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwgZ8wDQYJKoZIhvcNAQEBBQADgY0AMIGJAoGBAMrRjF2ct4IrSdiTChaI0g8pwv/cmHs8p/RwV/rt/91XKVhNl4XIBimKjQQNfgHsDs6yju++DrKJE7uKsphMddKYfFE5rGXsAdBEjBwRIxexTevx3HLEFGAt1moKx509dhxtiIdDgJv2YaVs49B0uJvNdy6SMqNNLHsDLzDS9oZHAgMBAAGjcjBwMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAUNh3o4p2C0gEYtTJrDtdDC5FYQzowDgYDVR0PAQH/BAQDAgeAMB0GA1UdDgQWBBSpg4PyGUjFPhJXCBTMzaN+mV8k9TAQBgoqhkiG92NkBgUBBAIFADANBgkqhkiG9w0BAQUFAAOCAQEAEaSbPjtmN4C/IB3QEpK32RxacCDXdVXAeVReS5FaZxc+t88pQP93BiAxvdW/3eTSMGY5FbeAYL3etqP5gm8wrFojX0ikyVRStQ+/AQ0KEjtqB07kLs9QUe8czR8UGfdM1EumV/UgvDd4NwNYxLQMg4WTQfgkQQVy8GXZwVHgbE/UC6Y7053pGXBk51NPM3woxhd3gSRLvXj+loHsStcTEqe9pBDpmG5+sk4tw+GK3GMeEN5/+e1QT9np/Kl1nj+aBw7C0xsy0bFnaAd1cSS6xdory/CUvM6gtKsmnOOdqTesbp0bs8sn6Wqs0C9dgcxRHuOMZ2tm8npLUm7argOSzQ==\";";
      local r3 = " \"purchase-info\" = \"ewoJIm9yaWdpbmFsLXB1cmNoYXNlLWRhdGUtcHN0IiA9ICIyMDEzLTExLTAyIDE3OjIwOjU1IEFtZXJpY2EvTG9zX0FuZ2VsZXMiOwoJInB1cmNoYXNlLWRhdGUtbXMiID0gIjEzODM0MzgwNTU4ODkiOwoJInVuaXF1ZS1pZGVudGlmaWVyIiA9ICI2ZWRjNDEwNDljMWVlYzcwNTk4ZGQ0MWRlMmM4Y2YzY2UzNDI5NjFjIjsKCSJvcmlnaW5hbC10cmFuc2FjdGlvbi1pZCIgPSAiODAwMDAwNzIyNjA1NjEiOwoJImJ2cnMiID0gIjEuNC4wIjsKCSJhcHAtaXRlbS1pZCIgPSAiNjY4MzE3OTI2IjsKCSJ0cmFuc2FjdGlvbi1pZCIgPSAiODAwMDAwNzIyNjA1NjEiOwoJInF1YW50aXR5IiA9ICIxIjsKCSJvcmlnaW5hbC1wdXJjaGFzZS1kYXRlLW1zIiA9ICIxMzgzNDM4MDU1ODg5IjsKCSJ1bmlxdWUtdmVuZG9yLWlkZW50aWZpZXIiID0gIjU4OEMyQkI1LUM2RUItNEQ4OS1BRTQ0LTUyREVGM0IxQTZGOCI7CgkiaXRlbS1pZCIgPSAiNjY4MzMwMTEzIjsKCSJ2ZXJzaW9uLWV4dGVybmFsLWlkZW50aWZpZXIiID0gIjU2MzYyNjcyIjsKCSJwcm9kdWN0LWlkIiA9ICJjb20ud2FubWVpLm1pbmkuY29uZG9yYXBwXzMyIjsKCSJwdXJjaGFzZS1kYXRlIiA9ICIyMDEzLTExLTAzIDAwOjIwOjU1IEV0Yy9HTVQiOwoJIm9yaWdpbmFsLXB1cmNoYXNlLWRhdGUiID0gIjIwMTMtMTEtMDMgMDA6MjA6NTUgRXRjL0dNVCI7CgkiYmlkIiA9ICJjb20ud2FubWVpLm1pbmkuY29uZG9yYXBwIjsKCSJwdXJjaGFzZS1kYXRlLXBzdCIgPSAiMjAxMy0xMS0wMiAxNzoyMDo1NSBBbWVyaWNhL0xvc19BbmdlbGVzIjsKfQ==\";";
      local r4 = " \"pod\" = \"8\";";
      local r5 = " \"signing-status\" = \"0\";";
      local r6 = "}";

      local r = r1 .. "\r\n" .. r2 .. "\r\n" .. r3 .. "\r\n" .. r4 .. "\r\n" .. r5 .. "\r\n" .. r6

      limaxctx.auanyservice.pay(1, 1, 0, 0, 0, r, 1000, function (s, e, o)
        print("!!! pay AppStore s = " , s , " e = " , e , " o = " , o);
      end)
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
