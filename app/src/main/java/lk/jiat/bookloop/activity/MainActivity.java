package lk.jiat.bookloop.activity;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.fragment.AboutFragment;
import lk.jiat.bookloop.fragment.CartFragment;
import lk.jiat.bookloop.fragment.HelpFragment;
import lk.jiat.bookloop.fragment.HomeFragment;
import lk.jiat.bookloop.fragment.LibraryFragment;
import lk.jiat.bookloop.fragment.MapFragment;
import lk.jiat.bookloop.fragment.MessageFragment;
import lk.jiat.bookloop.fragment.OrdersFragment;
import lk.jiat.bookloop.fragment.ProfileFragment;
import lk.jiat.bookloop.fragment.SettingsFragment;
import lk.jiat.bookloop.fragment.WishlistFragment;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        BottomNavigationView.OnItemSelectedListener {

    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.side_navigation_view);
        bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle =
                new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
        drawerLayout.addDrawerListener(toggle);

        toggle.syncState();

        // Open drawer when menu icon is clicked
//        findViewById(R.id.toolbar).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
//                    drawerLayout.closeDrawer(GravityCompat.START);
//                } else {
//                    drawerLayout.openDrawer(GravityCompat.START);
//                }
//            }
//        });

        // Handle back press — close drawer first if open
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
        if (savedInstanceState == null){
            loadFragment(new HomeFragment());
            navigationView.getMenu().findItem(R.id.nav_home).setChecked(true);
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_home).setChecked(true);
        }

    }

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
            loadFragment(new ProfileFragment());
            navigationView.getMenu().findItem(R.id.nav_profile).setChecked(true);
            bottomNavigationView.getMenu().findItem(R.id.bottom_nav_profile).setChecked(true);

        } else if (itemId == R.id.nav_cart) {
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

        } else if (itemId == R.id.nav_login) {
            Intent intent = new Intent(MainActivity.this, SignInActivity.class);
            startActivity(intent);

        } else if (itemId == R.id.nav_logout) {
            // TODO: handle logout

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