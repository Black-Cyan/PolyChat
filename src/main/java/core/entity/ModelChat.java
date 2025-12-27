package core.entity;

public class ModelChat {
    private String modelUuid;
    private String chatUuid;

    public ModelChat(String modelUuid, String chatUuid) {
        this.modelUuid = modelUuid;
        this.chatUuid = chatUuid;
    }

    public String getModelUuid() { return modelUuid; }
    public String getChatUuid() { return chatUuid; }
}
