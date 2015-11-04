package org.peno.b4.bikerisk;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.VisibleRegion;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements ConnectionManager.ResponseListener, OnMapReadyCallback,
        GoogleMap.OnMapLongClickListener, GoogleMap.OnMapClickListener {

    private static final String TAG = "MainActivity";
    private static final int notId = 14;

    private LatLng lastCameraPos;

    private ConnectionManager connectionManager;
    private PositionManager positionManager;

    private GoogleMap mMap;
    private Geocoder geocoder;

    private HashMap<String, GroundOverlay> streetMarkers;

    private HashMap<Float, Bitmap> markerCache;

    private Bitmap originalBitmap;

    private TableLayout pointsTable;
    private TextView speedText;
    private ProgressBar progressBar;

    private TextView connectionLostBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // create activity:
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // get map object (calls onMapReady)
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        hideStartedNotification();

        // init markers and bitmap cache
        streetMarkers = new HashMap<>();
        markerCache = new HashMap<>();

        originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ball);

        // find textviews for street and speed
        pointsTable = (TableLayout)findViewById(R.id.streets_table);
        speedText = (TextView)findViewById(R.id.speed_text);
        progressBar = (ProgressBar)findViewById(R.id.main_progressbar);
        connectionLostBanner = (TextView)findViewById(R.id.connectionLost);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // start connection with server
        connectionManager = ConnectionManager.getInstance(this, this);

        // login:
        if (connectionManager.loadFromSharedPrefs()) {
            // check login with saved key & user
            connectionManager.checkLogin();
        } else {
            // start login activity (calls on activity result)
            Intent loginIntent = new Intent(MainActivity.this, LoginActivity.class);
            startActivityForResult(loginIntent, LoginActivity.REQUEST_LOGIN);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "on pause");
        // stop connection with server
        connectionManager.stop();
        //TODO: stop positionManager from calling ui code
        super.onPause();
    }

    @Override
    protected void onDestroy() {


        // hide notification (gets shown again later if running)
        hideStartedNotification();

        //check if we are just rotation screens:
        if (isChangingConfigurations()) {
            // we will be restarted later again -> save camera position in positionmanager
            if (mMap != null) {
                positionManager.pause(mMap.getCameraPosition());
            } else {
                positionManager.pause(null);
            }
        } else {
            // we will be destroyed -> kill positionmanager
            positionManager.destroy();
        }
        Log.d(TAG, "on destroy");
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == LoginActivity.REQUEST_LOGIN) {
            if (resultCode == RESULT_OK) {
                // we are logged in (via login)
                hideProgressBar();
            } else {
                // somehow user got back here without logging in -> restart login activity
                Intent loginIntent = new Intent(this, LoginActivity.class);
                startActivityForResult(loginIntent, LoginActivity.REQUEST_LOGIN);
            }
        }
    }

    @Override
    public void onResponse(String req, Boolean result, JSONObject response) {
        connectionLostBanner.setVisibility(View.GONE);
        switch (req) {
            case "check-login":
                if (result) {
                    // we are logged in
                    hideProgressBar();
                } else {
                    //login (key not valid anymore):
                    Intent loginIntent = new Intent(this, LoginActivity.class);
                    startActivityForResult(loginIntent, LoginActivity.REQUEST_LOGIN);
                }
                break;
            case "logout":
                // we just logged out -> show login activity
                Intent loginIntent = new Intent(this, LoginActivity.class);
                startActivityForResult(loginIntent, LoginActivity.REQUEST_LOGIN);
                break;
            case "get-all-streets":
                if (result) {
                    // show received streets & markers
                    try {

                        JSONArray streets = response.getJSONArray("streets");
                        float[] HSV = new float[3];
                        for (int i = 0; i < streets.length(); i++) {
                            JSONArray street = streets.getJSONArray(i);
                            Color.colorToHSV(street.getInt(3), HSV);
                            String name = street.getString(0);
                            double lat = street.getDouble(1);
                            double lng = street.getDouble(2);
                            if (streetMarkers.containsKey(name)) {
                                // maybe owner changed -> update icon
                                streetMarkers.get(name)
                                        .setImage(BitmapDescriptorFactory
                                                .fromBitmap(Utils.getStreetBitmap(markerCache, originalBitmap, HSV[0])));
                            } else {
                                // add marker
                                addMarker(HSV[0], lat, lng, name);
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case "get-street":
                if (result) {
                    // show toast with name of owner
                    try {
                        JSONArray info = response.getJSONArray("info");
                        String street = response.getString("street");
                        Toast.makeText(this, "Owner of " + street + ": " + info.getString(1), Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case "add-points":
                if (result) {
                    Toast.makeText(this, "saved points", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "error saving points", Toast.LENGTH_SHORT).show();
                }
                break;

        }
    }


    @Override
    public void onConnectionLost(String reason) {
        Log.d(TAG, "connection lost: " + reason);
        connectionLostBanner.setVisibility(View.VISIBLE);
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.actions_main, menu);

        // change icon (if started or not)
        MenuItem item = menu.findItem(R.id.action_start_stop);
        if (positionManager == null || !positionManager.started) {
            item.setIcon(R.drawable.ic_play_dark);
        } else {
            item.setIcon(R.drawable.ic_pause_dark);

        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_logout:
                this.connectionManager.logout();
                return super.onOptionsItemSelected(item);
            case R.id.action_user_info:
                Intent intent = new Intent(this, UserInfoActivity.class);
                intent.putExtra("name", this.connectionManager.user);
                startActivity(intent);
                return super.onOptionsItemSelected(item);
            case R.id.action_start_stop:
                if (!positionManager.started) {
                    showStartedNotification();
                    showInfoText();
                    positionManager.start();
                    //TODO: wait for location??
                } else {
                    hideStartedNotification();
                    hideInfoText();
                    positionManager.stop();
                }
                // redraw options menu:
                invalidateOptionsMenu();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        geocoder = new Geocoder(this);

        positionManager = PositionManager.getInstance(this,
                new PositionManager.UIObjects(mMap, speedText, pointsTable, progressBar));
        if (positionManager.started) {
            // show notification
            showStartedNotification();
            invalidateOptionsMenu();
            showInfoText();
        }

        // set camera position:
        if (positionManager.lastCameraPosition != null) {
            // last position:
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(positionManager.lastCameraPosition));
        } else {
            // camera centered at Sint-Pieterskerk, Leuven
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(50.879549, 4.701242), 14));
        }

        // set up listeners:
        mMap.setOnMapLongClickListener(this);
        mMap.setOnMapClickListener(this);
        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                Log.d(TAG, "camera changed");
                // TODO: fix dist calculation
                // we can't rely on just distance (because of zoom)
                // so I use pixels -> different between devices / unreliable
                VisibleRegion reg = mMap.getProjection().getVisibleRegion();
                LatLngBounds bounds = reg.latLngBounds;
                float dist = 0;
                if (lastCameraPos != null) {
                    Point p1 = mMap.getProjection().toScreenLocation(lastCameraPos);
                    dist = p1.x*p1.x + p1.y*p1.y;
                    Log.d(TAG, "dist: " + dist);
                }

                if (lastCameraPos == null || dist > 700*700){
                    Log.d(TAG, "getting street");
                    connectionManager.getAllStreets(bounds);
                    lastCameraPos = bounds.northeast;
                }

            }
        });
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        // lookup street and start streetRankActivity:
        String street = Utils.lookupStreet(geocoder, latLng);
        if (street != null) {
            Intent intent = new Intent(this, StreetRankActivity.class);
            intent.putExtra(StreetRankActivity.EXTRA_STREET, street);
            //intent.putExtra(StreetRankActivity.EXTRA_CITY, city);
            startActivity(intent);
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        pointsTable.setVisibility(View.GONE);
        // lookup street & show toast (in onLoginResult)
        String street = Utils.lookupStreet(geocoder, latLng);
        if (street != null)
            connectionManager.getStreet(street);
    }

    private void showStartedNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("RoadWars is running")
                .setContentText("click to open")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);


        Intent intent = new Intent(this, this.getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent Pintent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        builder.setContentIntent(Pintent);
        NotificationManager notificationManager =
                (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(notId, builder.build());
    }

    private void hideStartedNotification() {
        NotificationManager notificationManager =
                (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(notId);
    }

    private void showInfoText() {
        speedText.setVisibility(View.VISIBLE);
    }

    private void hideInfoText() {
        speedText.setVisibility(View.GONE);
    }

    private void hideProgressBar() {
        progressBar.setVisibility(View.GONE);
    }

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void addMarker(float hue, double lat, double lng, String name) {
        /*
        MarkerOptions markerOptions = new MarkerOptions()
                .icon(BitmapDescriptorFactory.defaultMarker(hue))
                .position(new LatLng(lat, lng))
                .title(name);
        mMap.addMarker(markerOptions);
        */
        // add ground overlay
        GroundOverlayOptions groundOverlayOptions = new GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromBitmap(Utils.getStreetBitmap(markerCache, originalBitmap, hue)))
                .position(new LatLng(lat, lng), 40);

        streetMarkers.put(name, mMap.addGroundOverlay(groundOverlayOptions));
    }


}
