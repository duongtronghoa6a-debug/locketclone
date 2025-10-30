package com.example.locketclone.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.locketclone.FirestoreFriendAdapter;
import com.example.locketclone.R;
import com.example.locketclone.model.User;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class FriendsBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "FriendsBottomSheet";

    RecyclerView rvIncoming, rvFriends;
    FirestoreFriendAdapter friendsAdapter, incomingAdapter, sentAdapter;
    List<User> friendsList = new ArrayList<>();
    List<User> incomingList = new ArrayList<>();
    List<User> sentList = new ArrayList<>();
    View btnToggle;
    TextView tvToggleText;
    View searchBox;
    EditText etAdd;
    Button btnAdd;
    TextView tvIncomingHeader, tvFriendsHeader;

    FirebaseFirestore db;
    FirebaseAuth auth;
    String myUid;
    boolean expanded = false;

    public static FriendsBottomSheet newInstance() {
        return new FriendsBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_friends_bottom_sheet, container, false);
        rvIncoming = v.findViewById(R.id.rvIncoming);
        rvFriends = v.findViewById(R.id.rvFriends);
        btnToggle = v.findViewById(R.id.btnToggle);
        tvToggleText = v.findViewById(R.id.tvToggleText);
        searchBox = v.findViewById(R.id.searchBox);
        etAdd = v.findViewById(R.id.etAdd);
        btnAdd = v.findViewById(R.id.btnAdd);
        tvIncomingHeader = v.findViewById(R.id.tvIncomingHeader);
        tvFriendsHeader = v.findViewById(R.id.tvFriendsHeader);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            Toast.makeText(getContext(), "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            dismiss();
            return v;
        }
        myUid = u.getUid();

        friendsAdapter = new FirestoreFriendAdapter(friendsList, getContext(), FirestoreFriendAdapter.Mode.FRIENDS, newCallback());
        incomingAdapter = new FirestoreFriendAdapter(incomingList, getContext(), FirestoreFriendAdapter.Mode.INCOMING, newCallback());
        sentAdapter = new FirestoreFriendAdapter(sentList, getContext(), FirestoreFriendAdapter.Mode.SENT, newCallback());

        // Bind adapters
        rvIncoming.setAdapter(incomingAdapter);
        rvIncoming.setLayoutManager(new LinearLayoutManager(getContext()));
        rvFriends.setAdapter(friendsAdapter);
        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));

        // Search box click opens SearchActivity
        searchBox.setOnClickListener(v1 -> {
            Intent intent = new Intent(getContext(), com.example.locketclone.SearchActivity.class);
            startActivity(intent);
        });

        // toggle cycles through friend list expansion (keeps incoming visible on top)
        btnToggle.setOnClickListener(bt -> {
            expanded = !expanded;
            if (tvToggleText != null) {
                tvToggleText.setText(expanded ? "Rút gọn" : "Xem thêm");
            }
            refreshShownFriends();
        });

        // Keep backward compatibility with add button
        btnAdd.setOnClickListener(b -> {
            String email = etAdd.getText().toString().trim();
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(getContext(), "Nhập email để gửi yêu cầu", Toast.LENGTH_SHORT).show();
                return;
            }
            sendFriendRequestByEmail(email);
        });

        // attach snapshot listener to my user doc
        db.collection("users").document(myUid)
                .addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.e(TAG, "snapshot listener error", e);
                            return;
                        }
                        if (snapshot == null || !snapshot.exists()) {
                            return;
                        }

                        Log.d(TAG, "snapshot data: " + snapshot.getData());

                        // parse arrays (may be null)
                        List<String> friends = (List<String>) snapshot.get("friends");
                        List<String> incoming = (List<String>) snapshot.get("incomingRequests");
                        List<String> sent = (List<String>) snapshot.get("sentRequests");

                        loadUsersByUids(friends, friendsList, friendsAdapter);
                        loadUsersByUids(incoming, incomingList, incomingAdapter);
                        loadUsersByUids(sent, sentList, sentAdapter);

                        // show/hide incoming section (keep it above friends list)
                        if (incoming == null || incoming.isEmpty()) {
                            tvIncomingHeader.setVisibility(View.GONE);
                            rvIncoming.setVisibility(View.GONE);
                        } else {
                            tvIncomingHeader.setVisibility(View.VISIBLE);
                            rvIncoming.setVisibility(View.VISIBLE);
                        }

                        // show/hide friends header
                        if (friends == null || friends.isEmpty()) {
                            tvFriendsHeader.setVisibility(View.GONE);
                        } else {
                            tvFriendsHeader.setVisibility(View.VISIBLE);
                        }

                        refreshShownFriends();
                    }
                });

        return v;
    }

    private FirestoreFriendAdapter.Callback newCallback() {
        return new FirestoreFriendAdapter.Callback() {
            @Override
            public void onAccept(User user) {
                Log.d(TAG, "onAccept clicked for uid=" + user.uid);
                acceptRequest(user.uid);
            }

            @Override
            public void onDecline(User user) {
                Log.d(TAG, "onDecline clicked for uid=" + user.uid);
                declineRequest(user.uid);
            }

            @Override
            public void onCancel(User user) { cancelSentRequest(user.uid); }

            @Override
            public void onRemove(User user) { confirmUnfriend(user.uid, user.displayName != null ? user.displayName : user.email); }

            @Override
            public void onSendRequest(User user) { sendFriendRequest(user.uid); }

            @Override
            public void onBlock(User user) { blockUser(user.uid); }
        };
    }

    private void refreshShownFriends() {
        // show top 3 or full depending on expanded
        List<User> shown = new ArrayList<>();
        if (!expanded && friendsList.size() > 3) {
            shown.addAll(friendsList.subList(0, 3));
        } else {
            shown.addAll(friendsList);
        }
        friendsAdapter.updateItems(shown);
        
        // Show/hide toggle button based on friend count
        if (friendsList.size() > 3) {
            btnToggle.setVisibility(View.VISIBLE);
        } else {
            btnToggle.setVisibility(View.GONE);
        }
    }

    private void loadUsersByUids(List<String> uids, List<User> targetList, FirestoreFriendAdapter adapter) {
        targetList.clear();
        adapter.updateItems(new ArrayList<>()); // clear UI
        if (uids == null || uids.isEmpty()) return;
        final int[] remaining = {uids.size()};
        List<User> temp = new ArrayList<>();
        for (String fid : uids) {
            db.collection("users").document(fid).get().addOnSuccessListener(ds -> {
                if (ds != null && ds.exists()) {
                    User usr = ds.toObject(User.class);
                    if (usr != null) {
                        usr.uid = ds.getId();
                        temp.add(usr);
                    }
                }
                remaining[0]--;
                if (remaining[0] <= 0) {
                    // push results to adapter and targetList
                    targetList.clear();
                    targetList.addAll(temp);
                    adapter.updateItems(new ArrayList<>(targetList));
                    Log.d(TAG, "Loaded users for adapter, count=" + targetList.size());
                }
            }).addOnFailureListener(e -> {
                remaining[0]--;
                if (remaining[0] <= 0) {
                    targetList.clear();
                    targetList.addAll(temp);
                    adapter.updateItems(new ArrayList<>(targetList));
                }
            });
        }
    }

    private void cancelSentRequest(String targetUid) {
        db.collection("users").document(myUid)
                .update("sentRequests", FieldValue.arrayRemove(targetUid))
                .addOnSuccessListener(aVoid -> db.collection("users").document(targetUid)
                        .update("incomingRequests", FieldValue.arrayRemove(myUid))
                        .addOnSuccessListener(v -> Toast.makeText(getContext(), "Đã huỷ yêu cầu", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Huỷ thất bại", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Huỷ thất bại", Toast.LENGTH_SHORT).show());
    }

    private void declineRequest(String fromUid) {
        Log.d(TAG, "declineRequest fromUid=" + fromUid + " myUid=" + myUid);
        WriteBatch batch = db.batch();
        batch.update(db.collection("users").document(myUid), "incomingRequests", FieldValue.arrayRemove(fromUid));
        batch.update(db.collection("users").document(fromUid), "sentRequests", FieldValue.arrayRemove(myUid));
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "declineRequest: batch commit success");
                    Toast.makeText(getContext(), "Đã từ chối", Toast.LENGTH_SHORT).show();
                    refreshShownFriends();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "declineRequest failed", e);
                    Toast.makeText(getContext(), "Từ chối thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void acceptRequest(String fromUid) {
        Log.d(TAG, "acceptRequest fromUid=" + fromUid + " myUid=" + myUid);
        WriteBatch batch = db.batch();
        batch.update(db.collection("users").document(myUid), "incomingRequests", FieldValue.arrayRemove(fromUid));
        batch.update(db.collection("users").document(fromUid), "sentRequests", FieldValue.arrayRemove(myUid));
        batch.update(db.collection("users").document(myUid), "friends", FieldValue.arrayUnion(fromUid));
        batch.update(db.collection("users").document(fromUid), "friends", FieldValue.arrayUnion(myUid));
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "acceptRequest: batch commit success");
                    Toast.makeText(getContext(), "Đã chấp nhận", Toast.LENGTH_SHORT).show();
                    refreshShownFriends();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "acceptRequest failed", e);
                    Toast.makeText(getContext(), "Chấp nhận thất bại: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // Confirm unfriend before performing the actual unfriend
    private void confirmUnfriend(String friendUid, String friendLabel) {
        new AlertDialog.Builder(getContext())
                .setTitle("Xác nhận")
                .setMessage("Bạn có chắc muốn huỷ kết bạn với " + (friendLabel != null ? friendLabel : "người này") + "?")
                .setPositiveButton("Huỷ kết bạn", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        performUnfriend(friendUid);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // actual unfriend logic (previous behavior preserved)
    private void performUnfriend(String friendUid) {
        db.collection("users").document(myUid)
                .update("friends", FieldValue.arrayRemove(friendUid))
                .addOnSuccessListener(aVoid -> db.collection("users").document(friendUid)
                        .update("friends", FieldValue.arrayRemove(myUid))
                        .addOnSuccessListener(v -> Toast.makeText(getContext(), "Đã huỷ kết bạn", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Huỷ kết bạn thất bại (bên kia)", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Huỷ kết bạn thất bại", Toast.LENGTH_SHORT).show());
    }

    // Compatibility shim: keep old method name 'unfriend' so existing calls compile
    private void unfriend(String friendUid) {
        performUnfriend(friendUid);
    }

    private void blockUser(String uidToBlock) {
        // preserve previous behavior: call unfriend (alias) to remove friendship if any
        unfriend(uidToBlock);
    }

    // sendFriendRequest & sendFriendRequestByEmail: keep existing implementation
    private void sendFriendRequest(String targetUid) {
        if (targetUid.equals(myUid)) return;
        db.collection("users").document(targetUid)
                .update("incomingRequests", FieldValue.arrayUnion(myUid))
                .addOnSuccessListener(aVoid -> db.collection("users").document(myUid)
                        .update("sentRequests", FieldValue.arrayUnion(targetUid))
                        .addOnSuccessListener(b -> Toast.makeText(getContext(), "Đã gửi yêu cầu", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi cập nhật sentRequests", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi gửi yêu cầu", Toast.LENGTH_SHORT).show());
    }

    private void sendFriendRequestByEmail(String email) {
        db.collection("users").whereEqualTo("email", email).get().addOnSuccessListener(qs -> {
            if (qs == null || qs.isEmpty()) {
                Toast.makeText(getContext(), "Không tìm thấy người dùng với email này", Toast.LENGTH_SHORT).show();
                return;
            }
            String targetUid = qs.getDocuments().get(0).getId();
            sendFriendRequest(targetUid);
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Lỗi tìm người dùng", Toast.LENGTH_SHORT).show();
        });
    }
}