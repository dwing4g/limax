
local providers = { 12 }

local function initialize( ctx)
  local type = { [0] = "NEW", [1] = "REPLACE", [2] = "TOUCH", [3] = "DELETE" }
  local v12 = ctx[12]

  ctx.register( v12.gs.for_session.firstview, "var1", function(e)
    print( "var1 gs.for_session.firstview", e.view, e.sessionid, e.fieldname, e.value, type[e.type])
    ctx.send( v12.gs.for_session.firstview, "i get it")
  end)

  ctx.register( v12.gs.for_session.firstview, "bindsecond", function(e)
    print( "bindsecond gs.for_session.firstview", e.view, e.sessionid, e.fieldname, e.value, type[e.type])
	end)

  v12.gs.for_session.firstview.onchange = function(e)
    print( "v12.gs.for_session.firstview.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v12.gs.globalview.onchange = function(e)
    print( "v12.gs.globalview.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v12.gs.TestTempView.onchange = function(e)
    print( "v12.gs.TestTempView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v12.gs.TestTempView.onopen = function( this, instanceid, memberids)
    this[instanceid].onchange = this.onchange
    print( "v12.gs.TestTempView.onopen", this[instanceid], instanceid, memberids)
  end
  v12.gs.TestTempView.onattach = function( this, instanceid, memberid)
    print( "v12.gs.TestTempView.onattach", this, instanceid, memberid);
  end
  v12.gs.TestTempView.ondetach = function( this, instanceid, memberid, reason)
    print( "v12.gs.TestTempView.ondetach", this, instanceid, memberid, reason)
  end
  v12.gs.TestTempView.onclose = function( this, instanceid)
    print( "v12.gs.TestTempView.onclose", this, instanceid)
  end
  v12.gs.for_temp.TestTempView.onchange = function(e)
    print( "v12.gs.for_temp.TestTempView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v12.gs.for_temp.TestTempView.onopen = function( this, instanceid, memberids)
    this[instanceid].onchange = this.onchange
    print( "v12.gs.for_temp.TestTempView.onopen", this[instanceid], instanceid, memberids)
  end
  v12.gs.for_temp.TestTempView.onattach = function( this, instanceid, memberid)
    print( "v12.gs.for_temp.TestTempView.onattach", this[instanceid], memberid);
  end
  v12.gs.for_temp.TestTempView.ondetach = function( this, instanceid, memberid, reason)
    print( "v12.gs.for_temp.TestTempView.ondetach", this[instanceid], memberid, reason)
  end
  v12.gs.for_temp.TestTempView.onclose = function( this, instanceid)
    print( "v12.gs.for_temp.TestTempView.onclose", this[instanceid])
  end

  v12.gs.for_temp.TestTempView2.onchange = function(e)
    print( "v12.gs.for_temp.TestTempView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v12.gs.for_temp.TestTempView2.onopen = function( this, instanceid, memberids)
    this[instanceid].onchange = this.onchange
    print( "v12.gs.for_temp.TestTempView2.onopen", this[instanceid], instanceid, memberids)
  end
  v12.gs.for_temp.TestTempView2.onattach = function( this, instanceid, memberid)
    print( "v12.gs.for_temp.TestTempView2.onattach", this[instanceid], memberid);
  end
  v12.gs.for_temp.TestTempView2.ondetach = function( this, instanceid, memberid, reason)
    print( "v12.gs.for_temp.TestTempView2.ondetach", this[instanceid], memberid, reason)
  end
  v12.gs.for_temp.TestTempView2.onclose = function( this, instanceid)
    print( "v12.gs.for_temp.TestTempView2.onclose", this[instanceid])
  end
  
end

return { callback = function( ctx)

    ctx.onscript = function( i, s)
    --    local scriptlogfile = io.open( "/temp/script.txt", "a+")
    --    scriptlogfile:write( "limax( ", tostring( i), ",\"", tostring( s), "\")\r\n")
    --    scriptlogfile:close()
    --  print("in lua", i, s)
    end

    ctx.errortraceback= function(m)
      print( "LUA CODE ERROR: ", tostring( msg), "\n")
      print( debug.traceback())
    end

    ctx.onerror = function(e)
      print( "limax error", tostring( e))
    end
    ctx.onclose = function(e)
      print( "limax close", tostring( e))
    end
    ctx.onopen = function()
      initialize( ctx)
    end
end, pvids = providers }
