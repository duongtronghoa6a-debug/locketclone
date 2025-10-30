package com.example.locketclone;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.locketclone.model.User;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final boolean USE_FIREBASE = true;

    EditText etName, etEmail, etPassword;
    Button btnSignUp, btnSignIn;
    FirebaseAuth mAuth;
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
            db = FirebaseFirestore.getInstance();

            FirebaseUser cu = mAuth.getCurrentUser();
            if (cu != null) {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
                return;
            }
        }

        // Nút Đăng ký
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
                                FirebaseUser firebaseUser = mAuth.getCurrentUser();
                                if (firebaseUser != null) {
                                    String uid = firebaseUser.getUid();

                                    // Tạo đối tượng User mới
                                    User newUser = new User(uid, email, name, "");

                                    // Lưu user document vào Firestore
                                    db.collection("users").document(uid).set(newUser)
                                            .addOnSuccessListener(aVoid -> {
                                                Log.d(TAG, "User document created successfully");

                                                // Lưu thành công, chuyển sang HomeActivity
                                                Toast.makeText(this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
                                                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                                                finish();
                                            })
                                            .addOnFailureListener(e -> {
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
                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
            }
        });
    }
}