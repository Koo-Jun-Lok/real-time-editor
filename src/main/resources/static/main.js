console.log("Main.js V26 Loaded (Enhanced Features + English Comments)");

var stompClient = null;
var quill = null;

// [Smart Routing]
const urlParams = new URLSearchParams(window.location.search);
let currentDocId = urlParams.get('docId');

// [Modification]: If no ID is found, force redirect to Dashboard.
// We no longer redirect to the last open document from localStorage to ensure flow starts at Dashboard.
if (!currentDocId) {
    window.location.href = "dashboard.html";
} else {
    localStorage.setItem("last_doc_id", currentDocId);
}

// [Connection ID]
// Generate a unique ID for this tab/window to prevent "echo" (self-update) issues.
var username = localStorage.getItem("uum_user") || "Anonymous";
var myConnectionId = username + "_" + Math.random().toString(36).substr(2, 6);
console.log("My Connection ID:", myConnectionId);

// --- INITIALIZE QUILL ---
try {
    // Check if ImageResize module is available
    var ImageResize = window.ImageResize;
    if (ImageResize && typeof ImageResize !== 'function' && ImageResize.default) {
        ImageResize = ImageResize.default;
    }
    if (ImageResize) Quill.register('modules/imageResize', ImageResize);

    // Initialize Quill Editor with custom toolbar handlers
    quill = new Quill('#editor-container', {
        theme: 'snow',
        modules: {
            toolbar: {
                container: '#toolbar-container',
                handlers: {
                    'image': imageHandler,
                    'attach-file': fileHandler, // Custom handler for the paperclip button
                    'link': linkHandler         // Custom handler for better link insertion
                }
            },
            imageResize: { displaySize: true, modules: [ 'Resize', 'DisplaySize' ] }
        }
    });
} catch (e) { console.error("Quill Error:", e); }

// --- HANDLERS ---

// 1. Enhanced Link Handler
function linkHandler() {
    var range = quill.getSelection();
    if (range) {
        var value = prompt('Enter link URL:');
        if (value) {
            // Auto-prefix with https:// if missing, to ensure the link works
            if(!value.startsWith('http') && !value.startsWith('#') && !value.startsWith('mailto')) {
                value = 'https://' + value;
            }
            quill.format('link', value);
        }
    } else {
        alert("Please select some text to link first.");
    }
}

// 2. Image Handler
function imageHandler() { document.getElementById('image-upload').click(); }

// 3. File Handler (Attachment)
function fileHandler() { document.getElementById('file-upload').click(); }

// Trigger upload when a file is selected
document.getElementById('image-upload').onchange = function() {
    if(this.files[0]) uploadFileToServer(this.files[0], 'image');
};
document.getElementById('file-upload').onchange = function() {
    if(this.files[0]) uploadFileToServer(this.files[0], 'file');
};

// Core Upload Logic
function uploadFileToServer(file, type) {
    var formData = new FormData();
    formData.append('file', file);

    // Update UI status to show uploading
    document.getElementById("save-status").innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Uploading...';

    fetch('/upload', { method: 'POST', body: formData })
        .then(r => r.json())
        .then(data => {
            // Restore status
            document.getElementById("save-status").innerHTML = '<i class="fa-solid fa-check"></i> Uploaded';

            if (data.url) {
                var range = quill.getSelection(true);
                var index = range ? range.index : quill.getLength(); // Insert at end if no cursor

                if (type === 'image') {
                    // Insert Image Embed
                    quill.insertEmbed(index, 'image', data.url, 'user');
                } else {
                    // Insert File Attachment as a Link with icon
                    var linkText = "📎 " + data.name + " ";
                    quill.insertText(index, linkText, 'link', data.url, 'user');
                    // Insert a space after to prevent following text from being part of the link
                    quill.insertText(index + linkText.length, " ", 'user');
                }
            }
        })
        .catch(error => {
            alert("Upload failed!");
            console.error(error);
            document.getElementById("save-status").innerHTML = '<i class="fa-solid fa-times"></i> Error';
        });
}

// --- WEBSOCKET LOGIC ---
function connect() {
    var socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // Disable debug logs for cleaner console

    stompClient.connect({}, function (frame) {
        console.log("Connected to WebSocket");

        // 1. Subscribe to document updates (Real-time sync)
        stompClient.subscribe(`/topic/document/${currentDocId}`, function (msg) {
            var m = JSON.parse(msg.body);
            // Only update if the sender is NOT myself (based on Connection ID)
            if(m.sender !== myConnectionId && quill) {
                try {
                    var contentObj = JSON.parse(m.content);
                    if(contentObj.delta) quill.updateContents(contentObj.delta);
                } catch(e) {
                    // Fallback for full content replacement
                    if(quill.root.innerHTML !== m.content) quill.root.innerHTML = m.content;
                }
            }
        });

        // 2. Subscribe to document history (Load on join)
        // We subscribe to a unique topic specific to this connection ID
        stompClient.subscribe('/topic/history/' + myConnectionId, function (msg) {
            console.log("📥 RECEIVED HISTORY!");
            var body = JSON.parse(msg.body);

            // A. Set Editor Content
            var c = body.content;
            if(c) {
                try {
                    var s = JSON.parse(c);
                    quill.setContents(s.fullDoc ? s.fullDoc : s);
                } catch (e) {
                    quill.root.innerHTML = c;
                }
            }
            // B. Set Document Title
            if (body.title) {
                document.getElementById("doc-title").innerText = body.title;
                document.title = body.title;
            }
        });

        // 3. Subscribe to User Count updates
        stompClient.subscribe('/topic/users', function (msg) {
            var d = document.getElementById("user-count");
            if(d) d.innerHTML = '<i class="fa-solid fa-users"></i> ' + JSON.parse(msg.body).content;
        });

        // 4. Actively request current user count upon connection
        stompClient.send('/app/users', {}, {});

        // 5. Send Join Request to server
        stompClient.send(`/app/join/${currentDocId}`, {}, JSON.stringify({
            'sender': myConnectionId,
            'docId': currentDocId
        }));
    });
}

// Auto-save Listener
var saveTimeout;
if (quill) {
    quill.on('text-change', function(delta, oldDelta, source) {
        if (source === 'user' && stompClient && stompClient.connected) {
            // Update status to "Saving..."
            document.getElementById("save-status").innerHTML = '<i class="fa-solid fa-sync fa-spin"></i> Saving...';

            // Send delta to server
            stompClient.send(`/app/edit/${currentDocId}`, {}, JSON.stringify({
                'content': JSON.stringify({ delta: delta, fullDoc: quill.getContents() }),
                'sender': myConnectionId,
                'docId': currentDocId
            }));

            // Reset timeout to show "Saved" after 1 second of inactivity
            clearTimeout(saveTimeout);
            saveTimeout = setTimeout(function(){
                document.getElementById("save-status").innerHTML = '<i class="fa-solid fa-cloud"></i> Saved to Drive';
            }, 1000);
        }
    });
}

// Manual Save Function
function manualSave() {
    if (!stompClient || !stompClient.connected) return alert("Offline!");

    // Force send full document content
    stompClient.send(`/app/edit/${currentDocId}`, {}, JSON.stringify({
        'content': JSON.stringify({ fullDoc: quill.getContents() }),
        'sender': myConnectionId,
        'docId': currentDocId
    }));

    // UI Feedback for button
    var btn = document.querySelector(".btn-save");
    var originalText = btn.innerHTML;

    btn.innerHTML = '<i class="fa-solid fa-check"></i> Saved!';
    btn.style.backgroundColor = "#137333"; // Green color

    setTimeout(() => {
        btn.innerHTML = '<i class="fa-regular fa-floppy-disk"></i> Save';
        btn.style.backgroundColor = "#1a73e8"; // Revert to Blue
    }, 1000);
}

// Start connection
connect();