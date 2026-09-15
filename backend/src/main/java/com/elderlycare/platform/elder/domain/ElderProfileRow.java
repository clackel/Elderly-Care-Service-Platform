package com.elderlycare.platform.elder.domain;

public class ElderProfileRow {
    private Long id;
    private Long communityId;
    private String payloadCipher;
    private String nameHash;
    private String phoneHash;
    private String identityHash;
    private String requestId;
    private String requestHash;
    private String status;
    private Long version;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCommunityId() { return communityId; }
    public void setCommunityId(Long communityId) { this.communityId = communityId; }
    public String getPayloadCipher() { return payloadCipher; }
    public void setPayloadCipher(String payloadCipher) { this.payloadCipher = payloadCipher; }
    public String getNameHash() { return nameHash; }
    public void setNameHash(String nameHash) { this.nameHash = nameHash; }
    public String getPhoneHash() { return phoneHash; }
    public void setPhoneHash(String phoneHash) { this.phoneHash = phoneHash; }
    public String getIdentityHash() { return identityHash; }
    public void setIdentityHash(String identityHash) { this.identityHash = identityHash; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }
}
