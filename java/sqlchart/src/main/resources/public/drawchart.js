

function drawChart(renderTo, myclick, sqlChartData, cond, meta, sqlChartType) {
	var MAX_PIESLICES = 16;

	var ascounter = (sqlChartType == "COUNTER_LINE" || sqlChartType == "COUNTER_RATE_LINE");
	var asrate = (sqlChartType == "RATE_LINE" || sqlChartType == "COUNTER_RATE_LINE");
	var chartType = "line";
	if (sqlChartType == "COLUMN")
	    chartType = "column";
	else if (sqlChartType == "PIE")
	    chartType = "pie";

	var hasSepKey = cond.keys.length > 1.
	var key = cond.keys[0];
	var isStacking = hasSepKey;
	var table = _.find(meta.tables, function(t) { return t.table == cond.table; });
	var columnType = {};
	_.each(table.columns, function(c) { columnType[c.col] = c.type; });
	var isTimeSeries = columnType[key] == "TIMESTAMP";

	var chartData = {};
	if (cond.values.length > 1){
	    var dataList = [];
	    for (var i=0; i<cond.values.length; i++){
	        dataList.push({});
	    }

	    $.each(sqlChartData.result, function(k, r) {
	        var kk = isTimeSeries ? new Date(r[0]).getTime() : r[0];
	        for (var i=0; i<cond.values.length; i++){
                dataList[i][kk] = r[1+i];
            }
	    });

	    for (var i=0; i<cond.values.length; i++){
	        chartData[cond.values[i]] = dataList[i];
	    }
	}else if (hasSepKey){
	     $.each(sqlChartData.result, function(i, r) {
            var sk = ''+r[1];
            var res = chartData[sk];
            if (!res){
                res = {};
                chartData[sk] = res;
            }
            var kk = isTimeSeries ? new Date(r[0]).getTime() : r[0];
            res[kk] = r[2];
        });
	}else{
	    var res = {};
	    $.each(sqlChartData.result, function(i, r) {
	    	var kk = isTimeSeries ? new Date(r[0]).getTime() : r[0];
            res[kk] = r[1];
        });
	    chartData[cond.values[0]] = res;
	}

	Highcharts.setOptions({
		global: {
			useUTC: false
		}
	});
	
	function color(index){
		var colors = [ '#4572A7', '#AA4643', '#89A54E', '#80699B', '#3D96AE', '#DB843D', '#92A8CD', '#A47D7C', '#B5CA92' ];
		return colors[index%colors.length];
	}
	
	function local(table){
		return {
			table : function(){
				try{
				    return _.find(meta.descriptors, function(d){ return d.table == table}).desc;
				}catch(e){
					return table;
				} 
			},
			key: function(key){
			    try{
                    var d = _.find(meta.descriptors, function(d){ return d.table == table});
                    return _.find(d.keys, function(kd){kd.col == key}).desc;
				}catch(e){
					return key;
				}
			},
			keysep: function(key, sep) {
				try{
				    var d = _.find(meta.descriptors, function(d){ return d.table == table});
                    var kd = _.find(d.keys, function(kd){kd.col == key});
                    var s = _.find(kd.seps, function(s){ s.value == sep});
                    if (s != null){
                        return s.desc;
                    }else{
                        var kd = _.find(meta.keyDescs, function(kd){ return kd.col == s.base});
                        var s = _.find(kd.seps, function(s){ s.value == sep});
                        if (s != null){
                            return s.desc;
                        }else{
                            return sep;
                        }
                    }
                }catch(e){
					return sep;
				}
			},

			value: function(val) {
				try{
					 var d = _.find(meta.descriptors, function(d){ return d.table == table});
                     return _.find(d.values, function(kd){kd.col == key}).desc;
				}catch(e){
					return val;
				}
			}
		};
	}
	
	
	// 
	var title = local(cond.table).table();
	if (hasSepKey) {
		title += '.' + local(cond.table).value(cond.values[0]);
		title += '/' + local(cond.table).key(cond.keys[1]);
	}
	
	// 
	var subtitle = '';
	for ( var key in cond.filters) {
		var v = cond.filters[key];
		v = local(cond.table).keysep(key, v);
		key = local(cond.table).key(key);
		subtitle += key + '=' + v + ',';
	}
	if (subtitle.length > 0) {
		subtitle = subtitle.substring(0, subtitle.length - 1);
	}
	
	var xtitle = local(cond.table).key(cond.keys[0]);
	var ytitle = hasSepKey ? local(cond.table).value(cond.values[0]) : null;
	
	
	window['SAVE_' + renderTo] = function(){
		var c = document.getElementById("export");
		var svg = $('#' +  renderTo + ' > .highcharts-container').html().replace(/>\s+/g, ">").replace(/\s+</g, "<");
		canvg(c, svg, { renderCallback: function(){
    		var image = c.toDataURL("image/png").replace("image/png", "image/octet-stream");
			window.location.href = image;
    	}, ignoreMouse: true, ignoreAnimation: true });
	};

	var linePlotOpts = {
		fillOpacity : 0.3,
		marker : {
			enabled : false,
			states : {
				hover : {
					enabled : true,
					radius : 4
				}
			}
		},
		shadow : false,
	};
	
	var options = {
		chart : {
			renderTo : renderTo,
			type : chartType,
			zoomType : 'xy'
		},
		credits : {
			href : 'javascript:SAVE_' + renderTo + '()',
			text : '@' + Highcharts.dateFormat('%Y/%m/%e %H:%M', Date.now())
		},
		title : {
			text : title
		},

		subtitle : {
			text : subtitle
		},
		xAxis : {
			tickLength : 0,
			allowDecimals : false,
			title : {
				text : xtitle
			}
		},
		yAxis : {
			min : 0,
			allowDecimals : asrate,
			title : {
				text : ytitle
			}
		},

		plotOptions : {
			column : {
					groupPadding : 0.05,
					shadow : false,
					borderWidth : 0,
			},
			pie : {
				allowPointSelect : true,
				dataLabels : {
					enabled : true,
					formatter : function() {
						return '<b>' + this.point.name + '</b>: ' + Highcharts.numberFormat(this.y, 0) + ' - ' + ('' + this.percentage).substring(0, 4) + ' %';
					}
				},
			},
			area : linePlotOpts,
			line : linePlotOpts,
			spline: linePlotOpts,
			areaspline: linePlotOpts,
		},
		
		exporting: {
			enabled: false
		}
	};
	
	function setPointClick( clk ){
		var point = {
			events : {
				click : clk
			}
		};
		options.plotOptions.pie.point = point;
		options.plotOptions.column.point = point;
		options.plotOptions.area.point = point;
		options.plotOptions.line.point = point;
		options.plotOptions.spline.point = point;
		options.plotOptions.areaspline.point = point;
	}


	if (isTimeSeries) {
		options.xAxis.type = 'datetime';
		options.xAxis.dateTimeLabelFormats = {
			hour : '%H:%M',
			day : '%m/%e',
			week : '%m/%e',
			month : '%Y/%m',
			year : '%Y'
		};
	}
	
	if (isStacking) {
		options.plotOptions.series = {
			stacking : 'normal'
		};
	}
	

	//data
	var keySequences = [];
	options.series = [];
	
	if (chartType === 'pie') { 
		var data = [];
		$.each(chartData, function(kk, vv) {
			$.each(vv, function(k, v) {
				if (v > 0) {
					keySequences.push(k);
					data.push([ ''+ local(cond.table).keysep(cond.keys[0], k), v ]);
				}
			});
		});
		
		options.series.push( {data : data} );

	} else {
		
		var keyset = {};
		$.each(chartData, function(kk, vv) {
			$.each(vv, function(k, v) {
				keyset[k] = 1;
			});
		});
		$.each(keyset, function(k) {
			keySequences.push(k);
		});
		keySequences.sort(function(a, b) {
			return a - b;
		});
		
		
		if (chartType === 'column') {
			var cats = [];
			var len = 0;
			$.each(keySequences, function(i, key) {
				var cat = ''+local(cond.table).keysep(cond.keys[0], key);
				len += cat.length;
				cats.push(cat);
			});
			options.xAxis.categories = cats;
			options.xAxis.tickInterval = Math.ceil( cats.length / ($('#'+renderTo).innerWidth() / (len/cats.length * 10 * 1.8)) );
			
			$.each(chartData, function(kk, vv) {
				var data = [];
				$.each(keySequences, function(i, key) {
					data.push(vv[key] || 0);
				});
				options.series.push( {name : kk, data : data} );
			});

		} else { // line
			
			$.each(chartData, function(kk, vv) {
				var series = {name : kk, data : []};
				
				if (ascounter || asrate){
					var lastv = undefined;
					var lastk = undefined;
					series.step = "right";
					$.each(keySequences, function(i, key) {
						var thisv = vv[key];
						if (thisv !== undefined){
							if ( lastv !== undefined && thisv - lastv >= 0){
								var _denominator = asrate ? (key - lastk)  : 1;
								var _numerator = ascounter? thisv - lastv : thisv;
								series.data.push([ key, _numerator / _denominator ]);
							}
							lastv = thisv;
							lastk = key;
						}else{
							series.data.push([ key, 0 ]);
							lastv = undefined;
						}
					});
				}else{
					$.each(keySequences, function(i, key) {
						series.data.push([ key, vv[key] || 0 ]);
					});
				}
				
				options.series.push(series);
			});

			if (isStacking) {
				options.chart.type = 'area';
				options.series.sort(function(a, b) {
					return (+b.name) - (+a.name);
				});
			}
		}
		
		
		if (hasSepKey) {
			$.each(options.series, function(idx, s) {
				s.name = local(cond.table).keysep(cond.keys[1], s.name);
			});
		} else {
			$.each(options.series, function(idx, s) {
				s.name = local(cond.table).value(s.name);
			});
		}
		
		if (ascounter || asrate){
			$.each(options.series, function(idx, s) {
				if (ascounter)
					s.name += " grow";
				if (asrate)
					s.name += " rate";
			});
		}
	}
	
		
	// ////////////////////////////////////////
	
	setPointClick( function(e) {
		var kv;
		if (chartType === 'line'){
			kv = this.x;
		}else{
			kv = keySequences[this.x];
		}
		if (kv != undefined)
			myclick(cond.keys[0], kv);
	});


	if (chartType === 'pie') {
		options.tooltip = {
			formatter : function() {
				return '<b>' + this.point.name + '</b>: ' + Highcharts.numberFormat(this.y, 0) + ' - ' + ('' + this.percentage).substring(0, 4) + ' %';
			}
		};
	} else {
		options.tooltip = {
			shared : true,
			formatter : function() {
				var s = '<b>' + this.x;

				var decimals = asrate ? 2 : 0;
				var first = true;
				$.each(this.points, function(i, point) {
					s += first && isStacking ? ': ' + Highcharts.numberFormat(point.total, decimals) + '</b>' : '</b>';
					first = false;
					s += '<br/>' + point.series.name + ': ' + Highcharts.numberFormat(point.y, decimals);
				});

				return s;
			}
		};
	}
	
	
	if (options.xAxis.categories){
		var orig_tickInterval = options.xAxis.tickInterval;
		options.chart.events =  {
            selection: function(e){
                var ex = this.xAxis[0].getExtremes();
                if (e.resetSelection){
                    this.xAxis[0].options.tickInterval = orig_tickInterval;
                }else{
                    var w = e.xAxis[0].max - e.xAxis[0].min;
                    this.xAxis[0].options.tickInterval = Math.ceil( w / orig_width * orig_tickInterval );
                }
            }
        };
	}
	
	var thischart = new Highcharts.Chart(options);
	if (options.xAxis.categories){
		var ex = thischart.xAxis[0].getExtremes();
		var orig_width = ex.max - ex.min;
	}
	
}
