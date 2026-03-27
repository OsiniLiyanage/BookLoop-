package lk.jiat.bookloop.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.UUID;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.databinding.ActivityMainBinding;
import lk.jiat.bookloop.databinding.SideNavHeaderBinding;
import lk.jiat.bookloop.fragment.AboutFragment;
import lk.jiat.bookloop.fragment.CartFragment;
import lk.jiat.bookloop.fragment.CategoryFragment;
import lk.jiat.bookloop.fragment.HelpFragment;
import lk.jiat.bookloop.fragment.HomeFragment;
import lk.jiat.bookloop.fragment.LibraryFragment;
import lk.jiat.bookloop.fragment.MapFragment;
import lk.jiat.bookloop.fragment.MessageFragment;
import lk.jiat.bookloop.fragment.OrdersFragment;
import lk.jiat.bookloop.fragment.ProfileFragment;
import lk.jiat.bookloop.fragment.SettingsFragment;
import lk.jiat.bookloop.fragment.WishlistFragment;
import lk.jiat.bookloop.model.User;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        BottomNavigationView.OnItemSelectedListener {
    private ActivityMainBinding binding;
    private SideNavHeaderBinding sideNavHeaderBinding;
    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        View headerView = binding.sideNavigationView.getHeaderView(0);

        sideNavHeaderBinding = SideNavHeaderBinding.bind(headerView);


        drawerLayout = binding.drawerLayout;
        toolbar = binding.toolbar;
        navigationView = binding.sideNavigationView;
        bottomNavigationView = binding.bottomNavigationView;

        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle =
                new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(toggle);

        toggle.syncState();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }
            }
        });


        navigationView.setNavigationItemSelectedListener(this);
        bottomNavigationView.setOnItemSelectedListener(this);

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
            navigationView.getMenu().findItem(R.id.nav_home).setChecked(true);
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_home).setChecked(true);
        }

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        //check and load user details
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            firebaseFirestore.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(ds -> {

                        if (ds.exists()) {
                            User user = ds.toObject(User.class);
                            sideNavHeaderBinding.headerUserName.setText(user.getName());
                            sideNavHeaderBinding.headerUserEmail.setText(user.getEmail());

                            Glide.with(MainActivity.this)
                                    .load(user.getProfilePicUrl())
                                    .circleCrop()
                                    .into(sideNavHeaderBinding.headerProfilePic);
                        } else {
                            Log.e("Firestore", "Document does not exist");
                        }

                    }).addOnFailureListener(e -> {
                        Log.e("Firestore", "Error: " + e.getMessage());
                    });


            //Hide side nav login menu item
            navigationView.getMenu().findItem(R.id.nav_login).setVisible(false);

            //Show side nav menu items
            navigationView.getMenu().findItem(R.id.nav_profile).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_orders).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_wishlist).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_cart).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_messages).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_logout).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_about).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_help).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_logout).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_my_library).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_map).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_category).setVisible(true);

            /// Change or Set profile image
            sideNavHeaderBinding.headerProfilePic.setOnClickListener(v -> {

                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);

                activityResultLauncher.launch(intent);
            });

        }

    }

    ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Uri uri = result.getData().getData();
                    Log.i("ImageURI", uri.getPath());

                    Glide.with(MainActivity.this)
                            .load(uri)
                            .circleCrop()
                            .into(sideNavHeaderBinding.headerProfilePic);


                    String imageId = UUID.randomUUID().toString();

                    FirebaseStorage storage = FirebaseStorage.getInstance();

                    StorageReference imageReference = storage.getReference("profile_images").child(imageId);
                    imageReference.putFile(uri)
                            .addOnSuccessListener(taskSnapshot -> {

                                firebaseFirestore.collection("users")
                                        .document(firebaseAuth.getUid())
                                        .update("profilePicUrl", imageId)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(MainActivity.this, "Profile image changed!", Toast.LENGTH_SHORT).show();
                                        });
                            });

                }
            }
    );


    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        Menu navMenu = navigationView.getMenu();
        Menu bottomNavMenu = bottomNavigationView.getMenu();

        for (int i = 0; i < navMenu.size(); i++) {
            navMenu.getItem(i).setChecked(false);
        }

        for (int i = 0; i < bottomNavMenu.size(); i++) {
            bottomNavMenu.getItem(i).setChecked(false);
        }

        if (itemId == R.id.nav_home || itemId == R.id.bottom_nav_home) {
            loadFragment(new HomeFragment());
            navigationView.getMenu().findItem(R.id.nav_home).setChecked(true);
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_home).setChecked(true);

        } else if (itemId == R.id.nav_map || itemId == R.id.bottom_nav_map) {
            loadFragment(new MapFragment());
            navigationView.getMenu().findItem(R.id.nav_map).setChecked(true);
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_map).setChecked(true);

        } else if (itemId == R.id.nav_my_library || itemId == R.id.bottom_nav_my_library) {
            loadFragment(new LibraryFragment());
            navigationView.getMenu().findItem(R.id.nav_my_library).setChecked(true);
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_my_library).setChecked(true);

        } else if (itemId == R.id.nav_profile || itemId == R.id.bottom_nav_profile) {
            if (firebaseAuth.getCurrentUser() == null){
                Intent intent = new Intent(MainActivity.this, SignInActivity.class);
                startActivity(intent);
                finish();
            }
            loadFragment(new ProfileFragment());
            navigationView.getMenu().findItem(R.id.nav_profile).setChecked(true);
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_profile).setChecked(true);

        } else if (itemId == R.id.nav_cart) {
            if (firebaseAuth.getCurrentUser() == null){
                Intent intent = new Intent(MainActivity.this, SignInActivity.class);
                startActivity(intent);
                finish();
            }
            loadFragment(new CartFragment());
            navigationView.getMenu().findItem(R.id.nav_cart).setChecked(true);

        } else if (itemId == R.id.nav_orders) {
            loadFragment(new OrdersFragment());
            navigationView.getMenu().findItem(R.id.nav_orders).setChecked(true);

        } else if (itemId == R.id.nav_messages) {
            loadFragment(new MessageFragment());
            navigationView.getMenu().findItem(R.id.nav_messages).setChecked(true);

        } else if (itemId == R.id.nav_wishlist) {
            loadFragment(new WishlistFragment());
            navigationView.getMenu().findItem(R.id.nav_wishlist).setChecked(true);

        } else if (itemId == R.id.nav_settings) {
            loadFragment(new SettingsFragment());
            navigationView.getMenu().findItem(R.id.nav_settings).setChecked(true);

        } else if (itemId == R.id.nav_help) {
            loadFragment(new HelpFragment());
            navigationView.getMenu().findItem(R.id.nav_help).setChecked(true);

        } else if (itemId == R.id.nav_about) {
            loadFragment(new AboutFragment());
            navigationView.getMenu().findItem(R.id.nav_about).setChecked(true);

        } else if (itemId == R.id.nav_category) {
            loadFragment(new CategoryFragment());
            navigationView.getMenu().findItem(R.id.nav_category).setChecked(true);


        } else if (itemId == R.id.nav_login) {
            Intent intent = new Intent(MainActivity.this, SignInActivity.class);
            startActivity(intent);

        } else if (itemId == R.id.nav_logout) {
            firebaseAuth.signOut();
            loadFragment(new HomeFragment());
            navigationView.getMenu().clear();
            navigationView.inflateMenu(R.menu.side_nav_menu);

            //View headerView = navigationView.getHeaderView(0);

            navigationView.removeHeaderView(sideNavHeaderBinding.getRoot());
            navigationView.inflateHeaderView(R.layout.side_nav_header);

        }

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }

        return true;
    }

    private void loadFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }


}