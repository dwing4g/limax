if JSON then
  return
end

local function parse(s)
  local p, l = 1, s:len()
  local c
  local C, T, O, A, S, N, V
  C = function ()
    if (p > l) then
      error("insufficient input string")
    end
    c = s:sub(p, p)
    p = p + 1
  end
  T = function ()
    while true do
      C()
      if c == ' ' or c == '\f' or c == '\n' or c == '\r' or c == '\t' or c == '\v' then
      else
        return
      end
    end
  end
  O = function ()
    local r = {}
    while true do
      local k
      while true do
        T()
        if c == '"' then
          k = S()
          break
        elseif c == '}' then
          return r
        elseif c == ',' or c == ';' then
        else
          error("want string or '}'")
        end
      end
      T()
      if c == ':' or c == '=' then
        r[k] = V()
      else
        error("want [:=]")
      end
    end
  end
  A = function()
    local r = {}
    local i = 0
    while true do
      T()
      if c == ']' then
        return r
      elseif c == ',' or c == ';' then
      else
        p = p - 1
        i = i + 1
        r[i] = V()
      end
    end
  end
  S = function()
    local m =""
    while true do
      C()
      if c == '"' then
        return m
      end
      if c == '\\' then
        C()
        if c == '"' or c == '\\' or c == '/' then
        elseif c == 'b' then
          c = '\b'
        elseif c == 'f' then
          c = '\f'
        elseif c == 'n' then
          c = '\n'
        elseif c == 'r' then
          c = '\r'
        elseif c == 't' then
          c = '\t'
        elseif c == 'u' then
          C()
          local cp = c
          C()
          cp = cp .. c
          C()
          cp = cp .. c
          C()
          cp = tonumber(cp .. c, 16)
          if cp < 0x80 then
            c = string.char(cp)
          elseif cp >= 0x80 and cp <= 0x7ff then
            c = string.char((cp >> 6) & 0x1f | 0xc0, cp & 0x3f | 0x80)
          elseif cp >= 0xd800 and cp <= 0xdbff then
            C()
            if c ~= '\\' then
              error("error low-surrogates")
            end
            C()
            if c ~= 'u' then
              error("error low-surrogates")
            end
            C()
            local ls = c
            C()
            ls = ls .. c
            C()
            ls = ls .. c
            C()
            ls = tonumber(ls .. c, 16)
            cp = (((cp - 0xd800) << 10) | (ls - 0xdc00)) + 0x10000
            c = string.char((cp >> 18) | 0xf0, (cp >> 12) & 0x3f | 0x80, (cp >> 6) & 0x3f | 0x80, cp & 0x3f | 0x80)             
          else
            c = string.char((cp >> 12) & 0x0f | 0xe0, (cp >> 6) & 0x3f | 0x80, cp & 0x3f | 0x80)
          end
        else
          error("unsupported escape character <" .. c .. ">")
        end
      end
      m = m .. c
    end
  end
  N = function()
    local m = ""
    while true do
      C()
      if c >= '0' and c <= '9' or c == '.' or c == '-' or c == '+' or c == 'e' or c == 'E' then
        m = m .. c
      else
        p = p - 1
        return tonumber(m)
      end
    end
  end
  V = function()
    if p <= l then
      T()
      if c == '{' then
        return O()
      elseif c == '[' then
        return A()
      elseif c == '"' then
        return S()
      elseif c >= '0' and c <= '9' or c == '-' then
        p = p - 1
        return N()
      elseif c == 't' or c == 'T' then
        if s:sub(p, p + 2):lower() == "rue" then
          p = p + 3
          return true
        else
          error("true")
        end
      elseif c == 'f' or c == 'F' then
        if s:sub(p, p + 3):lower() == "alse" then
          p = p + 4
          return false
        else
          error("false")
        end
      elseif c == 'n' or c == 'N' then
        if s:sub(p, p + 2):lower() == "ull" then
          p = p + 3
          return nil
        else
          error("null")
        end
      else
        error("unexpected token " .. c)
      end
    end
  end
  local r = V()
  while p <= l do
    c = s:sub(p, p)
    if c == ' ' or c == '\f' or c == '\n' or c == '\r' or c == '\t' or c == '\v' then
    else
      error("unexpected token " .. c)
    end
    p = p + 1
  end
  return r
end

local function stringify(o)
  local l = {}
  local function impl(o)
    if o == nil then
      return "null"
    elseif type(o) == "table" then
      if l[o] then
        error("loop detected")
      end
      l[o] = true
      local s, e
      local a = {}
      if #o > 0 then
        s, e = '[', ']'
        for i = 1, #o do
          table.insert(a, impl(o[i]))
        end
      else
        s, e = '{', '}'
        for k, v in pairs(o) do
          table.insert(a, '"' .. tostring(k) .. '":' .. impl(v))
        end
      end
      table.remove(l)
      return s .. table.concat(a, ',') .. e
    elseif type(o) == "string" then
      local s, l = '"', o:len()
      for i = 1, l do
        local c = o:sub(i, i)
        if c == '"' then
          s = s .. '\\"'
        elseif c == '\\' then
          s = s .. '\\\\'
        elseif c == '\b' then
          s = s .. '\\b'
        elseif c == '\f' then
          s = s .. '\\f'
        elseif c == '\n' then
          s = s .. '\\n'
        elseif c == '\r' then
          s = s .. '\\r'
        elseif c == '\t' then
          s = s .. '\\t'
        elseif c < ' ' then
          s = s .. string.format("\\u%04x", c:byte())
        else
          s = s .. c
        end
      end
      return s .. '"'
    elseif type(o) == "number" then
      return o
    elseif type(o) == "boolean" then
      return o and "true" or "false"
    else
      return '"' .. tostring(o) .. '"'
    end
  end
  return impl(o)
end

JSON = { parse = parse, stringify = stringify }
