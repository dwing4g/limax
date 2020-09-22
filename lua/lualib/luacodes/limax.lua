return function (initializer, cache, ontunnel)
  local function toString36(n)
    local map = { 0,1,2,3,4,5,6,7,8,9,'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z'}
    local r, s = "", ""
    if n < 0 then
      n = -n
      s = "-"
    end
    while n > 35 do
      r = map[n % 36 + 1] .. r
      n = math.floor(n / 36)
    end
    return s .. map[n + 1] .. r
  end
  local function NIL()
  end
  local function Parser(_s)
    local s, p, d, _ = nil, nil, nil, {}
    _.I = function()
      local q = s:find(':', p)
      local r = tonumber(s:sub(p, q - 1), 36)
      p = q + 1
      return r
    end
    _.J = function()
      local q = s:find(':', p)
      local r = tonumber(s:sub(p, q - 1))
      p = q + 1
      return r
    end
    _.F = function()
      return _.J()
    end
    _.B = function()
      local c = s:sub(p, p)
      p = p + 1
      return c == 'T'
    end
    _.D = function()
      return _.U
    end
    _.S = function()
      local l = _.I()
      local q = utf8.offset(s, l + 1, p)
      local r = s:sub(p, q - 1)
      p = q
      return r
    end
    _.O = function()
      local l = _.I()
      local q = p
      local r = {}
      for i = 1, l do
        r[i] = tonumber(s:sub(q, q + 1), 16)
        q = q + 2
      end
      p = q
      return r
    end
    _.P = function()
      local r = {}
      while s:sub(p, p) ~= ":" do
        table.insert(r, _.R())
      end
      p = p + 1
      return r
    end
    _.L = function()
      if s:sub(p, p) ~= '?' then
        return _.P()
      end
      local r = {}
      while s:sub(p, p) ~= ":" do
        for k, v in pairs(_.R()) do
          r[k] = v
        end
      end
      p = p + 1
      return r
    end
    _.W = function()
      local r = _.P()
      return function()
        return r
      end
    end
    _.X = function()
      local r = {}
      r.f = d[_.I() + 1]
      r.v = _.R()
      return r
    end
    _.Y = function()
      local r = {}
      r.f = d[_.I() + 1]
      r.a = _.R()
      r.r = _.R()
      return r
    end
    _.Z = function()
      local r = {}
      r.f = d[_.I() + 1]
      r.c = _.R()
      r.r = _.R()
      return r
    end
    _.M = function()
      local r = {}
      while s:sub(p, p) ~= ":" do
        r[_.R()] = _.R()
      end
      p = p + 1
      return r
    end
    _.U = function()
      return NIL
    end
    _['?'] = function()
      local q = s:find('?', p)
      local k = d[tonumber(s:sub(p, q - 1), 36) + 1]
      p = q + 1
      return { [k] = _.R() }
    end
    _.R = function(_s)
      if (_s == nil)  then
        local c = s:sub(p, p)
        p = p + 1
        return _[c]()
      end
      if (type(_s) == 'string') then
        s = _s
        p = 1
      else
        d = _s
      end
      return _.R()
    end
    return _.R
  end
  local p, c, d, t = Parser(), {}, {}, {}
  local function onerror(e)
    pcall(c.onerror, e)
  end
  local function R(r, l, m)
    local function f(r, s, v, o, b)
      local e = { view = r, sessionid = s, fieldname = v, value = o, type = b}
      if type(r.onchange) == "function" then
        r.onchange(e)
      end
      if r.__e__ ~= nil then
        if type(r.__e__[v]) == "function" then
          r.__e__[v](e)
        elseif type(r.__e__[v]) == "table" then
          for i = 1, #r.__e__[v] do
            r.__e__[v][i](e)
          end
        end
      end
    end
    local function u(r, s, w, i, v)
      local b
      local o = w[i]
      if v == NIL then
        b = 2
      elseif type(v) == "function" then
        local z = v()
        if z == NIL then
          w[i] = nil
          b = 3
        else
          if o == nil then
            w[i] = {}
            o = w[i]
            b = 0
          else
            b = 1
          end
          for j = 1, #z do
            v = z[j]
            if v.v ~= nil then
              o[v.f] = v.v
            elseif v.c == nil then
              if o[v.f] == nil then
                o[v.f] = {}
              end
              local n = o[v.f]
              for x = 1, #v.r do
                for y = 1, #n do
                  if v.r[x] == n[y] then
                    table.remove(n, y)
                    break
                  end
                end
              end
              for x = 1, #v.a do
                table.insert(n, v.a[x])
              end
            else
              if o[v.f] == nil then
                o[v.f] = {}
              end
              local n = o[v.f]
              for x = 1, #v.r do
                n[v.r[x]] = nil
              end
              for x, y in pairs(v.c) do
                n[x] = y
              end
            end
          end
        end
      else
        if o == nil then
          b = 0
        else
          b = 1
        end
        w[i] = v
        o = w[i]
      end
      f(r, s, i, o, b)
    end
    for i = 1, #l do
      for j, v in pairs(l[i]) do
        u(r, c.i, r, j, v)
      end
    end
    for i = 1, #m, 2 do
      if m[i + 1] ~= NIL then
        for j, v in pairs(m[i + 1]) do
          if r[m[i]] == nil then
            r[m[i]] = {}
          end
          u(r, m[i], r[m[i]], j, v)
        end
      end
    end
  end
  local h = {
    [0] = function(r, s, l, m)
      R(r, l, m)
    end,
    [1] = function(r, s, l, m)
      r[s] = {
        __p__ = r.__p__,
        __c__ = r.__c__,
        __n__ = r.__n__,
        __i__ = s,
      }
      if type(r.onopen) == "function" then
        local k = {}
        for i = 1, #m, 2 do
          table.insert(k, m[i])
        end
        r:onopen(s, k)
      else
        onerror(r.__n__ ..  " onopen not defined")
      end
      R(r[s], l, m)
    end,
    [2] = function(r, s, l, m)
      R(r[s], l, m)
    end,
    [3] = function(r, s, l, m)
      if type(r.onattach) == "function" then
        r:onattach(s, m[1])
      else
        onerror(r.__n__ ..  " onattach not defined")
      end
      R(r[s], l, m)
    end,
    [4] = function(r, s, l, m)
      if type(r.ondetach) == "function" then
        r:ondetach(s, m[1], m[2])
      else
        onerror(r.__n__ ..  " ondetach not defined")
      end
      r[s][m[1]] = nil
    end,
    [5] = function(r, s, l, m)
      if type(r.onclose) == "function" then
        r:onclose(s)
      else
        onerror(r.__n__ ..  " onclose not defined")
      end
      r[s] = nil
    end
  }
  if cache == nil then
    cache = {
      put = function(key, value)
      end,
      get = function(key)
      end
    }
  end
  local function init(s)
    local r = { p(s), p() }
    if r[2] ~= 0 then
      return r[2]
    end
    c.i, c.f = p(), p()
    local l = p()
    local lmkdata = p()
    for i = 1, #l, 3 do
      d[l[i]] = {}
      for v in l[i + 1]:gmatch('[^,]+') do
        table.insert(d[l[i]], v)
      end
      local ck = table.remove(d[l[i]])
      local cv = cache.get(ck)
      if cv ~= nil then
        d[l[i]] = {}
        for v in p(cv):gmatch('[^,]+') do
          table.insert(d[l[i]], v)
        end
        l[i + 2] = p()
      else
        cv = table.concat(d[l[i]], ',')
        cv = 'S' .. toString36(cv:len()) .. ':' .. cv .. 'M'
        for k, v in pairs(l[i + 2]) do
          cv = cv .. 'I' .. toString36(k) .. ':L'
          for j = 1, #v do
            cv = cv .. 'I' .. toString36(v[j]) .. ':'
          end
          cv = cv .. ':'
        end
        cache.put(ck, cv .. ':')
      end
      t[l[i]], c[l[i]] = {}, {}
      local s = c[l[i]]
      for k, v in pairs(l[i + 2]) do
        local r = s
        local m = ""
        for j, v0 in pairs(v) do
          local n = d[l[i]][v0 + 1]
          m = m .. n .. "."
          if r[n] == nil then
            r[n] = {}
          end
          r = r[n]
        end
        t[l[i]][k] = r
        r.__p__ = l[i]
        r.__c__ = k
        r.__n__ = m:sub(0, m:len() - 1)
      end
    end
    if ontunnel then
   		ontunnel(1, 0, lmkdata)
    end
  end
  local function update(s)
    local v = p(s)
    if type(v) == 'string' then
		if ontunnel then
			ontunnel(p(), p(), v)
		end 
    else
		local r = t[v][p(d[v])]
		local i = p()
		h[p()](r, i, p(), p())
    end
  end
  local z, g = false, init
  local function onclose(p)
    z = true
    pcall(c.onclose, p)
    return 3
  end
  local login
  local tunnel
  local logindatas = {}
  local function onmessage(s)
    if g == init then
      if s:sub(1, 1) == "$" then
      	login(tonumber(s:sub(2), 36))
      	return 0
      end
      local r = g(s)
      if r then
        return onclose(r)
      end
      if type(c.onopen) == "function" then
        c.onopen()
      else
        onerror("context onopen not defined")
      end
      g = update
      return 2
    end
    g(s)
    return 0
  end
  initializer(c)
  return function(...)
	if z then
		return 3
	end
	local t, p = ...
	local argslen = select("#", ...)
  	if argslen == 1 then
  		logindatas = t
  		return
  	end
  	if argslen == 3 then
  		if tunnel then
  			tunnel(...)
  		end
  		return
  	end
    if t == 0 then
      login = function(v)
      	if not z then
      	  local s = toString36(v) .. ","
      	  local logindata = logindatas[v]
      	  if logindata then
			if type(logindata.label) == "number" then
			  s = s .. "1," .. toString36(logindata.label) .. "," .. logindata.data
			elseif not logindata.base64 then
			  s = s .. "2," .. logindata.data
			else
			  s = s .. "3," .. logindata.data
			end
      	  else
      	  	s = s .. "0"
      	  end
  		  local e = p(s)    	  
          if e ~= nil then
            onclose(e)
          end
      	end
      end
      tunnel = function(v, l, s)
        if not z then
          local e = p(toString36(v) .. "," .. toString36(l) .. "," .. s)
          if e ~= nil then
            onclose(e)
          end
        end
      end
      c.send = function(r, s)
        if not z then
          local e = p(toString36(r.__p__) .. "," .. toString36(r.__c__) .. "," .. (not r.__i__ and "0" or toString36(r.__i__)) .. ":" .. s)
          if e ~= nil then
            onclose(e)
          end
        end
      end
      c.register = function(r, v, f)
        if r.__e__ == nil then
          r.__e__ = {}
        end
        if r.__e__[v] == nil then
          r.__e__[v] = f
        elseif type(r.__e__[v]) == "function" then
          r.__e__[v] = {r.__e__[v], f}
        else
          table.insert(r.__e__[v], f)
        end
      end
      return 0
    elseif t == 1 then
      local r, v = pcall(onmessage, p)
      if r then
        return v
      else
        onerror(v)
      end
    end
    return onclose(p)
  end
end
