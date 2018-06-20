
local libprefix, libsubfix

local envvalue = os.getenv( "os");
if nil ~= envvalue then
  local pos = string.find( string.lower( envvalue), "windows")
  if nil ~= pos then
    libprefix = ""
    libsubfix = ".dll"
  else
    print( "error, unknow os :", envvalue)
    return nil
  end
else
  local pf = io.popen( "uname -s")
  envvalue = pf:read( "*l")
  pf:close()
  if nil ~= envvalue then
    local ostype = string.lower( envvalue)
    if nil ~= string.find( ostype, "linux") then
      libprefix = "lib"
      libsubfix = ".so"
    elseif nil ~= string.find( ostype, "darwin") then
      libprefix = "lib"
      libsubfix = ".macos.x86_64.dylib"
    else
      print( "error : unknow os", envvalue)
      return nil
    end
  else
    print( "error, unknow os :", envvalue)
    return nil
  end
end

local libfilename = libprefix .. "limaxlua" .. libsubfix
local limaxlib, msg = package.loadlib( libfilename, "luaopen_limaxcontext")
if nil == limaxlib then
  print( "error, loadlib failed : ", libfilename, msg)
  return nil
end

return limaxlib
