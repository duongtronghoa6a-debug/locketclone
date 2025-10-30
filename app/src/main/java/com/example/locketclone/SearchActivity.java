package com.example.locketclone;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.locketclone.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SearchActivity extends AppCompatActivity {
    private static final String TAG = "SearchActivity";

    EditText etQuery;
    Button btnSearch;
    ImageButton btnBack;
    TextView tvSearchResultsTitle;
    LinearLayout sentRequestSection;

    RecyclerView rvResults;
    RecyclerView rvSentRequests;

    List<User> searchResults = new ArrayList<>();
    List<User> sentRequests = new ArrayList<>();
    Map<String, Boolean> blockedStatusMap = new HashMap<>(); // Track blocked users

    FirebaseFirestore db;
    FirebaseUser currentUser;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etQuery = findViewById(R.id.etQuery);
        btnSearch = findViewById(R.id.btnSearch);
        btnBack = findViewById(R.id.btnBack);
        tvSearchResultsTitle = findViewById(R.id.tvSearchResultsTitle);
        sentRequestSection = findViewById(R.id.sentRequestSection);
        rvResults = findViewById(R.id.rvResults);
        rvSentRequests = findViewById(R.id.rvSentRequests);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        btnBack.setOnClickListener(v -> finish());

        // Setup RecyclerView cho kết quả tìm kiếm
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        updateSearchAdapter();

        // Setup RecyclerView cho danh sách đã gửi yêu cầu
        FirestoreFriendAdapter sentRequestAdapter = new FirestoreFriendAdapter(
                sentRequests,
                this,
                FirestoreFriendAdapter.Mode.SENT,
                new FirestoreFriendAdapter.Callback() {
                    @Override public void onAccept(User user) {}
                    @Override public void onDecline(User user) {}
                    @Override public void onCancel(User user) { cancelSentRequest(user); }
                    @Override public void onRemove(User user) {}
                    @Override public void onSendRequest(User user) {}
                    @Override public void onBlock(User user) {}
                    @Override public void onUnblock(User user) {}
                }
        );
        rvSentRequests.setAdapter(sentRequestAdapter);
        rvSentRequests.setLayoutManager(new LinearLayoutManager(this));

        btnSearch.setOnClickListener(v -> {
            String q = etQuery.getText().toString().trim().toLowerCase();
            if (TextUtils.isEmpty(q)) {
                Toast.makeText(this, "Nhập từ khoá tìm kiếm", Toast.LENGTH_SHORT).show();
                return;
            }
            searchUsers(q);
        });

        loadSentRequests();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSentRequests();
    }

    /**
     * Cập nhật adapter cho search results với logic động
     */
    private void updateSearchAdapter() {
        FirestoreFriendAdapter searchAdapter = new FirestoreFriendAdapter(
                searchResults,
                this,
                FirestoreFriendAdapter.Mode.SEARCH,
                new FirestoreFriendAdapter.Callback() {
                    @Override public void onAccept(User user) {}
                    @Override public void onDecline(User user) {}
                    @Override public void onCancel(User user) {}
                    @Override public void onRemove(User user) {}
                    @Override public void onSendRequest(User user) {
                        sendFriendRequest(user);
                    }
                    @Override public void onBlock(User user) {}
                    @Override public void onUnblock(User user) {
                        unblockUserFromSearch(user);
                    }
                }
        ) {
            @Override
            public void onBindViewHolder(VH holder, int position) {
                User u = getItems().get(position);

                // Kiểm tra blocked status
                boolean isBlocked = blockedStatusMap.containsKey(u.uid) && blockedStatusMap.get(u.uid);

                // Hiển thị tên
                String displayName = u.displayName != null && !u.displayName.isEmpty()
                        ? u.displayName
                        : (u.email != null ? u.email : "Không tên");

                if (isBlocked) {
                    displayName += " (Đã chặn)";
                }
                holder.tvName.setText(displayName);

                // Hiển thị avatar
                if (u.photoUrl != null && !u.photoUrl.isEmpty()) {
                    com.bumptech.glide.Glide.with(SearchActivity.this)
                            .load(u.photoUrl)
                            .circleCrop()
                            .into(holder.ivAvatar);
                } else {
                    holder.ivAvatar.setImageResource(R.drawable.ic_person_circle);
                }

                // Cấu hình nút
                holder.btnPrimary.setVisibility(View.VISIBLE);
                holder.btnSecondary.setVisibility(View.GONE);

                if (isBlocked) {
                    // Nút "Gỡ chặn"
                    holder.btnPrimary.setText("Gỡ chặn");
                    holder.btnPrimary.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFFFF5252)
                    );
                    holder.btnPrimary.setOnClickListener(v -> unblockUserFromSearch(u));
                } else {
                    // Nút "Kết bạn"
                    holder.btnPrimary.setText("Kết bạn");
                    holder.btnPrimary.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFFBB86FC)
                    );
                    holder.btnPrimary.setOnClickListener(v -> sendFriendRequest(u));
                }
            }
        };

        rvResults.setAdapter(searchAdapter);
    }

    /**
     * Load danh sách yêu cầu kết bạn đã gửi
     */
    private void loadSentRequests() {
        if (currentUser == null) return;
        String myUid = currentUser.getUid();

        db.collection("users")
                .document(myUid)
                .collection("sentRequests")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    sentRequests.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            sentRequests.add(user);
                        }
                    }

                    if (sentRequests.isEmpty()) {
                        sentRequestSection.setVisibility(View.GONE);
                    } else {
                        sentRequestSection.setVisibility(View.VISIBLE);
                        rvSentRequests.getAdapter().notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading sent requests", e);
                });
    }

    /**
     * Hủy yêu cầu kết bạn đã gửi
     */
    private void cancelSentRequest(User user) {
        if (currentUser == null) return;
        String myUid = currentUser.getUid();

        db.collection("users")
                .document(myUid)
                .collection("sentRequests")
                .document(user.uid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    db.collection("users")
                            .document(user.uid)
                            .collection("friendRequests")
                            .document(myUid)
                            .delete()
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(this, "Đã hủy yêu cầu", Toast.LENGTH_SHORT).show();
                                loadSentRequests();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error canceling request", e);
                    Toast.makeText(this, "Lỗi hủy yêu cầu", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * TÌM KIẾM NGƯỜI DÙNG - ĐƠNN GIẢN HÓA
     */
    private void searchUsers(String q) {
        if (currentUser == null) return;
        String myUid = currentUser.getUid();

        Log.d(TAG, "Searching for: " + q);

        db.collection("users").limit(50).get().addOnSuccessListener(qs -> {
            searchResults.clear();
            blockedStatusMap.clear();

            int totalMatches = 0;

            for (DocumentSnapshot d : qs.getDocuments()) {
                User u = d.toObject(User.class);
                if (u == null) continue;
                u.uid = d.getId();

                String name = u.displayName != null ? u.displayName.toLowerCase() : "";
                String email = u.email != null ? u.email.toLowerCase() : "";

                if (name.contains(q) || email.contains(q)) {
                    if (myUid.equals(u.uid)) continue;
                    totalMatches++;

                    // Thêm vào kết quả và kiểm tra status sau
                    searchResults.add(u);
                }
            }

            Log.d(TAG, "Found " + totalMatches + " matches");

            if (searchResults.isEmpty()) {
                Toast.makeText(this, "Không tìm thấy kết quả", Toast.LENGTH_SHORT).show();
                tvSearchResultsTitle.setVisibility(View.GONE);
            } else {
                tvSearchResultsTitle.setVisibility(View.VISIBLE);
                // Kiểm tra từng user xem có bị chặn, đã là bạn, hoặc đã gửi yêu cầu chưa
                checkAllUserStatuses(myUid);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Search error", e);
            Toast.makeText(this, "Lỗi tìm kiếm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Kiểm tra trạng thái của tất cả users trong kết quả
     */
    private void checkAllUserStatuses(String myUid) {
        List<User> filteredResults = new ArrayList<>();

        // Load blocked list trước
        db.collection("users")
                .document(myUid)
                .collection("blockedUsers")
                .get()
                .addOnSuccessListener(blockedSnapshot -> {
                    // Đánh dấu các user đã bị chặn
                    for (QueryDocumentSnapshot doc : blockedSnapshot) {
                        blockedStatusMap.put(doc.getId(), true);
                    }

                    // Load friends list
                    db.collection("users")
                            .document(myUid)
                            .collection("friends")
                            .get()
                            .addOnSuccessListener(friendsSnapshot -> {
                                List<String> friendIds = new ArrayList<>();
                                for (QueryDocumentSnapshot doc : friendsSnapshot) {
                                    friendIds.add(doc.getId());
                                }

                                // Load sent requests list
                                db.collection("users")
                                        .document(myUid)
                                        .collection("sentRequests")
                                        .get()
                                        .addOnSuccessListener(sentSnapshot -> {
                                            List<String> sentIds = new ArrayList<>();
                                            for (QueryDocumentSnapshot doc : sentSnapshot) {
                                                sentIds.add(doc.getId());
                                            }

                                            // Filter: chỉ giữ lại user chưa là bạn và chưa gửi yêu cầu
                                            // Nhưng vẫn giữ user đã chặn để hiển thị nút "Gỡ chặn"
                                            for (User user : searchResults) {
                                                if (!friendIds.contains(user.uid) && !sentIds.contains(user.uid)) {
                                                    filteredResults.add(user);
                                                }
                                            }

                                            // Cập nhật kết quả
                                            searchResults.clear();
                                            searchResults.addAll(filteredResults);

                                            Log.d(TAG, "Filtered results: " + searchResults.size());

                                            updateSearchAdapter();

                                            if (searchResults.isEmpty()) {
                                                Toast.makeText(this, "Không có kết quả phù hợp", Toast.LENGTH_SHORT).show();
                                                tvSearchResultsTitle.setVisibility(View.GONE);
                                            }
                                        });
                            });
                });
    }

    /**
     * GỬI YÊU CẦU KẾT BẠN
     */
    private void sendFriendRequest(User targetUser) {
        if (currentUser == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        String myUid = currentUser.getUid();
        String targetUid = targetUser.uid;

        // Kiểm tra xem có chặn người này không
        if (blockedStatusMap.containsKey(targetUid) && blockedStatusMap.get(targetUid)) {
            Toast.makeText(this, "Bạn đã chặn người dùng này. Hãy gỡ chặn trước.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Sending friend request from " + myUid + " to " + targetUid);

        db.collection("users").document(myUid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    User myUser = documentSnapshot.toObject(User.class);
                    if (myUser == null) {
                        Toast.makeText(this, "Không tìm thấy thông tin người dùng", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    db.collection("users")
                            .document(targetUid)
                            .collection("friendRequests")
                            .document(myUid)
                            .set(myUser)
                            .addOnSuccessListener(aVoid -> {
                                db.collection("users")
                                        .document(myUid)
                                        .collection("sentRequests")
                                        .document(targetUid)
                                        .set(targetUser)
                                        .addOnSuccessListener(aVoid1 -> {
                                            Toast.makeText(this, "Đã gửi yêu cầu kết bạn", Toast.LENGTH_SHORT).show();

                                            searchResults.remove(targetUser);
                                            updateSearchAdapter();

                                            loadSentRequests();
                                        });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error sending request", e);
                                Toast.makeText(this, "Lỗi gửi yêu cầu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                });
    }

    /**
     * GỠ CHẶN TỪ KẾT QUẢ TÌM KIẾM
     */
    private void unblockUserFromSearch(User user) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Gỡ chặn")
                .setMessage("Bạn có chắc chắn muốn gỡ chặn " + user.displayName + "?")
                .setPositiveButton("Gỡ chặn", (dialog, which) -> {
                    performUnblockFromSearch(user);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void performUnblockFromSearch(User user) {
        String myUid = currentUser.getUid();

        db.collection("users")
                .document(myUid)
                .collection("blockedUsers")
                .document(user.uid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Đã gỡ chặn " + user.displayName, Toast.LENGTH_SHORT).show();

                    // Cập nhật map
                    blockedStatusMap.put(user.uid, false);

                    // Reload adapter để hiển thị nút "Kết bạn"
                    updateSearchAdapter();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi gỡ chặn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}