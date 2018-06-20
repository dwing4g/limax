math.randomseed(os.time())

return function(limaxctx, JSON, httpHost, httpPort, appid, additionalQuery, timeout, maxsize, cacheDir, staleEnable)
  local url = "http://" .. httpHost .. ":" .. httpPort .. "/app?native=" .. appid
  if additionalQuery:len() > 0 then
  	if additionalQuery:sub(1,1) ~= "&" then
  		url = url .. "&"
  	end 
  	url = url .. additionalQuery
  end
  local jsonstring = limaxctx.httpdownload(url, timeout, maxsize, cacheDir, staleEnable)
  local s, o = pcall(JSON.parse, jsonstring)
  if not s then
    return nil
  end
  local services = o.services;
  for i = 1, #services do
    local s, t = services[i], {}
    for j = 1, #s.userjsons do
      local s, o = pcall(JSON.parse, s.userjsons[j])
      if not s then
        return nil
      end
      table.insert(t, o)
    end
    s.userjsons = t;
    table.insert(s.pvids, 1)
    s.host = function()
      local sw = s.switchers[math.random(#s.switchers)]
      return { serverip = sw.host, serverport = sw.port }
    end
    s.config = function(cbvars, c)
      local sw = s.host()
      cbvars.pvids = s.pvids;
      c.serverip, c.serverport, c.script = sw.serverip, sw.serverport, cbvars
      return c
    end
  end
  return services
end
