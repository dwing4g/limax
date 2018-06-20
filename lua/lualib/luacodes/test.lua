
local function initialize101(ctx)
  local type = {[0] = 'NEW', [1] = 'REPLACE', [2] = 'TOUCH', [3] = 'DELETE'}
  local v101 = ctx[101]
  v101.users.UserView.onchange = function(e)
    print("v101.users.UserView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
end

local function initialize102(ctx)
  local type = {[0] = 'NEW', [1] = 'REPLACE', [2] = 'TOUCH', [3] = 'DELETE'}
  local v102 = ctx[102]
  v102.roles.role.RoleView.onchange = function(e)
    print("v102.roles.role.RoleView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.building.BuildingView.onchange = function(e)
    print("v102.roles.building.BuildingView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.building.ArmyView.onchange = function(e)
    print("v102.roles.building.ArmyView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.item.BagView.onchange = function(e)
    print("v102.roles.item.BagView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.attr.RoleAttr.onchange = function(e)
    print("v102.roles.attr.RoleAttr.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
  v102.roles.move.MoveView.onchange = function(e)
    print("v102.roles.move.MoveView.onchange", e.view, e.sessionid, e.fieldname, e.value, type[e.type]);
  end
end

local function main()
  local Limax = require "limax"
  local callback101 = function(ctx)
    ctx.onerror = function(e)
      print('limax error', tostring(e))
    end
    ctx.onclose = function(e)
      print('limax close', tostring(e));
    end
    ctx.onopen = function()
      xpcall(
        function ()
          initialize101(ctx)
        end,
        function (msg)
          print('LUA ERROR: ', tostring(msg), '\n')
          print(debug.traceback())
        end)
    end
  end
  local callback102 = function(ctx)
    ctx.onerror = function(e)
      print('limax error', tostring(e))
    end
    ctx.onclose = function(e)
      print('limax close', tostring(e));
    end
    ctx.onopen = function()
      xpcall(
        function ()
          initialize102(ctx)
        end,
        function (msg)
          print('LUA ERROR: ', tostring(msg), '\n')
          print(debug.traceback())
        end)
    end
  end
  local limax = Limax(callback101)
  local limax2 = Limax(callback102)

  print("limax1", limax( 0, function( msg) print( "script send mesage : ", msg) end))
  print("limax2", limax2( 0, function( msg) print( "script send mesage : ", msg) end))
  --limax( 1, "I0:I0:I6bk:I1:LI2u:Slb:roles,login,role,building,item,attr,move,CreateRoleView,GlobalLogin,RoleView,BuildingView,BagView,RoleAttr,MoveView,name,properties,buildings,capacity,food,items,srolemove,ssetrolelocation,sadduserscreen,sremoveuserscreen,snpcmoveto,sroleplayaction,supdaterolescenestate,sroleenterscene,npckey,speed,destpos,x,y,roleid,actionid,cuizi1,cuizi2,buildingMap,ownerName,destPos,destz,changetype,sceneID,weatherId,tipsParm,status,cuiziNo,level,finishtime,makeArmyTotalNumber,lastLevelUpTime,lastCollectTime,rolename,userid,exp,allexp,school,sex,shape,createtime,onlinetime,offlinetime,hp,mp,phy,maxPhy,lastRecoverPhyTime,power,castlePosition,rolelist,npclist,Y,rolebasicOctets,pos,posz,poses,id,dir,components,position,locz,roleids,npcids,uniqueId,number,roleId,scenestate,aMI0:LI0:I1:I7::I1:LI0:I1:I8::I2:LI0:I2:I9::I3:LI0:I3:Ia::I4:LI0:I4:Ib::I5:LI0:I5:Ic::I6:LI0:I6:Id::::")
  --limax( 1, "I2u:I0:Il:I1:P?e?S3:ddd:PI6bk:U:")
  --limax( 1,"I0:I0:Icn4:I-1:LIc:Sdy:gs,for_session,for_temp,firstview,globalview,TestTempView,var1,var2,var3,var4,bindfirst,bindsecond,bindt4cache,bindt3,bindkeyisxcompare2,bindany,bindt4cachevarmarshal,var5,_var1,_var2,_var3,_var4,_bindfirst,_bindsecond,_bindt4cache,_bindt3,_bindkeyisxcompare2,s,sets,boolv,varmarshal,varbool,varbyte,varint,varlong,varshort,var6,setfirst,listfirst,vectorfirst,mapfirst,mapxfirst,first,i,varmarshal2,l,seti,text,setl,cacheb1,cacheb2,varfloat,varlist,varset,vartext,varoctets,varmap,varvector,xc1,xc2,f,bMI0:LI0:I1:I3::I1:LI0:I4::I2:LI0:I5::I3:LI0:I2:I5::::")
  --limax( 1,"Ic:I0:I0:I0:P?6?I0:?9?I2s:?b?L?11?LI-ocbd1p:I-oez9eb:I-oezfrm:I-oe79km:I-oe2gd6:I-ohp38s:I-of0jz4:I-odzaij:I-odrnao::?12?L:?13?L:?14?MI-ocbd1p:S17:firstview.bindsecond.mapfirst 1428752235827I-oez9eb:S17:firstview.bindsecond.mapfirst 1428747761581I-oezfrm:S17:firstview.bindsecond.mapfirst 1428747753326I-oe79km:S17:firstview.bindsecond.mapfirst 1428749067722I-oe2gd6:S17:firstview.bindsecond.mapfirst 1428749292198I-ohp38s:S17:firstview.bindsecond.mapfirst 1428743197268I-of0jz4:S17:firstview.bindsecond.mapfirst 1428747701216I-odzaij:S17:firstview.bindsecond.mapfirst 1428749439749I-odrnao:S17:firstview.bindsecond.mapfirst 1428749796432:?15?M:?16?L?r?I0:?17?I0:?19?I0:?1b?S0:?u?O0:?s?L:?1a?L:?1c?L::?17?I0:?18?O0:::P:")
  --limax( 1,"Ic:I3:Iu:I1:P:PIcn4:?i?I0:Icn4:?l?I2s:Icn4:?n?L?11?LI-ocbd1p:I-oez9eb:I-oezfrm:I-oe79km:I-oe2gd6:I-ohp38s:I-of0jz4:I-odzaij:I-odrnao::?12?L:?13?L:?14?MI-ocbd1p:S17:firstview.bindsecond.mapfirst 1428752235827I-oez9eb:S17:firstview.bindsecond.mapfirst 1428747761581I-oezfrm:S17:firstview.bindsecond.mapfirst 1428747753326I-oe79km:S17:firstview.bindsecond.mapfirst 1428749067722I-oe2gd6:S17:firstview.bindsecond.mapfirst 1428749292198I-ohp38s:S17:firstview.bindsecond.mapfirst 1428743197268I-of0jz4:S17:firstview.bindsecond.mapfirst 1428747701216I-odzaij:S17:firstview.bindsecond.mapfirst 1428749439749I-odrnao:S17:firstview.bindsecond.mapfirst 1428749796432:?15?M:?16?L?r?I0:?17?I0:?19?I0:?1b?S0:?u?O0:?s?L:?1a?L:?1c?L::?17?I0:?18?O0:::")
  --limax( 1,"Ic:I0:I0:I0:P?8?L?w?I0:?x?I3:?y?I0:?z?I0:?1f?F0.0:?1g?L:?1h?L:?1i?S0:?1j?O0:?1k?M:?1l?L::?9?D?b?WY11:PI-oc9e7h::PI-ocbd1p::Z14:MI-oc9e7h:S17:firstview.bindsecond.mapfirst 1428752327635:PI-ocbd1p::::P:")
  --limax( 1,"Ic:I3:Iu:I2:P:PIcn4:?k?L?w?I0:?x?I3:?y?I0:?z?I0:?1f?F0.0:?1g?L:?1h?L:?1i?S0:?1j?O0:?1k?M:?1l?L::Icn4:?l?DIcn4:?n?WY11:PI-oc9e7h::PI-ocbd1p::Z14:MI-oc9e7h:S17:firstview.bindsecond.mapfirst 1428752327635:PI-ocbd1p::::")

  print("limax1", limax(1, "I0:I0:I188w:I0:LI2t:S29:users,UserView,lasterror,loginconfig,readyFlag,code,msg,username,token,platflag,bMI0:LI0:I1::::"))
  print( "limax1", limax(2, nil))
  print("limax1", limax(1, "I2t:I0:I0:I0:P?4?BT:P:"))
  print("limax2", limax2( 1, "I0:I0:I188w:I0:LI2u:Sfz:roles,role,building,item,attr,move,RoleView,BuildingView,ArmyView,BagView,RoleAttr,MoveView,properties,createRoleFlag,buildings,armys,capacity,food,items,sadduserscreen,x,y,cuizi1,cuizi2,buildingMap,npclist,armyMap,battleArmyMap,woundedArmyMap,rolename,userid,exp,allexp,level,school,sex,camp,shape,createtime,onlinetime,offlinetime,hp,mp,phy,maxPhy,lastRecoverPhyTime,power,castlePosition,sceneId,castleNpcKey,status,cuiziNo,finishtime,makeArmyTotalNumber,lastLevelUpTime,lastCollectTime,uniqueId,id,position,number,npckey,name,pos,posz,destpos,speed,dir,state,armyCofnigIdsMI0:LI0:I1:I6::I1:LI0:I2:I7::I2:LI0:I2:I8::I3:LI0:I3:I9::I4:LI0:I4:Ia::I5:LI0:I5:Ib::::"))

end

local function error_traceback(msg)
  print("----------------------------------------")
  print("LUA ERROR: ", tostring(msg), "\n")
  print(debug.traceback())
  print("----------------------------------------")
end

xpcall( main, error_traceback)
