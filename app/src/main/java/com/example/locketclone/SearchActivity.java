package com.example.locketclone;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.locketclone.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple search activity that lists users and allows sending friend requests.
 * For demo we do client-side filtering for partial match on email/displayName.
 */
public class SearchActivity extends AppCompatActivity {

    EditText etQuery;
    Button btnSearch;
    RecyclerView rvResults;
    FirestoreFriendAdapter adapter;
    List<User> results = new ArrayList<>();

    FirebaseFirestore db;
    FirebaseUser currentUser;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        etQuery = findViewById(R.id.etQuery);
        btnSearch = findViewById(R.id.btnSearch);
        rvResults = findViewById(R.id.rvResults);

        db = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        adapter = new FirestoreFriendAdapter(results, this, FirestoreFriendAdapter.Mode.SEARCH, new FirestoreFriendAdapter.Callback() {
            @Override public void onAccept(User user) {}
            @Override public void onDecline(User user) {}
            @Override public void onCancel(User user) {}
            @Override public void onRemove(User user) {}
            @Override public void onSendRequest(User user) { sendRequest(user.uid); }
            @Override public void onBlock(User user) {}
        });
        rvResults.setAdapter(adapter);
        rvResults.setLayoutManager(new LinearLayoutManager(this));

        btnSearch.setOnClickListener(v -> {
            String q = etQuery.getText().toString().trim().toLowerCase();
            if (TextUtils.isEmpty(q)) {
                Toast.makeText(this, "Nhập từ khoá tìm kiếm", Toast.LENGTH_SHORT).show();
                return;
            }
            searchUsers(q);
        });
    }

    private void searchUsers(String q) {
        // Simple approach: fetch some users and filter client-side (for demo / small number of users)
        db.collection("users").limit(50).get().addOnSuccessListener(qs -> {
            results.clear();
            for (DocumentSnapshot d : qs.getDocuments()) {
                User u = d.toObject(User.class);
                if (u == null) continue;
                u.uid = d.getId();
                String name = u.displayName != null ? u.displayName.toLowerCase() : "";
                String email = u.email != null ? u.email.toLowerCase() : "";
                if (name.contains(q) || email.contains(q)) {
                    // exclude current user
                    if (currentUser != null && currentUser.getUid().equals(u.uid)) continue;
                    results.add(u);
                }
            }
            adapter.notifyDataSetChanged();
        }).addOnFailureListener(e -> Toast.makeText(this, "Lỗi tìm kiếm", Toast.LENGTH_SHORT).show());
    }

    private void sendRequest(String targetUid) {
        if (currentUser == null) { Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show(); return; }
        String myUid = currentUser.getUid();
        db.collection("users").document(targetUid)
                .update("incomingRequests", com.google.firebase.firestore.FieldValue.arrayUnion(myUid))
                .addOnSuccessListener(aVoid -> db.collection("users").document(myUid)
                        .update("sentRequests", com.google.firebase.firestore.FieldValue.arrayUnion(targetUid))
                        .addOnSuccessListener(v -> Toast.makeText(this, "Đã gửi yêu cầu", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(this, "Lỗi cập nhật sentRequests", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e -> Toast.makeText(this, "Lỗi gửi yêu cầu", Toast.LENGTH_SHORT).show());
    }
}