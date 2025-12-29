package core.entity;

public class ChatMessage {
    private String uuid;
    private String role;
    private String content;
    private long timestamp;

    public ChatMessage(String uuid, String role, String content, long timestamp) {
        this.uuid = uuid;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getUuid() { return uuid; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
}