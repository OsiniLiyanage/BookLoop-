package lk.jiat.bookloop.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.util.UUID;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.activity.SignInActivity;
import lk.jiat.bookloop.databinding.FragmentProfileBinding;
import lk.jiat.bookloop.helper.WishlistDatabase;
import lk.jiat.bookloop.model.User;

// NEW — Profile Fragment: shows user stats, allows camera photo change, call support.
//       Covers: Multimedia (camera capture), Telephony (call intent)
public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    // NEW — BookLoop admin support phone number
    private static final String SUPPORT_PHONE = "+94112345678";

    private FragmentProfileBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore db;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (firebaseAuth.getCurrentUser() == null) {
            // Not logged in — go to sign in
            startActivity(new Intent(getActivity(), SignInActivity.class));
            return;
        }

        loadUserProfile();
        loadStats();

        // NEW — Profile photo change via gallery or camera (Multimedia requirement)
        binding.profileChangePhoto.setOnClickListener(v -> showImagePickerDialog());
        binding.profileImage.setOnClickListener(v -> showImagePickerDialog());

        // Navigation to other fragments
        binding.profileMenuOrders.setOnClickListener(v -> loadFragment(new OrdersFragment()));
        binding.profileMenuWishlist.setOnClickListener(v -> loadFragment(new WishlistFragment()));
        binding.profileMenuSettings.setOnClickListener(v -> loadFragment(new SettingsFragment()));

        // NEW — Telephony: one-tap call to BookLoop support (Telephony requirement)
        binding.profileMenuCallSupport.setOnClickListener(v -> {
            Intent callIntent = new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + SUPPORT_PHONE));
            startActivity(callIntent);
        });

        // Sign out
        binding.profileBtnSignout.setOnClickListener(v -> {
            firebaseAuth.signOut();
            startActivity(new Intent(getActivity(), SignInActivity.class));
            requireActivity().finish();
        });
    }

    // Load name, email, profile picture from Firestore
    private void loadUserProfile() {
        String uid = firebaseAuth.getCurrentUser().getUid();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(ds -> {
                    if (ds.exists()) {
                        User user = ds.toObject(User.class);
                        if (user != null) {
                            binding.profileName.setText(user.getName());
                            binding.profileEmail.setText(user.getEmail());

                            // NEW — Load profile pic from Firebase Storage using stored image ID
                            if (user.getProfilePicUrl() != null && !user.getProfilePicUrl().isEmpty()) {
                                FirebaseStorage.getInstance()
                                        .getReference("profile_images")
                                        .child(user.getProfilePicUrl())
                                        .getDownloadUrl()
                                        .addOnSuccessListener(uri ->
                                                Glide.with(this)
                                                        .load(uri)
                                                        .circleCrop()
                                                        .into(binding.profileImage));
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load user: " + e.getMessage()));
    }

    // NEW — Load stats: orders count, wishlist count, active rentals
    private void loadStats() {
        String uid = firebaseAuth.getCurrentUser().getUid();

        // Orders count from Firestore
        db.collection("orders").whereEqualTo("userId", uid).get()
                .addOnSuccessListener(qds -> binding.profileOrdersCount.setText(String.valueOf(qds.size())))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load orders count: " + e.getMessage()));

        // Active rentals (orders with status PAID or PROCESSING)
        db.collection("orders")
                .whereEqualTo("userId", uid)
                .whereIn("status", java.util.Arrays.asList("PAID", "PROCESSING", "DELIVERED"))
                .get()
                .addOnSuccessListener(qds -> binding.profileActiveRentals.setText(String.valueOf(qds.size())))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to load active rentals: " + e.getMessage()));

        // Wishlist count from local SQLite (fast, no network needed)
        new Thread(() -> {
            WishlistDatabase wishlistDb = new WishlistDatabase(requireContext());
            int count = wishlistDb.getWishlistCount();
            requireActivity().runOnUiThread(() ->
                    binding.profileWishlistCount.setText(String.valueOf(count)));
        }).start();
    }

    // NEW — Show bottom sheet to choose Gallery or Camera (Multimedia requirement)
    private void showImagePickerDialog() {
        String[] options = {"Take Photo (Camera)", "Choose from Gallery"};
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Change Profile Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // NEW — Camera capture (Multimedia: camera)
                        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        cameraLauncher.launch(cameraIntent);
                    } else {
                        // Gallery picker
                        Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        galleryIntent.setType("image/*");
                        galleryLauncher.launch(galleryIntent);
                    }
                })
                .show();
    }

    // NEW — Handle camera result (Multimedia requirement)
    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    android.os.Bundle extras = result.getData().getExtras();
                    if (extras != null) {
                        android.graphics.Bitmap bitmap = (android.graphics.Bitmap) extras.get("data");
                        binding.profileImage.setImageBitmap(bitmap);

                        // Save bitmap to internal storage, then upload to Firebase Storage
                        saveBitmapToStorage(bitmap);
                    }
                }
            });

    // Gallery result
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    Glide.with(this).load(uri).circleCrop().into(binding.profileImage);
                    uploadImageToFirebase(uri);
                }
            });

    // NEW — Save camera bitmap to internal app storage, then upload (Internal Storage requirement)
    private void saveBitmapToStorage(android.graphics.Bitmap bitmap) {
        new Thread(() -> {
            try {
                // Save to internal storage first
                java.io.File dir = requireContext().getFilesDir();
                java.io.File file = new java.io.File(dir, "profile_temp.jpg");
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fos);
                fos.close();

                // Upload from the saved file
                Uri fileUri = Uri.fromFile(file);
                requireActivity().runOnUiThread(() -> uploadImageToFirebase(fileUri));
            } catch (Exception e) {
                Log.e(TAG, "Failed to save bitmap: " + e.getMessage());
            }
        }).start();
    }

    // Upload selected image to Firebase Storage and save path to Firestore
    private void uploadImageToFirebase(Uri uri) {
        String uid = firebaseAuth.getCurrentUser().getUid();
        String imageId = UUID.randomUUID().toString();

        FirebaseStorage.getInstance().getReference("profile_images").child(imageId)
                .putFile(uri)
                .addOnSuccessListener(ts -> {
                    db.collection("users").document(uid)
                            .update("profilePicUrl", imageId)
                            .addOnSuccessListener(v ->
                                    Toast.makeText(getContext(), "Profile photo updated!", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Profile image upload failed: " + e.getMessage());
                    Toast.makeText(getContext(), "Upload failed. Try again.", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadFragment(Fragment fragment) {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }
}