package com.example.locketclone;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

// THÊM CÁC IMPORT NÀY VÀO
import com.example.locketclone.model.User; // 1. Import lớp User model
import com.google.firebase.firestore.FirebaseFirestore; // 2. Import Firestore

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * LoginActivity: demo login/signup.
 * If you have Firebase configured, set USE_FIREBASE = true to use real sign-in.
 * Otherwise set to false to use local demo login.
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final boolean USE_FIREBASE = true; // set false to demo without Firebase

    EditText etName, etEmail, etPassword;
    Button btnSignUp, btnSignIn;
    FirebaseAuth mAuth;

    // 3. KHAI BÁO BIẾN CHO FIRESTORE
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnSignIn = findViewById(R.id.btnSignIn);

        if (USE_FIREBASE) {
            mAuth = FirebaseAuth.getInstance();
            // 4. KHỞI TẠO FIRESTORE
            db = FirebaseFirestore.getInstance();

            FirebaseUser cu = mAuth.getCurrentUser();
            if (cu != null) {
                // already logged in
                startActivity(new Intent(this, HomeActivity.class));
                finish();
                return;
            }
        } else {
            // demo: skip auto login
        }

        // ===================================================================
        // == SỬA PHẦN NÀY: btnSignUp.setOnClickListener ==
        // ===================================================================
        btnSignUp.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pwd = etPassword.getText().toString();
            String name = etName.getText().toString().trim();
            if (email.isEmpty() || pwd.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "Điền đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }
            if (USE_FIREBASE) {
                mAuth.createUserWithEmailAndPassword(email, pwd)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                // Đăng ký Authentication thành công, giờ lưu vào Firestore
                                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                                if (firebaseUser != null) {
                                    String uid = firebaseUser.getUid();

                                    // Tạo đối tượng User mới
                                    User newUser = new User(uid, email, name, ""); // photoUrl ban đầu để trống

                                    // Lưu vào Firestore
                                    db.collection("users").document(uid).set(newUser)
                                            .addOnSuccessListener(aVoid -> {
                                                // Lưu Firestore thành công!
                                                Toast.makeText(this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
                                                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                                                finish();
                                            })
                                            .addOnFailureListener(e -> {
                                                // Lỗi khi lưu Firestore
                                                Toast.makeText(this, "Lỗi khi lưu dữ liệu: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                Log.e(TAG, "Firestore save failed", e);
                                            });
                                }
                            } else {
                                Toast.makeText(this, "Signup failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                                Log.e(TAG, "signup failed", task.getException());
                            }
                        });
            } else {
                // Demo: accept and go to home
                Toast.makeText(this, "Demo signup OK", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
            }
        });

        btnSignIn.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pwd = etPassword.getText().toString();
            if (email.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "Nhập email & mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }
            if (USE_FIREBASE) {
                mAuth.signInWithEmailAndPassword(email, pwd)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                                finish();
                            } else {
                                Toast.makeText(this, "Login failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
            } else {
                // Demo login pass
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
            }
        });
    }
}
