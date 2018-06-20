function $(id) {
    return document.getElementById(id);
}

function exchange(handler, req, cb) {
	if (req) {
	    req['authCode'] = $('authCode').value;
	    $('authCode').value = '';
	}
    var r = new XMLHttpRequest();
    r.open('GET', '/' + handler + '?' + encodeURIComponent(JSON.stringify(req)), true);
    r.onreadystatechange = function() {
        if (r.readyState != r.DONE)
            return;
        if (r.status == 200) {
            cb(handler, JSON.parse(r.responseText));
        }
        r.abort();
    }
    r.onerror = r.onabort = r.ontimeout = r.abort;
    r.send();
}

var updateUsages;
function load() {
    $('reset').disabled = true;
    var current;
    var menus = $('menu').getElementsByTagName('a');
    function selectMenu(target) {
        $(target.text).hidden = false;
        target.className = 'background-red';
        current = target.text;
        for (var i = 0; i < menus.length; i++) {
            if (target != menus[i]) {
                $(menus[i].text).hidden = true;
                menus[i].className = '';
            }
        }
    }
    selectMenu(menus[0]);
    for (var i = 0; i < menus.length; i++)
        menus[i].addEventListener('click', function(e) { selectMenu(e.target); });
    var onselect = function(e) {
        var subjectAltNames = $('subjectAltNames');
        if (e.target.selectedIndex == 0) {
            if (subjectAltNames.childElementCount > 1)
                subjectAltNames.removeChild(e.target.parentElement.parentElement);                        
            return;
        }
        e.target.parentElement.nextElementSibling.firstElementChild.focus();
        for (var i = subjectAltNames.firstElementChild; i != null; i = i.nextElementSibling)
            if(i.firstElementChild.nextElementSibling.firstElementChild.selectedIndex == 0)
                return;
        var elem = subjectAltNames.firstElementChild.cloneNode(true)
        subjectAltNames.appendChild(elem);
        elem.firstElementChild.firstChild.nodeValue = '';
        elem.lastElementChild.firstElementChild.value = '';
        elem.getElementsByTagName('select')[0].addEventListener('change', onselect, {passive : true});
        elem.getElementsByTagName('input')[0].addEventListener('focus', onfocus);
    }
    var onfocus = function(e) {
        e.target.className = 'ok';
    }
    var subjectAltNames = document.getElementById('subjectAltNames');
    subjectAltNames.getElementsByTagName('select')[0].addEventListener('change', onselect, {passive : true});
    subjectAltNames.getElementsByTagName('input')[0].addEventListener('focus', onfocus);
    $('subject').addEventListener('focus', onfocus);
    $('notBefore').addEventListener('focus', onfocus);
    $('notAfter').addEventListener('focus', onfocus);
    $('pkcs12passphrase').addEventListener('focus', onfocus);
    $('pkcs12confirm').addEventListener('focus', onfocus);
    $('pktext').addEventListener('focus', onfocus);
    $('revoketext').addEventListener('focus', onfocus);
    $('recalltext').addEventListener('focus', onfocus);
    $('authCode').addEventListener('focus', onfocus);
    $('action').addEventListener('click', function() { window[current](); });
    $('reset').addEventListener('click', function() { refresh(current); $('reset').disabled=true; } );
    var areas = document.getElementsByTagName('textarea');
    for (var i = 0; i < areas.length; i++) {
        areas[i].addEventListener('input', function(e) {
            e.target.previousElementSibling.value = null
        });
        areas[i].previousElementSibling.addEventListener('change', function(e) {
            var fr = new FileReader();
            fr.onloadend = function() {
                var textarea = e.target.nextElementSibling;
                textarea.value = fr.result;
                textarea.className = 'ok';
            } 
            fr.readAsText(e.target.files[0]);
        });
    }
    $('pkcs12').getElementsByTagName('span')[0].addEventListener('click', function() {
        $('pubkey').hidden = false;
        $('pkcs12').hidden = true;
    });
    $('pubkey').getElementsByTagName('span')[0].addEventListener('click', function() {
        $('pubkey').hidden = true;
        $('pkcs12').hidden = false;
    });
    var usages = {};
    var list = document.getElementsByName('keyUsage');
    for (var i = 0; i < list.length; i++)
        usages[list[i].nextSibling.data] = list[i];
    var list = document.getElementsByName('extKeyUsage');
    for (var i = 0; i < list.length; i++)
        usages[list[i].nextSibling.data] = list[i];
    updateUsages = function(obj) {
        if (obj instanceof Array) {
            for (var i in obj) 
                usages[obj[i]].checked = true;
        } else {
            for (var i in obj) {
                usages[i].disabled = ((obj[i] & 2) != 0);
                usages[i].checked = ((obj[i] & 1) != 0);
            }
        }
    }
    refresh(current);
}

function refresh(tab) {
    $('authCode').value = '';
    if (tab === 'sign') {
        exchange(tab, null, render);
        var subjectAltNames = $('subjectAltNames');
        var items = subjectAltNames.getElementsByClassName('item');
        for (var i = items.length - 1; i > 0; i--)
            subjectAltNames.removeChild(items[i]);
        items[0].getElementsByTagName('select')[0].selectedIndex = 0;
        items[0].getElementsByTagName('input')[0].value = '';
        $('pkfile').value = null;
        $('pktext').value = '';
        $('pkcs12passphrase').value = '';
        $('pkcs12confirm').value = '';
    } else if (tab === 'revoke') {
        $('revokefile').value = null;
        $('revoketext').value = '';
    } else {
        $('recallfile').value = null;
        $('recalltext').value = '';
    }
}

function render(tab, res) {
    if (tab === 'sign') {
        if (res.subject) {
            $('subject').value = res.subject;
        } else if (res.subject === null) {
            $('subject').className = 'err';
        }
        if (res.subjectAltNames) {
            var subjectAltNames = res.subjectAltNames;
            for (var i = $('subjectAltNames').firstElementChild; i != null; i = i.nextElementSibling) {
                var div = i.firstElementChild.nextElementSibling;
                var select = div.firstElementChild; 
                var index = select.selectedIndex;
                if(index == 0)
                    continue;
                var val = subjectAltNames.shift();
                var input = div.nextElementSibling.firstElementChild;
                if (val) {
                    input.value = val;            
                } else {
                    input.className = 'err';
                }
            }
        }
        if (res.notBefore) {
            if (typeof res.notBefore === 'string') {
                $('notBefore').value = res.notBefore;
            } else {
                for (var key in res.notBefore) {
                    $('notBefore').value = key;
                    $('notBefore').disabled = res.notBefore[key];
                }
            }
        } else if (res.notBefore === null) {
            $('notBefore').className = 'err';
        }
        if (res.notAfter) {
            $('notAfter').value = res.notAfter;
        } else if (res.notAfter === null) {
            $('notAfter').className = 'err';
        }
        updateUsages(res.keyUsage);
        updateUsages(res.extKeyUsage);
        if ($('pubkey').hidden) {
        } else {
            if (res.publicKey === null) {
                $('pktext').className = 'err';
            }
        }
        if (res.retrieveKey) {
            $('retrieveKey').href = '/' + res.retrieveKey;
            var event;
            try {
                event = new MouseEvent('click', {
                    'view' : window,
                    'bubbles' : true,
                    'cancelable':  true
                });
            } catch(e) {
                event = document.createEvent('HTMLEvents');
                event.initEvent('click', true, true);        
            }
            $('retrieveKey').dispatchEvent(event);
            $('reset').disabled = false;
        }
    } else if (tab === 'revoke') {
        if (res.certificate === null) {
            $('revoketext').className = 'err';
        }        
        if (res.result) {
            window.alert((res.result.status ? 'OK\n' : 'ERROR\n') + res.result.message);
            $('reset').disabled = false;
        }
    } else {
        if (res.certificate === null) {
            $('recalltext').className = 'err';
        }        
        if (res.result) {
            window.alert((res.result.status ? 'OK\n' : 'ERROR\n') + res.result.message);
            $('reset').disabled = false;
        }
    }
    if (res.authCode === null) {
        $('authCode').value = '';
        $('authCode').className = 'err';
    }
}

function sign() {
    var req = {};
    req['subject'] = $('subject').value;
    var subjectAltNames = [];
    for (var i = $('subjectAltNames').firstElementChild; i != null; i = i.nextElementSibling) {
        var div = i.firstElementChild.nextElementSibling;
        var select = div.firstElementChild; 
        var index = select.selectedIndex;
        if(index == 0)
            continue;
        var item = {};
        item[select.children[index].value] = div.nextElementSibling.firstElementChild.value;
        subjectAltNames.push(item);
    }
    req['subjectAltNames'] = subjectAltNames;
    req['notBefore'] = $('notBefore').value;
    req['notAfter'] = $('notAfter').value;
    var keyUsage = [];
    var list = document.getElementsByName('keyUsage');
    for (var i = 0; i < list.length; i++)
        if (list[i].checked)
            keyUsage.push(list[i].nextSibling.data);
    req['keyUsage'] = keyUsage;
    var extKeyUsage = [];
    var list = document.getElementsByName('extKeyUsage');
    for (var i = 0; i < list.length; i++)
        if (list[i].checked)
            extKeyUsage.push(list[i].nextSibling.data);
    req['extKeyUsage'] = extKeyUsage;
    if ($('pubkey').hidden) {
        var pkcs12passphrase = $('pkcs12passphrase');
        if (pkcs12passphrase.value.length < 6) {
            pkcs12passphrase.className = 'err';
            return;
        }
        var pkcs12confirm = $('pkcs12confirm');
        if (pkcs12passphrase.value != pkcs12confirm.value) {
            pkcs12confirm.className = 'err';
            return;
        }
        req['pkcs12passphrase'] = pkcs12passphrase.value;
    } else {
        var pktext = $('pktext');
        req['publicKey'] = pktext.value;
    }
    exchange('sign', req, render);
}

function revoke() {
    exchange('revoke', {
        certificate : revoketext.value
    }, render);
}

function recall() {
    exchange('recall', {
        certificate : recalltext.value
    }, render);
}
