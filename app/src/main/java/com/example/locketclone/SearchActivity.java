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
    Map<String, Boolean> blockedStatusMap = new HashMap<>();
    Map<String, String> userStatusMap = new HashMap<>(); // "friend"/"sent"/"incoming"

    FirebaseFirestore db;
    FirebaseUser currentUser;
    FirestoreFriendAdapter searchAdapter;
    FirestoreFriendAdapter sentRequestAdapter;

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
        sentRequestAdapter = new FirestoreFriendAdapter(
                sentRequests,
                this,
                FirestoreFriendAdapter.Mode.SENT,
                new FirestoreFriendAdapter.Callback() {
                    @Override public void onAccept(User user) {}
                    @Override public void onDecline(User user) {}
                    @Override public void onCancel(User user) {
                        cancelSentRequestFromSentSection(user); // FIX: dùng method mới
                    }
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

        // FIX 3: Load status trước khi search để tránh delay
        preloadAllStatuses();
        loadSentRequests();
    }

    @Override
    protected void onResume() {
        super.onResume();
        preloadAllStatuses();
        loadSentRequests();
    }

    /**
     * FIX 3: Preload tất cả status vào cache (chạy ngầm khi mở activity)
     */
    private void preloadAllStatuses() {
        if (currentUser == null) return;
        String myUid = currentUser.getUid();

        // Load blocked
        db.collection("users").document(myUid).collection("blockedUsers").get()
                .addOnSuccessListener(qs -> {
                    for (QueryDocumentSnapshot doc : qs) {
                        blockedStatusMap.put(doc.getId(), true);
                    }
                });

        // Load friends
        db.collection("users").document(myUid).collection("friends").get()
                .addOnSuccessListener(qs -> {
                    for (QueryDocumentSnapshot doc : qs) {
                        userStatusMap.put(doc.getId(), "friend");
                    }
                    Log.d(TAG, "Preloaded " + userStatusMap.size() + " friends");
                });

        // Load sentRequests
        db.collection("users").document(myUid).collection("sentRequests").get()
                .addOnSuccessListener(qs -> {
                    for (QueryDocumentSnapshot doc : qs) {
                        userStatusMap.put(doc.getId(), "sent");
                    }
                });

        // Load incoming
        db.collection("users").document(myUid).collection("friendRequests").get()
                .addOnSuccessListener(qs -> {
                    for (QueryDocumentSnapshot doc : qs) {
                        userStatusMap.put(doc.getId(), "incoming");
                    }
                });
    }

    /**
     * Cập nhật adapter cho search results
     */
    private void updateSearchAdapter() {
        searchAdapter = new FirestoreFriendAdapter(
                searchResults,
                this,
                FirestoreFriendAdapter.Mode.SEARCH,
                new FirestoreFriendAdapter.Callback() {
                    @Override public void onAccept(User user) {}
                    @Override public void onDecline(User user) {}
                    @Override public void onCancel(User user) {
                        cancelSentRequestFromSearchResults(user);
                    }
                    @Override public void onRemove(User user) {}
                    @Override public void onSendRequest(User user) { sendFriendRequest(user); }
                    @Override public void onBlock(User user) {}
                    @Override public void onUnblock(User user) { unblockUserFromSearch(user); }
                }
        ) {
            @Override
            public void onBindViewHolder(VH holder, int position) {
                User u = getItems().get(position);

                boolean isBlocked = blockedStatusMap.containsKey(u.uid) && blockedStatusMap.get(u.uid);
                String status = userStatusMap.get(u.uid);

                String displayName = u.displayName != null && !u.displayName.isEmpty()
                        ? u.displayName
                        : (u.email != null ? u.email : "Không tên");

                if (isBlocked) {
                    displayName += " (Đã chặn)";
                }
                holder.tvName.setText(displayName);

                if (u.photoUrl != null && !u.photoUrl.isEmpty()) {
                    com.bumptech.glide.Glide.with(SearchActivity.this)
                            .load(u.photoUrl)
                            .circleCrop()
                            .into(holder.ivAvatar);
                } else {
                    holder.ivAvatar.setImageResource(R.drawable.ic_person_circle);
                }

                holder.btnPrimary.setVisibility(View.VISIBLE);
                holder.btnSecondary.setVisibility(View.GONE);

                if (isBlocked) {
                    holder.btnPrimary.setText("Gỡ chặn");
                    holder.btnPrimary.setEnabled(true);
                    holder.btnPrimary.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFFFF5252)
                    );
                    holder.btnPrimary.setOnClickListener(v -> unblockUserFromSearch(u));
                } else if ("friend".equals(status)) {
                    holder.btnPrimary.setText("Bạn bè");
                    holder.btnPrimary.setEnabled(false);
                    holder.btnPrimary.setAlpha(0.5f);
                    holder.btnPrimary.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFF757575)
                    );
                    holder.btnPrimary.setOnClickListener(null);
                } else if ("sent".equals(status)) {
                    // NÚT VÀNG "Hủy"
                    holder.btnPrimary.setText("Hủy");
                    holder.btnPrimary.setEnabled(true);
                    holder.btnPrimary.setAlpha(1.0f);
                    holder.btnPrimary.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFFFF9800) // Vàng
                    );
                    holder.btnPrimary.setOnClickListener(v -> cancelSentRequestFromSearchResults(u));
                } else if ("incoming".equals(status)) {
                    holder.btnPrimary.setText("Đã gửi cho bạn");
                    holder.btnPrimary.setEnabled(false);
                    holder.btnPrimary.setAlpha(0.5f);
                    holder.btnPrimary.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(0xFF757575)
                    );
                    holder.btnPrimary.setOnClickListener(null);
                } else {
                    holder.btnPrimary.setText("Kết bạn");
                    holder.btnPrimary.setEnabled(true);
                    holder.btnPrimary.setAlpha(1.0f);
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
     * Load danh sách yêu cầu đã gửi
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
                        sentRequestAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading sent requests", e);
                });
    }

    /**
     * FIX 2: Hủy yêu cầu từ section "Đã gửi" (nút xám) → sync với search results
     */
    private void cancelSentRequestFromSentSection(User user) {
        if (currentUser == null) return;
        String myUid = currentUser.getUid();

        Log.d(TAG, "Cancelling from Sent Section: " + user.uid);

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

                                // FIX 2: Update status → null (có thể gửi lại)
                                userStatusMap.remove(user.uid);

                                // Reload sent section
                                loadSentRequests();

                                // Refresh search results (nếu user này đang trong search)
                                if (searchAdapter != null) {
                                    searchAdapter.notifyDataSetChanged();
                                }
                            });
                });
    }

    /**
     * Hủy yêu cầu từ search results (nút vàng)
     */
    private void cancelSentRequestFromSearchResults(User user) {
        if (currentUser == null) return;
        String myUid = currentUser.getUid();

        Log.d(TAG, "Cancelling from Search Results: " + user.uid);

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

                                // Update status → null
                                userStatusMap.remove(user.uid);

                                // Refresh search adapter
                                if (searchAdapter != null) {
                                    searchAdapter.notifyDataSetChanged();
                                }

                                // Reload sent section
                                loadSentRequests();
                            });
                });
    }

    /**
     * TÌM KIẾM NGƯỜI DÙNG
     */
    private void searchUsers(String q) {
        if (currentUser == null) return;
        String myUid = currentUser.getUid();

        Log.d(TAG, "Searching for: " + q);

        db.collection("users").limit(50).get().addOnSuccessListener(qs -> {
            searchResults.clear();

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
                    searchResults.add(u);
                }
            }

            Log.d(TAG, "Found " + totalMatches + " matches");

            if (searchResults.isEmpty()) {
                Toast.makeText(this, "Không tìm thấy kết quả", Toast.LENGTH_SHORT).show();
                tvSearchResultsTitle.setVisibility(View.GONE);
            } else {
                tvSearchResultsTitle.setVisibility(View.VISIBLE);
                // FIX 3: Không cần query lại, dùng cache
                updateSearchAdapter();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Search error", e);
            Toast.makeText(this, "Lỗi tìm kiếm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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

        if (blockedStatusMap.containsKey(targetUid) && blockedStatusMap.get(targetUid)) {
            Toast.makeText(this, "Bạn đã chặn người dùng này. Hãy gỡ chặn trước.", Toast.LENGTH_SHORT).show();
            return;
        }

        String status = userStatusMap.get(targetUid);
        if ("friend".equals(status)) {
            Toast.makeText(this, "Đã là bạn bè rồi", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("sent".equals(status)) {
            Toast.makeText(this, "Đã gửi yêu cầu trước đó", Toast.LENGTH_SHORT).show();
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

                                            // Update status → "sent"
                                            userStatusMap.put(targetUid, "sent");
                                            if (searchAdapter != null) {
                                                searchAdapter.notifyDataSetChanged();
                                            }

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
     * GỠ CHẶN
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
                    blockedStatusMap.put(user.uid, false);
                    updateSearchAdapter();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Lỗi gỡ chặn: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}