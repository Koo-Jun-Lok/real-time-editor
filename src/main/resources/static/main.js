var stompClient = null;
var myClientId = Math.random().toString(36).substring(7);

// Initialize Quill Editor (The Google Docs interface)
var quill = new Quill('#editor-container', {
    theme: 'snow',
    placeholder: 'Start collaborating...'
});

function connect() {
    var socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function (frame) {
        document.getElementById("status").innerText = "Connected as User: " + myClientId;

        // Subscribe to incoming changes
        stompClient.subscribe('/topic/document', function (messageOutput) {
            var message = JSON.parse(messageOutput.body);

            // 1. Check if the message is from SOMEONE ELSE
            if (message.sender !== myClientId) {
                // 2. Apply their change (Delta) to my screen
                // This merges their "A" with my "B" without deleting anything!
                console.log("Applying update from " + message.sender);
                quill.updateContents(message.payload);
            }
        });
    });
}

// Listen for local changes (When I type)
quill.on('text-change', function(delta, oldDelta, source) {
    // Only send changes if *I* made them (not if the API made them)
    if (source === 'user') {
        if(stompClient) {
            // Send the "delta" (the change object) to the server
            stompClient.send("/app/edit", {}, JSON.stringify({
                'payload': delta,
                'sender': myClientId
            }));
        }
    }
});

connect();