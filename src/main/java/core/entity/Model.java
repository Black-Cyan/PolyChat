package core.entity;

public class Model {
    private String uuid;
    private String baseUrl;
    private String apiKey; // 只写
    private String modelName;
    private String nickname;

    public Model(String uuid, String baseUrl, String apiKey, String modelName, String nickname) {
        this.uuid = uuid;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.nickname = nickname;
    }

    public String getUuid() { return uuid; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
