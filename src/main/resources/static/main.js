console.log("Main.js V25 Loaded (Always Dashboard)");

var stompClient = null;
var quill = null;

// [智能路由]
const urlParams = new URLSearchParams(window.location.search);
let currentDocId = urlParams.get('docId');

// [修改点]：如果没有 ID，强制去 Dashboard，不再读取 localStorage 里的旧记录
if (!currentDocId) {
    window.location.href = "dashboard.html";
} else {
    localStorage.setItem("last_doc_id", currentDocId);
}

// [Connection ID]
var username = localStorage.getItem("uum_user") || "Anonymous";
var myConnectionId = username + "_" + Math.random().toString(36).substr(2, 6);
console.log("My Connection ID:", myConnectionId);

// --- INITIALIZE QUILL ---
try {
    var ImageResize = window.ImageResize;
    if (ImageResize && typeof ImageResize !== 'function' && ImageResize.default) {
        ImageResize = ImageResize.default;
    }
    if (ImageResize) Quill.register('modules/imageResize', ImageResize);

    quill = new Quill('#editor-container', {
        theme: 'snow',
        modules: {
            toolbar: {
                container: '#toolbar-container',
                handlers: {
                    'image': imageHandler,
                    'attach-file': fileHandler,
                    'link': linkHandler
                }
            },
            imageResize: { displaySize: true, modules: [ 'Resize', 'DisplaySize' ] }
        }
    });
} catch (e) { console.error("Quill Error:", e); }

// --- HANDLERS ---
function linkHandler() {
    var range = quill.getSelection();
    if (range) {
        var value = prompt('Enter link URL:');
        if (value) quill.format('link', value);
    }
}
function imageHandler() { document.getElementById('image-upload').click(); }
function fileHandler() { document.getElementById('file-upload').click(); }

document.getElementById('image-upload').onchange = function() {
    if(this.files[0]) uploadFileToServer(this.files[0], 'image');
};
document.getElementById('file-upload').onchange = function() {
    if(this.files[0]) uploadFileToServer(this.files[0], 'file');
};

function uploadFileToServer(file, type) {
    var formData = new FormData();
    formData.append('file', file);
    fetch('/upload', { method: 'POST', body: formData })
        .then(r => r.json())
        .then(data => {
            if (data.url) {
                var range = quill.getSelection(true);
                var index = range ? range.index : quill.getLength();
                if (type === 'image') quill.insertEmbed(index, 'image', data.url, 'user');
                else quill.insertText(index, "📄 " + data.name, 'link', data.url, 'user');
            }
        });
}

// --- WEBSOCKET LOGIC ---
function connect() {
    var socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function (frame) {
        console.log("Connected to WebSocket");

        // 1. 订阅文档更新
        stompClient.subscribe(`/topic/document/${currentDocId}`, function (msg) {
            var m = JSON.parse(msg.body);
            if(m.sender !== myConnectionId && quill) {
                try {
                    var contentObj = JSON.parse(m.content);
                    if(contentObj.delta) quill.updateContents(contentObj.delta);
                } catch(e) {
                    if(quill.root.innerHTML !== m.content) quill.root.innerHTML = m.content;
                }
            }
        });

        // 2. 订阅历史记录
        stompClient.subscribe('/topic/history/' + myConnectionId, function (msg) {
            console.log("📥 RECEIVED HISTORY!");
            var body = JSON.parse(msg.body);
            var c = body.content;
            if(c) {
                try {
                    var s = JSON.parse(c);
                    quill.setContents(s.fullDoc ? s.fullDoc : s);
                } catch (e) { quill.root.innerHTML = c; }
            }
            if (body.title) {
                document.getElementById("doc-title").innerText = body.title;
                document.title = body.title;
            }
        });

        // 3. 订阅用户数
        stompClient.subscribe('/topic/users', function (msg) {
            var d = document.getElementById("user-count");
            if(d) d.innerHTML = '<i class="fa-solid fa-users"></i> ' + JSON.parse(msg.body).content;
        });

        // 4. 主动询问人数
        stompClient.send('/app/users', {}, {});

        // 5. 发送加入请求
        stompClient.send(`/app/join/${currentDocId}`, {}, JSON.stringify({
            'sender': myConnectionId,
            'docId': currentDocId
        }));
    });
}

// 自动保存监听
var saveTimeout;
if (quill) {
    quill.on('text-change', function(delta, oldDelta, source) {
        if (source === 'user' && stompClient && stompClient.connected) {
            document.getElementById("save-status").innerHTML = '<i class="fa-solid fa-sync fa-spin"></i> Saving...';

            stompClient.send(`/app/edit/${currentDocId}`, {}, JSON.stringify({
                'content': JSON.stringify({ delta: delta, fullDoc: quill.getContents() }),
                'sender': myConnectionId,
                'docId': currentDocId
            }));

            clearTimeout(saveTimeout);
            saveTimeout = setTimeout(function(){
                document.getElementById("save-status").innerHTML = '<i class="fa-solid fa-cloud"></i> Saved to Drive';
            }, 1000);
        }
    });
}

// 手动保存
function manualSave() {
    if (!stompClient || !stompClient.connected) return alert("Offline!");
    stompClient.send(`/app/edit/${currentDocId}`, {}, JSON.stringify({
        'content': JSON.stringify({ fullDoc: quill.getContents() }),
        'sender': myConnectionId,
        'docId': currentDocId
    }));
    var btn = document.querySelector(".btn-save");
    btn.innerHTML = '<i class="fa-solid fa-check"></i> Saved!';
    btn.style.backgroundColor = "#137333";
    setTimeout(() => {
        btn.innerHTML = '<i class="fa-regular fa-floppy-disk"></i> Save';
        btn.style.backgroundColor = "#1a73e8";
    }, 1000);
}

connect();