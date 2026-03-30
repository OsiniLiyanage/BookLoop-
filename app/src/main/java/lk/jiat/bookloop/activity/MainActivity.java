package lk.jiat.bookloop.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
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
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

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
import java.util.concurrent.TimeUnit;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.databinding.ActivityMainBinding;
import lk.jiat.bookloop.databinding.SideNavHeaderBinding;
import lk.jiat.bookloop.fragment.AboutFragment;
import lk.jiat.bookloop.fragment.AllBooksFragment;
import lk.jiat.bookloop.fragment.CartFragment;
import lk.jiat.bookloop.fragment.CategoryFragment;
import lk.jiat.bookloop.fragment.HelpFragment;
import lk.jiat.bookloop.fragment.HomeFragment;
import lk.jiat.bookloop.fragment.ListingFragment;
import lk.jiat.bookloop.fragment.MapFragment;
import lk.jiat.bookloop.fragment.OrdersFragment;
import lk.jiat.bookloop.fragment.ProfileFragment;
import lk.jiat.bookloop.fragment.SettingsFragment;
import lk.jiat.bookloop.fragment.WishlistFragment;
import lk.jiat.bookloop.helper.NotificationHelper;
import lk.jiat.bookloop.helper.ThemeApplier;
import lk.jiat.bookloop.model.User;
import lk.jiat.bookloop.receiver.ConnectivityReceiver;
import lk.jiat.bookloop.worker.RentalReminderWorker;

/*
 * MainActivity.java
 * The main container activity — hosts all Fragments inside fragment_container.
 *
 * Bottom nav: Home, Map, Cart, Orders, Profile
 * Side drawer: all secondary navigation
 *
 * SEARCH BAR BEHAVIOUR:
 *   - On HomeFragment: live search filters the home sections
 *   - On ListingFragment: live search filters the category listing
 *   - On AllBooksFragment: live search filters the all-books grid
 *   - On any OTHER fragment: pressing Enter/search launches AllBooksFragment
 *     with the query pre-filled so the user always gets results
 */
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

    private ConnectivityReceiver connectivityReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeApplier.apply(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        View headerView = binding.sideNavigationView.getHeaderView(0);
        sideNavHeaderBinding = SideNavHeaderBinding.bind(headerView);

        drawerLayout         = binding.drawerLayout;
        toolbar              = binding.toolbar;
        navigationView       = binding.sideNavigationView;
        bottomNavigationView = binding.bottomNavigationView;

        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
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

        // ── Search bar wiring ─────────────────────────────────────────────────
        // The header search bar works on EVERY screen:
        //   • HomeFragment      → live search within home sections
        //   • ListingFragment   → live filter of category books
        //   • AllBooksFragment  → live filter of all-books grid
        //   • Everything else   → on text change, navigate to AllBooksFragment
        //     so the user always gets a result no matter which screen they're on
        binding.textInputSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Fragment current = getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_container);

                if (current instanceof HomeFragment) {
                    // Home: live search within home sections
                    ((HomeFragment) current).performSearch(s.toString());

                } else if (current instanceof ListingFragment) {
                    // Category listing: filter in memory
                    ((ListingFragment) current).filterProducts(s.toString());

                } else if (current instanceof AllBooksFragment) {
                    // All-books screen: filter its grid
                    ((AllBooksFragment) current).filterProducts(s.toString());

                } else if (s.length() >= 2) {
                    // Any other screen + user has typed at least 2 chars →
                    // open AllBooksFragment with the search term pre-loaded.
                    // We use >= 2 to avoid launching on every single keystroke.
                    openAllBooksWithQuery(s.toString().trim());
                }
            }

            public void afterTextChanged(android.text.Editable s) {}
        });

        firebaseAuth      = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();

        // Load the logged-in user's details into the drawer header
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            firebaseFirestore.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(ds -> {
                        if (ds.exists()) {
                            User user = ds.toObject(User.class);
                            if (user != null) {
                                sideNavHeaderBinding.headerUserName.setText(user.getName());
                                sideNavHeaderBinding.headerUserEmail.setText(user.getEmail());

                                if (user.getProfilePicUrl() != null && !user.getProfilePicUrl().isEmpty()) {
                                    FirebaseStorage.getInstance()
                                            .getReference("profile_images")
                                            .child(user.getProfilePicUrl())
                                            .getDownloadUrl()
                                            .addOnSuccessListener(uri ->
                                                    Glide.with(MainActivity.this)
                                                            .load(uri)
                                                            .circleCrop()
                                                            .into(sideNavHeaderBinding.headerProfilePic));
                                }
                            }
                        }
                    })
                    .addOnFailureListener(e -> Log.e("Firestore", "Failed to load user: " + e.getMessage()));

            navigationView.getMenu().findItem(R.id.nav_login).setVisible(false);
            navigationView.getMenu().findItem(R.id.nav_profile).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_orders).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_wishlist).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_cart).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_logout).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_about).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_help).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_map).setVisible(true);
            navigationView.getMenu().findItem(R.id.nav_category).setVisible(true);

            sideNavHeaderBinding.headerProfilePic.setOnClickListener(v -> {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                activityResultLauncher.launch(intent);
            });
        }

        NotificationHelper.createNotificationChannels(this);

        PeriodicWorkRequest reminderWork = new PeriodicWorkRequest.Builder(
                RentalReminderWorker.class, 12, TimeUnit.HOURS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "rental_reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                reminderWork);

        connectivityReceiver = new ConnectivityReceiver();
        ConnectivityReceiver.setConnectivityListener(isConnected ->
                Log.i("Connectivity", "Network connected: " + isConnected));
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(connectivityReceiver, filter);
    }

    // Navigate to AllBooksFragment and pass the search query so it pre-filters on load
    private void openAllBooksWithQuery(String query) {
        // Don't stack multiple AllBooksFragments — pop back to one if already there
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_container);
        if (current instanceof AllBooksFragment) {
            // Already on AllBooksFragment — just call filterProducts directly
            ((AllBooksFragment) current).filterProducts(query);
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putString("mode", "search");
        bundle.putString("initialQuery", query);
        AllBooksFragment fragment = new AllBooksFragment();
        fragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    ActivityResultLauncher<Intent> activityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();

                    Glide.with(MainActivity.this)
                            .load(uri)
                            .circleCrop()
                            .into(sideNavHeaderBinding.headerProfilePic);

                    String imageId = UUID.randomUUID().toString();
                    FirebaseStorage storage = FirebaseStorage.getInstance();
                    StorageReference imageRef = storage.getReference("profile_images").child(imageId);
                    imageRef.putFile(uri)
                            .addOnSuccessListener(taskSnapshot ->
                                    firebaseFirestore.collection("users")
                                            .document(firebaseAuth.getUid())
                                            .update("profilePicUrl", imageId)
                                            .addOnSuccessListener(aVoid ->
                                                    Toast.makeText(MainActivity.this,
                                                            "Profile image updated!", Toast.LENGTH_SHORT).show()));
                }
            });

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        Menu navMenu       = navigationView.getMenu();
        Menu bottomNavMenu = bottomNavigationView.getMenu();
        for (int i = 0; i < navMenu.size(); i++)       navMenu.getItem(i).setChecked(false);
        for (int i = 0; i < bottomNavMenu.size(); i++) bottomNavMenu.getItem(i).setChecked(false);

        // ── Home ──────────────────────────────────────────────────────────────
        if (itemId == R.id.nav_home || itemId == R.id.bottom_nav_home) {
            loadFragment(new HomeFragment());
            navigationView.getMenu().findItem(R.id.nav_home).setChecked(true);
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_home).setChecked(true);
            binding.textInputSearch.setText("");

            // ── Map (bottom nav) ─────────────────────────────────────────────────
        } else if (itemId == R.id.bottom_nav_map || itemId == R.id.nav_map) {
            loadFragment(new MapFragment());
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_map).setChecked(true);
            if (navigationView.getMenu().findItem(R.id.nav_map) != null)
                navigationView.getMenu().findItem(R.id.nav_map).setChecked(true);

            // ── Browse / Categories (side drawer only now) ───────────────────────
        } else if (itemId == R.id.nav_category) {
            loadFragment(new CategoryFragment());
            navigationView.getMenu().findItem(R.id.nav_category).setChecked(true);

            // ── Cart ─────────────────────────────────────────────────────────────
        } else if (itemId == R.id.nav_cart || itemId == R.id.bottom_nav_cart) {
            if (firebaseAuth.getCurrentUser() == null) {
                startActivity(new Intent(this, SignInActivity.class));
                return true;
            }
            loadFragment(new CartFragment());
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_cart).setChecked(true);
            if (navigationView.getMenu().findItem(R.id.nav_cart) != null)
                navigationView.getMenu().findItem(R.id.nav_cart).setChecked(true);

            // ── Orders ───────────────────────────────────────────────────────────
        } else if (itemId == R.id.nav_orders || itemId == R.id.bottom_nav_orders) {
            loadFragment(new OrdersFragment());
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_orders).setChecked(true);
            if (navigationView.getMenu().findItem(R.id.nav_orders) != null)
                navigationView.getMenu().findItem(R.id.nav_orders).setChecked(true);

            // ── Profile ──────────────────────────────────────────────────────────
        } else if (itemId == R.id.nav_profile || itemId == R.id.bottom_nav_profile) {
            if (firebaseAuth.getCurrentUser() == null) {
                startActivity(new Intent(this, SignInActivity.class));
                return true;
            }
            loadFragment(new ProfileFragment());
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_profile).setChecked(true);
            if (navigationView.getMenu().findItem(R.id.nav_profile) != null)
                navigationView.getMenu().findItem(R.id.nav_profile).setChecked(true);

            // ── Side drawer only ─────────────────────────────────────────────────
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

        } else if (itemId == R.id.nav_login) {
            startActivity(new Intent(this, SignInActivity.class));

        } else if (itemId == R.id.nav_logout) {
            firebaseAuth.signOut();
            loadFragment(new HomeFragment());
            navigationView.getMenu().clear();
            navigationView.inflateMenu(R.menu.side_nav_menu);
            navigationView.removeHeaderView(sideNavHeaderBinding.getRoot());
            navigationView.inflateHeaderView(R.layout.side_nav_header);
            binding.textInputSearch.setText("");
        }

        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        return true;
    }

    private void loadFragment(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.fragment_container, fragment);
        ft.commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityReceiver != null) {
            unregisterReceiver(connectivityReceiver);
        }
    }
}