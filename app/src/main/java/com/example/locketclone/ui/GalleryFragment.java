package com.example.locketclone.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.locketclone.R; // Đảm bảo bạn có layout tương ứng

public class GalleryFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        // Bạn sẽ cần tạo một file layout tên là 'fragment_gallery.xml'
        return inflater.inflate(R.layout.fragment_gallery, container, false);
    }
}
