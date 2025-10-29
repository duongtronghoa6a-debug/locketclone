package com.example.locketclone.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

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

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * BottomSheet that loads lists from Firestore in realtime:
 * - incomingRequests (uids)
 * - sentRequests (uids)
 * - friends (uids)
 *
 * Uses Firestore snapshot listener on current user's document.
 */
public class FriendsBottomSheet extends BottomSheetDialogFragment {

    RecyclerView rvFriends;
    FirestoreFriendAdapter friendsAdapter, incomingAdapter, sentAdapter;
    List<User> friendsList = new ArrayList<>();
    List<User> incomingList = new ArrayList<>();
    List<User> sentList = new ArrayList<>();
    Button btnToggle;
    EditText etAdd;
    Button btnAdd;

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
        rvFriends = v.findViewById(R.id.rvFriends);
        btnToggle = v.findViewById(R.id.btnToggle);
        etAdd = v.findViewById(R.id.etAdd);
        btnAdd = v.findViewById(R.id.btnAdd);

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

        rvFriends.setAdapter(friendsAdapter);
        rvFriends.setLayoutManager(new LinearLayoutManager(getContext()));

        btnToggle.setOnClickListener(bt -> {
            expanded = !expanded;
            btnToggle.setText(expanded ? "Rút gọn" : "Xem thêm");
            refreshShownFriends();
        });

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
                            // error
                            return;
                        }
                        if (snapshot == null || !snapshot.exists()) {
                            return;
                        }
                        // parse arrays (may be null)
                        List<String> friends = (List<String>) snapshot.get("friends");
                        List<String> incoming = (List<String>) snapshot.get("incomingRequests");
                        List<String> sent = (List<String>) snapshot.get("sentRequests");

                        loadUsersByUids(friends, friendsList, friendsAdapter);
                        loadUsersByUids(incoming, incomingList, incomingAdapter);
                        loadUsersByUids(sent, sentList, sentAdapter);

                        refreshShownFriends();
                    }
                });

        return v;
    }

    private FirestoreFriendAdapter.Callback newCallback() {
        return new FirestoreFriendAdapter.Callback() {
            @Override
            public void onAccept(User user) { acceptRequest(user.uid); }
            @Override
            public void onDecline(User user) { declineRequest(user.uid); }
            @Override
            public void onCancel(User user) { cancelSentRequest(user.uid); }
            @Override
            public void onRemove(User user) { unfriend(user.uid); }
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
        // update adapter via public API
        friendsAdapter.updateItems(shown);
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

    // Send friend request by target UID
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

    // helper: search by email then send
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
        db.collection("users").document(myUid)
                .update("incomingRequests", FieldValue.arrayRemove(fromUid))
                .addOnSuccessListener(aVoid -> db.collection("users").document(fromUid)
                        .update("sentRequests", FieldValue.arrayRemove(myUid))
                        .addOnSuccessListener(v -> Toast.makeText(getContext(), "Đã từ chối", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Từ chối thất bại", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Từ chối thất bại", Toast.LENGTH_SHORT).show());
    }

    private void acceptRequest(String fromUid) {
        // remove pending entries, then add to friends for both users
        db.collection("users").document(myUid)
                .update("incomingRequests", FieldValue.arrayRemove(fromUid))
                .addOnSuccessListener(aVoid -> {
                    // remove myUid from sender's sentRequests
                    db.collection("users").document(fromUid)
                            .update("sentRequests", FieldValue.arrayRemove(myUid))
                            .addOnSuccessListener(v -> {
                                // now add each other as friends
                                db.collection("users").document(myUid)
                                        .update("friends", FieldValue.arrayUnion(fromUid))
                                        .addOnSuccessListener(a -> db.collection("users").document(fromUid)
                                                .update("friends", FieldValue.arrayUnion(myUid))
                                                .addOnSuccessListener(b -> Toast.makeText(getContext(), "Đã chấp nhận", Toast.LENGTH_SHORT).show())
                                                .addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi khi cập nhật friend bên kia", Toast.LENGTH_SHORT).show()))
                                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi khi cập nhật friend", Toast.LENGTH_SHORT).show());
                            })
                            .addOnFailureListener(e -> Toast.makeText(getContext(), "Lỗi cập nhật sentRequests người gửi", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Chấp nhận thất bại", Toast.LENGTH_SHORT).show());
    }

    private void unfriend(String friendUid) {
        db.collection("users").document(myUid)
                .update("friends", FieldValue.arrayRemove(friendUid))
                .addOnSuccessListener(aVoid -> db.collection("users").document(friendUid)
                        .update("friends", FieldValue.arrayRemove(myUid))
                        .addOnSuccessListener(v -> Toast.makeText(getContext(), "Đã huỷ kết bạn", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Huỷ kết bạn thất bại (bên kia)", Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Huỷ kết bạn thất bại", Toast.LENGTH_SHORT).show());
    }

    private void blockUser(String uidToBlock) {
        // demo: remove friend/request then (optionally) add to blocked list (not implemented in data model)
        unfriend(uidToBlock);
        // You may wish to add a "blocked" field in your user doc
    }
}