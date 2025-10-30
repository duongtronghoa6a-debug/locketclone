package com.example.locketclone;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.locketclone.ui.FriendsBottomSheet;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class HomeActivity extends AppCompatActivity {

    private TextView tvFriendsCount;
    private LinearLayout btnFriends;
    private ImageButton btnLogout;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        mAuth = FirebaseAuth.getInstance();

        tvFriendsCount = findViewById(R.id.tvFriendsCount);
        btnFriends = findViewById(R.id.btnFriends);
        btnLogout = findViewById(R.id.btnLogout);

        // Load số lượng bạn bè
        loadFriendsCount();

        // Nút Bạn bè
        btnFriends.setOnClickListener(v -> {
            // Mở Bottom Sheet
            FriendsBottomSheet bottomSheet = new FriendsBottomSheet();
            bottomSheet.show(getSupportFragmentManager(), "FriendsBottomSheet");
        });

        // Nút Log Out
        btnLogout.setOnClickListener(v -> {
            showLogoutDialog();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload số bạn bè khi quay lại activity
        loadFriendsCount();
    }

    private void loadFriendsCount() {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Log.d("HomeActivity", "Loading friends count for user: " + currentUserId);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(currentUserId)
                .collection("friends")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    Log.d("HomeActivity", "Friends count: " + count);
                    if (count == 0) {
                        tvFriendsCount.setText("Chưa có bạn bè");
                    } else {
                        tvFriendsCount.setText(count + " bạn bè");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("HomeActivity", "Error loading friends count", e);
                    tvFriendsCount.setText("... bạn bè");
                });
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                    logout();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void logout() {
        // Đăng xuất Firebase
        mAuth.signOut();

        Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();

        // Chuyển về màn hình đăng nhập
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}