package com.example.locketclone;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.locketclone.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that supports multiple modes:
 * - FRIENDS: shows "Huỷ bạn" (onRemove)
 * - INCOMING: shows "Đồng ý" / "Từ chối" (onAccept/onDecline)
 * - SENT: shows "Huỷ" (onCancel)
 * - SEARCH: shows "Kết bạn" (onSendRequest)
 */
public class FirestoreFriendAdapter extends RecyclerView.Adapter<FirestoreFriendAdapter.VH> {
    public enum Mode { FRIENDS, INCOMING, SENT, SEARCH }

    private final List<User> items;
    private final Context ctx;
    private final Mode mode;
    private final Callback callback;

    public interface Callback {
        void onAccept(User user);
        void onDecline(User user);
        void onCancel(User user);
        void onRemove(User user);
        void onSendRequest(User user);
        void onBlock(User user);
    }

    public FirestoreFriendAdapter(List<User> list, Context ctx, Mode mode, Callback cb) {
        this.items = list != null ? list : new ArrayList<>();
        this.ctx = ctx;
        this.mode = mode;
        this.callback = cb;
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_friend, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        User u = items.get(position);
        holder.tvName.setText(u.displayName != null && !u.displayName.isEmpty() ? u.displayName : (u.email != null ? u.email : "Không tên"));
        if (u.photoUrl != null && !u.photoUrl.isEmpty()) {
            Glide.with(ctx).load(u.photoUrl).circleCrop().into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_person_circle);
        }

        // configure buttons based on mode
        holder.btnPrimary.setVisibility(View.GONE);
        holder.btnSecondary.setVisibility(View.GONE);

        switch (mode) {
            case FRIENDS:
                holder.btnPrimary.setText("Huỷ bạn");
                holder.btnPrimary.setVisibility(View.VISIBLE);
                holder.btnPrimary.setOnClickListener(v -> {
                    if (callback != null) callback.onRemove(u);
                });
                holder.btnSecondary.setText("Chặn");
                holder.btnSecondary.setVisibility(View.VISIBLE);
                holder.btnSecondary.setOnClickListener(v -> {
                    if (callback != null) callback.onBlock(u);
                });
                break;
            case INCOMING:
                holder.btnPrimary.setText("Đồng ý");
                holder.btnPrimary.setVisibility(View.VISIBLE);
                holder.btnPrimary.setOnClickListener(v -> {
                    if (callback != null) callback.onAccept(u);
                });
                holder.btnSecondary.setText("Từ chối");
                holder.btnSecondary.setVisibility(View.VISIBLE);
                holder.btnSecondary.setOnClickListener(v -> {
                    if (callback != null) callback.onDecline(u);
                });
                break;
            case SENT:
                holder.btnPrimary.setText("Huỷ");
                holder.btnPrimary.setVisibility(View.VISIBLE);
                holder.btnPrimary.setOnClickListener(v -> {
                    if (callback != null) callback.onCancel(u);
                });
                break;
            case SEARCH:
                holder.btnPrimary.setText("Kết bạn");
                holder.btnPrimary.setVisibility(View.VISIBLE);
                holder.btnPrimary.setOnClickListener(v -> {
                    new AlertDialog.Builder(ctx)
                            .setTitle("Gửi yêu cầu")
                            .setMessage("Gửi yêu cầu kết bạn tới " + (u.displayName != null ? u.displayName : u.email) + "?")
                            .setPositiveButton("Gửi", (d,w) -> { if (callback!=null) callback.onSendRequest(u); })
                            .setNegativeButton("Hủy", null)
                            .show();
                });
                break;
        }

        // long-press options as fallback (info)
        holder.itemView.setOnLongClickListener(v -> {
            CharSequence[] options = {"Thông tin", "Huỷ"};
            new AlertDialog.Builder(ctx)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            new AlertDialog.Builder(ctx)
                                    .setTitle(u.displayName != null ? u.displayName : u.email)
                                    .setMessage("Email: " + (u.email != null ? u.email : "-"))
                                    .setPositiveButton("OK", null).show();
                        }
                    }).show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // Public method to update adapter items from outside
    public void updateItems(List<User> newItems) {
        items.clear();
        if (newItems != null && !newItems.isEmpty()) items.addAll(newItems);
        notifyDataSetChanged();
    }

    // Optional getter (read-only view)
    public List<User> getItems() {
        return items;
    }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;
        Button btnPrimary, btnSecondary;
        public VH(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            btnPrimary = itemView.findViewById(R.id.btnPrimary);
            btnSecondary = itemView.findViewById(R.id.btnSecondary);
        }
    }
}