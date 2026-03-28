package lk.jiat.bookloop.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.android.PolyUtil;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import lk.jiat.bookloop.R;
import lk.jiat.bookloop.databinding.FragmentMapBinding;

// MapFragment — "Find BookLoop Near You"
// WHY this feature exists: A book rental app needs pickup and return points.
//     Users can see their location, find the nearest BookLoop spot, and get directions.
// COVERS: Google Maps (Generic Map + Direction API) assignment requirement.
// ADAPTED FROM: MapsActivity practical — converted to Fragment and applied to BookLoop context.
public class MapFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "MapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private FragmentMapBinding binding;
    private GoogleMap mMap;

    // FusedLocationProviderClient — Google's recommended way to get GPS location.
    // WHY: More battery-efficient and accurate than raw LocationManager.
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    // Stores the user's most recent GPS position — used as route start point
    private LatLng currentLocation;

    // The draggable destination marker — moves when user long-presses
    private Marker destinationMarker;

    // The polyline drawn on the map showing the driving route
    private Polyline routePolyline;

    // Background thread executor — Directions API call blocks the thread,
    // so we MUST run it off the main thread to avoid freezing the app.
    // Same pattern used in the practical (ExecutorService instead of AsyncTask).
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // BookLoop pickup/return locations around Colombo
    // WHY hardcoded: These are the physical locations of our fictional book pickup points.
    //     In a real app, these would come from Firestore
    private static final LatLng[] PICKUP_LOCATIONS = {
            new LatLng(7.1424, 80.1037),   // Nittambuwa
            new LatLng(7.2427, 80.1270),   // Mirigama
            new LatLng(7.0883, 79.9898),   // Gampaha
            new LatLng(7.0000, 79.9500),   // Kadawatha
            new LatLng(6.9553, 79.9220),   // Kelaniya
            new LatLng(7.1540, 80.0600),   // Veyangoda
    };



    private static final String[] PICKUP_NAMES = {
            "BookLoop Nittambuwa",
            "BookLoop Mirigama",
            "BookLoop Gampaha",
            "BookLoop Kadawatha",
            "BookLoop Kelaniya",
            "BookLoop Veyangoda",
    };

    private static final String[] PICKUP_HOURS = {
            "Mon–Sat: 9am–7pm",
            "Mon–Sat: 9am–7pm",
            "Mon–Sun: 8am–8pm",
            "Mon–Fri: 10am–6pm",
            "Mon–Sat: 9am–7pm",
            "Mon–Sat: 9am–6pm",
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set up GPS client — same as practical
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // LocationRequest — how often we want GPS updates
        // PRIORITY_HIGH_ACCURACY: uses GPS + network for best accuracy
        // 3000ms interval, 2000ms minimum
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(2000)
                .build();

        // KEY DIFFERENCE from practical: Fragment uses getChildFragmentManager()
        // NOT getSupportFragmentManager(). This is because we are INSIDE a fragment
        // and the map is a CHILD fragment nested inside us.
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            // This tells the Maps SDK to call onMapReady() when the map is loaded
            mapFragment.getMapAsync(this);
        }

        // Clear route button — removes the direction line from the map
        binding.mapBtnClearRoute.setOnClickListener(v -> {
            if (routePolyline != null) {
                routePolyline.remove();
                routePolyline = null;
            }
            if (destinationMarker != null) {
                destinationMarker.remove();
                destinationMarker = null;
            }
            binding.mapBtnClearRoute.setVisibility(View.GONE);
            binding.mapNearestLabel.setText("Tap a marker to see details");
        });
    }

    // ─── Called by Maps SDK when the Google Map is ready to use ──────────────
    // Everything map-related (markers, camera, listeners) goes here.
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Enable zoom buttons and compass
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        // Default camera position — Colombo city centre
        // To this (centers on Gampaha district):
        LatLng gampaha = new LatLng(7.0883, 79.9898);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gampaha, 10f));
        // Zoom levels: 2-5 = country, 10-15 = city, 18-20 = building

        // Add all BookLoop pickup/return markers to the map
        addPickupMarkers();

        // Request GPS permission and start tracking
        enableMyLocation();

        // When user taps a marker info window, get directions to it
        mMap.setOnInfoWindowClickListener(marker -> {
            if (currentLocation != null) {
                LatLng destination = marker.getPosition();
                binding.mapNearestLabel.setText("Getting directions to " + marker.getTitle() + "...");
                getDirections(currentLocation, destination);
            } else {
                Toast.makeText(getContext(), "Waiting for your location...", Toast.LENGTH_SHORT).show();
            }
        });

        // Long press anywhere → place a custom destination marker and get directions.
        // Same pattern as practical's setOnMapLongClickListener.
        mMap.setOnMapLongClickListener(latLng -> {
            if (currentLocation == null) {
                Toast.makeText(getContext(), "Waiting for GPS location...", Toast.LENGTH_SHORT).show();
                return;
            }

            // Move or create the destination marker
            if (destinationMarker == null) {
                destinationMarker = mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Your Destination")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            } else {
                destinationMarker.setPosition(latLng);
            }

            binding.mapNearestLabel.setText("Drawing route...");
            getDirections(currentLocation, latLng);
        });
    }

    // ─── Add BookLoop pickup markers (yellow/gold markers) ───────────────────
    private void addPickupMarkers() {
        for (int i = 0; i < PICKUP_LOCATIONS.length; i++) {
            mMap.addMarker(new MarkerOptions()
                    .position(PICKUP_LOCATIONS[i])
                    .title(PICKUP_NAMES[i])
                    .snippet(PICKUP_HOURS[i])  // snippet = small text shown under title
                    // HUE_ORANGE makes these stand out from the user's blue dot
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        }
    }

    // ─── Check permission, enable blue "my location" dot ─────────────────────
    // Same logic as practical — check if we have ACCESS_FINE_LOCATION permission.
    // If yes → enable the dot + start GPS updates.
    // If no → ask the user for permission.
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Permission already granted — turn on blue dot and start GPS
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
            startLocationUpdates();
            binding.mapLocationStatus.setText("📍 Live");

        } else {
            // Ask user for location permission
            // The result comes back in onRequestPermissionsResult()
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            binding.mapLocationStatus.setText("📍 No GPS");
        }
    }

    // ─── Start receiving live GPS updates ────────────────────────────────────
    // Same as practical's startLocationUpdate() — uses FusedLocationProviderClient.
    // locationCallback fires every ~3 seconds with a new LatLng.
    @RequiresPermission(allOf = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION})
    private void startLocationUpdates() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location location : result.getLocations()) {
                    // Save latest GPS position — used as route starting point
                    currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    Log.d(TAG, "Location updated: " + currentLocation);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, requireActivity().getMainLooper());
    }

    // ─── Handle user's answer to the permission popup ────────────────────────
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // User said YES — now enable location
                enableMyLocation();
            } else {
                Toast.makeText(getContext(),
                        "Location permission needed to show your position",
                        Toast.LENGTH_LONG).show();
                binding.mapLocationStatus.setText("📍 Denied");
            }
        }
    }

    // ─── Get driving directions using Java Maps Client SDK ───────────────────
    // ADAPTED FROM: practical's getDirectionWithApi() — same library, same pattern.
    // WHY background thread: The DirectionsApi.newRequest().await() call BLOCKS
    //     until it gets a response from Google. If we ran this on the main thread,
    //     the app would freeze. ExecutorService runs it on a background thread,
    //     then we use requireActivity().runOnUiThread() to update the map safely.
    public void getDirections(LatLng start, LatLng end) {
        executor.execute(() -> {
            // Your BookLoop API key (same one in manifest)
            String apiKey = "AIzaSyDIK1Hd29CwsXchoRla9QlAg6qt_h3Ij3Q";

            String origin      = start.latitude + "," + start.longitude;
            String destination = end.latitude   + "," + end.longitude;

            // GeoApiContext is the connection to Google's APIs — must be closed after use
            // (try-with-resources automatically closes it)
            try (GeoApiContext context = new GeoApiContext.Builder().apiKey(apiKey).build()) {

                DirectionsResult result = DirectionsApi.newRequest(context)
                        .mode(TravelMode.DRIVING)   // could change to WALKING or TRANSIT
                        .origin(origin)
                        .destination(destination)
                        .departureTimeNow()
                        .await();                   // this BLOCKS — that's why we're on background thread

                if (result.routes.length > 0) {
                    // The route comes back as an encoded polyline string
                    // PolyUtil.decode() converts it into a List<LatLng> we can draw
                    String encodedPath = result.routes[0].overviewPolyline.getEncodedPath();
                    List<LatLng> points = PolyUtil.decode(encodedPath);

                    // Get human-readable distance and duration
                    String distance = result.routes[0].legs[0].distance.humanReadable;
                    String duration = result.routes[0].legs[0].duration.humanReadable;

                    // Update UI on main thread — can't touch Views from background thread!
                    requireActivity().runOnUiThread(() -> {
                        // Draw or update the route line on the map
                        if (routePolyline == null) {
                            routePolyline = mMap.addPolyline(new PolylineOptions()
                                    .width(14)
                                    .color(Color.parseColor("#8D4E2C")) // BookLoop primary color
                                    .addAll(points));
                        } else {
                            routePolyline.setPoints(points);
                        }

                        // Show route info and "Clear Route" button
                        binding.mapNearestLabel.setText("🚗 " + distance + " · " + duration);
                        binding.mapBtnClearRoute.setVisibility(View.VISIBLE);

                        // Zoom map to fit the whole route
                        com.google.android.gms.maps.model.LatLngBounds.Builder builder =
                                new com.google.android.gms.maps.model.LatLngBounds.Builder();
                        for (LatLng point : points) builder.include(point);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(
                                builder.build(), 120));
                    });
                }

            } catch (ApiException e) {
                Log.e(TAG, "Directions API error: " + e.getMessage());
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "Directions error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
            } catch (InterruptedException | IOException e) {
                Log.e(TAG, "Directions request failed: " + e.getMessage());
            }
        });
    }

    // ─── Pause GPS updates when app goes to background ───────────────────────
    // WHY: Saves battery — we don't need GPS when user isn't looking at the map.
    // Same as practical's onPause().
    @Override
    public void onPause() {
        super.onPause();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    // ─── Resume GPS updates when user comes back to map screen ───────────────
    @Override
    public void onResume() {
        super.onResume();
        if (mMap != null && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    // ─── Shutdown background thread when fragment is destroyed ───────────────
    // WHY: Prevents memory leaks — the thread keeps running if we don't shut it down.
    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}