package com.example.locketclone.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.locketclone.R;
import com.example.locketclone.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class FriendRequestAdapter extends RecyclerView.Adapter<FriendRequestAdapter.ViewHolder> {

    private List<User> friendRequests;
    private Context context;
    private OnRequestHandledListener listener;

    public interface OnRequestHandledListener {
        void onFriendRequestHandled();
    }

    public FriendRequestAdapter(List<User> friendRequests, Context context, OnRequestHandledListener listener) {
        this.friendRequests = friendRequests;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_friend_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = friendRequests.get(position);

        holder.tvName.setText(user.getUsername());

        // Load avatar
        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            Glide.with(context)
                    .load(user.getProfileImageUrl())
                    .into(holder.ivAvatar);
        }

        // Nút Đồng ý
        holder.btnAccept.setOnClickListener(v -> {
            acceptFriendRequest(user, position);
        });

        // Nút Từ chối
        holder.btnDecline.setOnClickListener(v -> {
            declineFriendRequest(user, position);
        });
    }

    @Override
    public int getItemCount() {
        return friendRequests.size();
    }

    private void acceptFriendRequest(User user, int position) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Thêm vào danh sách bạn bè
        db.collection("users")
                .document(currentUserId)
                .collection("friends")
                .document(user.getUserId())
                .set(user)
                .addOnSuccessListener(aVoid -> {
                    // Xóa khỏi danh sách yêu cầu
                    db.collection("users")
                            .document(currentUserId)
                            .collection("friendRequests")
                            .document(user.getUserId())
                            .delete()
                            .addOnSuccessListener(aVoid1 -> {
                                Toast.makeText(context, "Đã chấp nhận lời mời kết bạn", Toast.LENGTH_SHORT).show();
                                friendRequests.remove(position);
                                notifyItemRemoved(position);
                                listener.onFriendRequestHandled();
                            });
                });
    }

    private void declineFriendRequest(User user, int position) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Xóa yêu cầu kết bạn
        db.collection("users")
                .document(currentUserId)
                .collection("friendRequests")
                .document(user.getUserId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(context, "Đã từ chối lời mời kết bạn", Toast.LENGTH_SHORT).show();
                    friendRequests.remove(position);
                    notifyItemRemoved(position);
                    listener.onFriendRequestHandled();
                });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        MaterialButton btnAccept;
        MaterialButton btnDecline;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnDecline = itemView.findViewById(R.id.btnDecline);
        }
    }
}