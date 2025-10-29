package com.example.locketclone;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.locketclone.ui.FriendsBottomSheet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class HomeActivity extends AppCompatActivity {
    Button btnFriends;
    TextView tvFriendCount;
    TextView tvUserName;
    Button btnSignOut;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration userListener;
    private static final int MAX_FRIENDS = 20;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btnFriends = findViewById(R.id.btnFriends);
        tvFriendCount = findViewById(R.id.tvFriendCount);
        tvUserName = findViewById(R.id.tvUserName);
        btnSignOut = findViewById(R.id.btnSignOut);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Click friends -> open sheet only if logged in
        btnFriends.setOnClickListener(v -> {
            FirebaseUser u = auth.getCurrentUser();
            if (u == null) {
                // redirect to login
                startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                return;
            }
            FriendsBottomSheet sheet = FriendsBottomSheet.newInstance();
            sheet.show(getSupportFragmentManager(), "friends_sheet");
        });

        btnSignOut.setOnClickListener(v -> {
            auth.signOut();
            // remove listener and refresh UI / redirect to login
            detachUserListener();
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            // not logged in -> go to login screen
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        // show basic user info immediately (email or uid). Full info will update from Firestore listener.
        tvUserName.setText(u.getDisplayName() != null && !u.getDisplayName().isEmpty() ? u.getDisplayName() : u.getEmail());

        attachUserListenerIfLoggedIn();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachUserListener();
    }

    private void attachUserListenerIfLoggedIn() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;
        String uid = u.getUid();
        detachUserListener();
        userListener = db.collection("users").document(uid)
                .addSnapshotListener((DocumentSnapshot snapshot, @Nullable com.google.firebase.firestore.FirebaseFirestoreException e) -> {
                    if (e != null) {
                        // error reading doc -> show fallback
                        tvFriendCount.setText("0 người bạn");
                        return;
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        tvFriendCount.setText("0 người bạn");
                        return;
                    }
                    // update display name if available in user doc
                    String displayName = snapshot.getString("displayName");
                    if (displayName != null && !displayName.isEmpty()) {
                        tvUserName.setText(displayName);
                    }
                    Object raw = snapshot.get("friends");
                    int count = 0;
                    if (raw instanceof List) {
                        count = ((List<?>) raw).size();
                    }
                    tvFriendCount.setText(String.format("%d / %d người bạn", count, MAX_FRIENDS));
                });
    }

    private void detachUserListener() {
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }
}