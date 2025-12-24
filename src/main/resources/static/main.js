var stompClient = null;
var myClientId = Math.random().toString(36).substring(7);
var quill = null;

try {
    // 1. Initialize Quill
    quill = new Quill('#editor-container', {
        theme: 'snow',
        modules: {
            toolbar: [
                ['bold', 'italic', 'underline'],
                [{ 'list': 'ordered'}, { 'list': 'bullet' }],
                [{ 'header': [1, 2, 3, false] }],
                ['clean']
            ]
        }
    });
} catch (e) {
    console.error("Quill failed to load:", e);
}

function connect() {
    var socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function (frame) {
        // Update status to show we are online
        document.getElementById("status").innerText = "Online (ID: " + myClientId + ")";

        // 2. Subscribe to LIVE updates (The typing)
        stompClient.subscribe('/topic/document', function (messageOutput) {
            var message = JSON.parse(messageOutput.body);
            if(message.sender !== myClientId && quill) {
                var payload = JSON.parse(message.content);
                quill.updateContents(payload.delta);
            }
        });

        // 3. Subscribe to HISTORY (For late joiners)
        stompClient.subscribe('/topic/history', function (messageOutput) {
            var message = JSON.parse(messageOutput.body);
            if (message.content && message.content !== "" && quill) {
                var savedState = JSON.parse(message.content);

                // Check if the history has the special structure (with fullDoc)
                if (savedState.fullDoc) {
                    quill.setContents(savedState.fullDoc);
                } else {
                    quill.setContents(savedState);
                }
            }
        });

        // 4. Subscribe to USER COUNT (The new feature)
        stompClient.subscribe('/topic/users', function (messageOutput) {
            var message = JSON.parse(messageOutput.body);
            // Update the HTML element with the new number
            document.getElementById("user-count").innerText = "Users: " + message.content;
        });

        // 5. Ask for the document history immediately
        stompClient.send("/app/join", {}, JSON.stringify({ 'sender': myClientId }));
    });
}

// 6. Handle Outgoing Changes (When YOU type)
if (quill) {
    quill.on('text-change', function(delta, oldDelta, source) {
        if (source === 'user' && stompClient && stompClient.connected) {

            // We send BOTH the small change (delta) AND the full doc
            var payload = {
                delta: delta,
                fullDoc: quill.getContents()
            };

            stompClient.send("/app/edit", {}, JSON.stringify({
                'content': JSON.stringify(payload),
                'sender': myClientId
            }));
        }
    });
}

connect();