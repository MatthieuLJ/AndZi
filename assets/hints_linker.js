function showTargetFunctionGenerator(num) {
    return function(e) {
        $('#target_help').html(hints[num]);
    };
}

$(document).ready(function() {
    var i;
    for (i=0; i<9; i++) {
        hints[i] = '<img src="'+(i+1)+'.png" height="20"/>'+hints[i];
        $('#hint'+(i+1)).click(showTargetFunctionGenerator(i));
    }
});
