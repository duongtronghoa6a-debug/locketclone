package com.example.locketclone.model;

import java.util.ArrayList;
import java.util.List;

public class User {
    public String uid;
    public String email;
    public String displayName;
    public String photoUrl;

    // THÊM CÁC TRƯỜNG NÀY VÀO
    public List<String> friends = new ArrayList<>();
    public List<String> incomingRequests = new ArrayList<>();
    public List<String> sentRequests = new ArrayList<>();

    // Constructor mặc định (bắt buộc cho Firestore)
    public User() { }

    // Constructor đầy đủ (tùy chọn)
    public User(String uid, String email, String displayName, String photoUrl) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.photoUrl = photoUrl;
    }

    // THÊM CÁC GETTER METHODS
    public String getUserId() {
        return uid;
    }

    public String getUsername() {
        return displayName != null ? displayName : email;
    }

    public String getProfileImageUrl() {
        return photoUrl;
    }

    public String getEmail() {
        return email;
    }

    public List<String> getFriends() {
        return friends;
    }

    public List<String> getIncomingRequests() {
        return incomingRequests;
    }

    public List<String> getSentRequests() {
        return sentRequests;
    }

    // THÊM CÁC SETTER METHODS (nếu cần)
    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public void setFriends(List<String> friends) {
        this.friends = friends;
    }

    public void setIncomingRequests(List<String> incomingRequests) {
        this.incomingRequests = incomingRequests;
    }

    public void setSentRequests(List<String> sentRequests) {
        this.sentRequests = sentRequests;
    }
}