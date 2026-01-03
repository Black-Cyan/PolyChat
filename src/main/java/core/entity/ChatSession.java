package core.entity;

public class ChatSession {
    private final String uuid;
    private final String modelUuid;
    private String title;
    private final long createdAt;

    public ChatSession(String uuid, String modelUuid, String title, long createdAt) {
        this.uuid = uuid;
        this.modelUuid = modelUuid;
        this.title = title;
        this.createdAt = createdAt;
    }

    public String getUuid() { return uuid; }
    public String getModelUuid() { return modelUuid; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getCreatedAt() { return createdAt; }
}
