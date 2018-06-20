return function (o)
  local l, sp = {}, '    '
  local function impl(o, t)
    if o == nil then
      return nil
    elseif type(o) == "table" then
      for i = 1, #l do
        if o == l[i] then
          return "<!loop!>"
        end
      end
      table.insert(l, o)
      local s, p = { }, t .. sp
      for k, v in pairs(o) do
        table.insert(s, p .. tostring(k) .. '(' .. type(k) .. ')' .. ' = ' .. impl(v, p) .. ',\n')
      end
      table.remove(l)
      return '{\n' .. table.concat(s) .. t .. '}'
    elseif type(o) == "string" then
      local s = '"'
      local l = o:len()
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
      return "<" .. type(o) .. ">"
    end
  end
  return impl(o, "")
end
