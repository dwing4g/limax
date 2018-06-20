$(function(){
    $(document).ajaxComplete(function(e, xhr, settings){
        if (xhr.responseText.length < 128)
		    $("#dashboard-request-res").text(xhr.status + " " + xhr.statusText + " " + xhr.responseText);
	});

    var sepSchema = {type: 'object',
        properties: {
            value: {type: 'integer', title: 'value'},
            desc: {type: 'string', title: 'desc'}
        }
    };

    var keyDescSchema = { type: 'object',
        properties: {
            col: { type: 'string', title: 'col'},
            desc: { type: 'string', title: 'desc'},
            base: { type: 'string', title: 'base'},
            seps: { type: 'array', items: sepSchema, title: 'seps' }
        }
    };

    var tableSchema = { type: 'object',
        properties: {
            table: { type: 'string', title: 'table'},
            exist: { type: 'boolean', title: 'exist'},
            keys: { type: 'array', items: {type: 'string'}, title: 'keys' },
            columns: { type: 'array', title: 'columns',
                items: { type: 'object',
                    properties: {
                        col: { type: 'string', title: 'col'},
                        type: { type: 'string', title: 'type', enum: ['INT', 'STRING', 'TIMESTAMP']}
                    }
                }
            }
        }
    };

    var valueDescSchema = {type: 'object',
        properties: {
            col: {type: 'string', title: 'col'},
            desc: {type: 'string', title: 'desc'}
        }
    };

    var descriptorSchema = { type: 'object',
        properties: {
            table: {type: 'string', title: 'table'},
            desc: {type: 'string', title: 'desc'},
            keys: { type: 'array', items: keyDescSchema, title: 'keys'},
            values:  { type: 'array', items: valueDescSchema, title: 'values'}
        }
    };

    var chartSchema = { type: 'object',
        properties: {
            type: { type: 'string', title: 'type', enum: ['COLUMN', 'PIE', 'LINE', 'COUNTER_LINE', 'RATE_LINE', 'COUNTER_RATE_LINE']},
            keys: { type: 'array', items: { type: 'string' }, title: 'keys' },
            values: { type: 'array', items: { type: 'string' }, title: 'values'}
        }
    };

    var chartSeriesSchema = { type: 'object',
        properties: {
            name: {type: 'string', title: 'name'},
            table: {type: 'string', title: 'table'},
            filters: { type: 'array', items: valueDescSchema, title: 'filters'},
            charts:  { type: 'array', items: chartSchema, title: 'charts'}
        }
    };

    var dataSourceSchema = {
       name: { type: 'string', title: 'name' },
       url: { type: 'string', title: 'url' },
       poolSize: { type: 'integer', title: 'poolSize', minimum: 1 },
       alive: { type: 'boolean', title: 'alive' },
       tables: { type: 'array', items: tableSchema, title: 'tables' },
       keyDescs: { type: 'array', items: keyDescSchema, title: 'keyDescs' },
       descriptors: { type: 'array', items: descriptorSchema, title: 'descriptors' },
       chartSeriess: { type: 'array', items: chartSeriesSchema, title: 'chartSeriess' },
    };

    var dataSourceForm = [
        { key: 'name'},
        { key: 'url'},
        { key: 'poolSize'},
        { key: 'alive', disabled: true},
        { key: 'tables', type: "tabarray",
            items: {
                type: "section",
                legend: "{{value}}",
                items: [
                    { key: "tables[].table", valueInLegend: true },
                    { key: "tables[].exist", disabled: true },
                    { key: "tables[].keys",  type: "tabarray" },
                    { key: "tables[].columns", type: "tabarray", disabled: true, readonly: true }
                ]
            }
        },
        { key: 'keyDescs', type: "tabarray",
            items: {
                type: "section",
                legend: "{{value}}",
                items: [
                    { key: "keyDescs[].col", valueInLegend: true },
                    { key: "keyDescs[].desc" },
                    { key: "keyDescs[].base" },
                    { key: "keyDescs[].seps", type: "tabarray" }
                ]
            }
        },

        { key: 'descriptors', type: "tabarray",
            items: {
                type: "section",
                legend: "{{value}}",
                items: [
                    { key: "descriptors[].table", valueInLegend: true },
                    { key: "descriptors[].desc" },
                    { key: "descriptors[].keys", type: "tabarray" },
                    { key: "descriptors[].values", type: "tabarray" }
                ]
            }
        },
        { key: 'chartSeriess', type: "tabarray",
            items: {
                type: "section",
                legend: "{{value}}",
                items: [
                    { key: "chartSeriess[].name", valueInLegend: true },
                    { key: "chartSeriess[].table" },
                    { key: "chartSeriess[].filters", type: "tabarray" },
                    { key: "chartSeriess[].charts", type: "tabarray" }
                ]
            }
        },
        {
            type: 'submit',
            title:'submit'
        }
    ];

    var template_sidebar = Handlebars.compile($("#template-sidebar").html());
    var template_meta = Handlebars.compile($("#template-meta").html());
    var template_setting = Handlebars.compile($("#template-setting").html());
    var template_chart = Handlebars.compile($("#template-chart").html());

    var meta;
    var metaText;

    function settingClick(){
        $("#dashboard").html( template_setting(meta) );
        $("#meta").click(function(){
            metaClick();
        });
        $('#setting-form').jsonForm({
            schema: dataSourceSchema,
            form: dataSourceForm,
            value: meta,
            onSubmit: function (errors, values) {
                if (errors) {
                } else {
                    $.ajax({
                        type: "put",
                        url : "/datasource",
                        data: JSON.stringify(values),
                        dataType: 'text'
                    }).done(function(){
                        location.reload();
                    });
                }
            }
        });
    }

    function metaClick(){
        $("#dashboard").html( template_meta(meta) );
        $("#setting").click(function(){
            settingClick();
        });

        var editor = ace.edit("editor");
        editor.setTheme("ace/theme/monokai");
        editor.getSession().setMode("ace/mode/json");
        editor.setValue(metaText);

        $('#editor-submit').click( function() {
            $.ajax({
                type: "put",
                url : "/datasource",
                data: editor.getValue(),
                dataType: 'text'
            }).done(function(){
                location.reload();
            });
        });
    }




    var MAXLVL = 6;
    var CHARTSERIES;
    var FILTERS;

	function vanishLevel(lvl) {
		for ( var i = lvl; i <= MAXLVL; i++) {
			$('#action' + i).html('');
			$('#holder' + i).html('');
		}
	}

	function queryClick( lvl ) {
        var maxlvl = CHARTSERIES.charts.length;
        if (lvl >= maxlvl) {
            return;
        }

        vanishLevel(lvl + 1);

        // where
        var filters = _.clone(FILTERS);
        for ( var i = 1; i <= lvl; i++) {
            var kn = $('#action' + i + ' .kn').val();
            var kv = +($('#action' + i + ' .kv').val());
            filters[kn] = kv;
        }

        var chart = CHARTSERIES.charts[lvl];
        var q = {table: CHARTSERIES.table, filters: filters, keys: chart.keys, values: chart.values};
        $.ajax({
            type: "GET",
            url : "/query" + location.search + "&q=" + JSON.stringify(q)
        }).done(function( msg, status, xhr ) {
            //console.log(xhr.responseText);
            function myclick(kn, kv) {
                vanishLevel(lvl + 1);
                $('#action' + (lvl + 1)).html('<input class="kn hide" value="' + kn + '"/><input class="kv hide" value="' + kv + '"/>');
                queryClick(lvl + 1);
            }
            drawChart("holder"+lvl, myclick, msg, q, meta, chart.type);
        });
    }


    function chartSeriesClick(name) {
        CHARTSERIES = _.find(meta.chartSeriess, function(c){ return c.name == name; });
        $("#dashboard").html( template_chart(CHARTSERIES) );
        var schema = {};
        var value = {};
        _.each(CHARTSERIES.filters, function(v) { schema[v.col] = {type: "string", title: v.col}; } );
        _.each(CHARTSERIES.filters, function(v) { value[v.col] = v.desc; })

        $("#filter-form").jsonForm({
            schema: schema,
            form: ["*", {type: "submit", title: "submit"}],
            value: value,
            onSubmit: function (errors, values) {
                if (errors){
                }else{
                    FILTERS = values;
                    queryClick(0);
                }
            }
        });

        queryClick(0);
    }

    $.ajax({
        type: "GET",
        url : "/meta" + location.search
    }).done(function( msg, status, xhr ) {
        meta = msg;
        metaText = xhr.responseText;
        $("#sidebar-wrapper").html( template_sidebar(meta) );
        $("#sidebar-meta").click( metaClick);
        $(".sidebar-chart").each( function(){
            $(this).click(function() {
                chartSeriesClick($(this).text())
            });
        });
    });





})
