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
}
