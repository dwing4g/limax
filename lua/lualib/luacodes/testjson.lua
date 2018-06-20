require "json"

local s, o = pcall(JSON.parse ,'  { "abc" : "d\tef" , "xx" : [100.2,true,-30e-2,"c\r\n", false, { "0" : []} ], "null" : [null, 1,null,2]}');
print(o.abc)
if s then
  print(JSON.stringify(o))
else
  print("err")
end

local a = {}
local b = {}
local c = {1}
a['x'] = b
a.y = c
a.z = c
b[1] = 100
b[2] = JSON.null

print(pcall(JSON.stringify, a))
b[3] = a
print(pcall(JSON.stringify, a))

local dump =require 'dump'
local x = {}
table.insert(x, 'a')
local y = { x = 1, y = 2, [3] = 3,  x}
table.insert(y, 100)
print (dump(y))
table.insert(y, 200)
print (dump(y))
table.insert(y, nil)
print (dump(y))
table.remove(y)
print (dump(y))
table.remove(y)
print (dump(y))

local s = "{\"a\":10} [2,3]5[12]"
print (dump(JSON.parse(s)))