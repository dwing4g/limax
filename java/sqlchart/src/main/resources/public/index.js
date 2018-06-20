$(function(){
    $.ajax({
        type: "GET",
        url : "/datasource",
    }).done(function( msg ) {
        var template= Handlebars.compile($("#header").html());
        $("#target").html( template(msg) );
    });

   $("#add").click( function() {
   		$.ajax({
   			type: "POST",
   			url : "/datasource?ds=" + $("#ds").val(),
   		}).done( function(){
   		    location.reload();
   		});
   	});
    $("#delete").click( function() {
        $.ajax({
            type: "DELETE",
            url : "/datasource?ds=" + $("#ds").val(),
        }).done( function(){
            location.reload();
        });
    });
})